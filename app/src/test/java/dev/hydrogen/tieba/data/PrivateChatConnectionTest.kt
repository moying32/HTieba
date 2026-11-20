package dev.hydrogen.tieba.data

import java.io.*
import java.net.*
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import dev.hydrogen.tieba.data.ChatProto.encode
import dev.hydrogen.tieba.data.ChatProto.parse
import dev.hydrogen.tieba.data.ChatProto.b
import dev.hydrogen.tieba.data.ChatProto.n
import dev.hydrogen.tieba.data.ChatProto.s
import dev.hydrogen.tieba.data.ChatProto.bytes
import dev.hydrogen.tieba.data.ChatProto.text
import dev.hydrogen.tieba.data.ChatProto.number

/** Local loopback IM peer only. No real account, remote login or message send. */
class PrivateChatConnectionTest {
    companion object {
        private val secret = ByteArray(31) { it.toByte() }
        private val sessionKey = PrivateChatProtocol.derive(secret)
        private fun login(uid: Long) = encode(b(2,encode(b(1,encode(n(1,600),n(20,6))),
            b(1,encode(n(1,700),n(20,2))),b(2,encode(n(1,uid),s(2,"本机测试账号"))),n(6,uid))))
        private fun message(id: Long, sender: Long, type: Long = 1) = b(2,encode(n(1,id),n(3,type),s(5,"本地测试文本"),n(8,1000),
            b(10,encode(n(1,sender),s(2,"真实返回的昵称"),s(4,"tb.1.test?t=12")))))
        private fun history() = encode(b(2,encode(b(1,encode(b(1,encode(n(1,600),n(20,6))),message(1,77),message(2,42),message(3,77,5))),
            b(1,encode(b(1,encode(n(1,700),n(20,2))),message(4,88))))))
    }

    private data class WireRequest(val command: Int, val id: Int, val body: ByteArray)
    private class MockIm(private val uid: Long = 42, private val silentLogin: Boolean = false,
        private val handler: MockIm.(WireRequest) -> Unit = {}) : Closeable {
        private val server = ServerSocket(0,1,InetAddress.getLoopbackAddress()).apply { soTimeout = 5000 }
        private val worker = Executors.newSingleThreadExecutor { runnable -> Thread(runnable,"local-im-test").apply { isDaemon = true } }
        @Volatile private var socket: Socket? = null
        private lateinit var output: DataOutputStream
        @Volatile var headers = ""; private set
        val commands = CopyOnWriteArrayList<WireRequest>()
        val client: OkHttpClient = PrivateChatProtocol.client().newBuilder().proxy(Proxy.NO_PROXY)
            .dns(object : okhttp3.Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    require(hostname == PrivateChatProtocol.HOST); return listOf(InetAddress.getLoopbackAddress())
                }
            }).build()
        val connection = PrivateChatConnection("ws://${PrivateChatProtocol.HOST}:${server.localPort}/",client) { secret }
        private val result = worker.submit {
            try {
                server.accept().use { peer ->
                    socket = peer; peer.soTimeout = 5000
                    val input = DataInputStream(peer.getInputStream()); output = DataOutputStream(peer.getOutputStream())
                    val head = ByteArrayOutputStream()
                    while(!head.toString(Charsets.US_ASCII.name()).endsWith("\r\n\r\n")) {
                        require(head.size() < 16384); head.write(input.readUnsignedByte())
                    }
                    headers = head.toString(Charsets.US_ASCII.name())
                    val key = headers.lineSequence().first { it.startsWith("Sec-WebSocket-Key:",true) }.substringAfter(':').trim()
                    val accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                        .digest((key+"258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(Charsets.US_ASCII)))
                    output.writeBytes("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: $accept\r\nSec-WebSocket-Protocol: chat\r\n\r\n"); output.flush()
                    while(!peer.isClosed) {
                        val first = input.readUnsignedByte(); val second = input.readUnsignedByte()
                        val opcode = first and 15; val marker = second and 127
                        val length = when(marker) { 126 -> input.readUnsignedShort().toLong(); 127 -> input.readLong(); else -> marker.toLong() }
                        require(length in 0..4*1024*1024L && second and 128 != 0)
                        val mask = ByteArray(4); input.readFully(mask)
                        val frame = ByteArray(length.toInt()); input.readFully(frame)
                        frame.indices.forEach { frame[it] = (frame[it].toInt() xor mask[it%4].toInt()).toByte() }
                        if(opcode == 8) break
                        if(opcode == 9) { writeFrame(frame,10); continue }
                        require(opcode == 2 && frame.size >= 9)
                        val buffer = ByteBuffer.wrap(frame); val flag = buffer.get().toInt() and 255
                        val command = buffer.int; val id = buffer.int
                        var body = frame.copyOfRange(9,frame.size)
                        if(flag and 128 != 0) body = PrivateChatProtocol.cipher(Cipher.DECRYPT_MODE,body,sessionKey)
                        val request = WireRequest(command,id,body); commands += request
                        if(command == 1001) {
                            assertEquals("local-fake-credential",parse(parse(body).bytes(2)).text(1))
                            if(!silentLogin) reply(request,login(uid),encrypted = false)
                        } else handler(request)
                    }
                }
            } catch(_: EOFException) { /* Client closed after the test. */ }
            catch(_: SocketException) { /* Test cancellation closes the peer. */ }
        }
        private fun writeFrame(frame: ByteArray, opcode: Int = 2) {
            output.writeByte(128 or opcode)
            when { frame.size < 126 -> output.writeByte(frame.size); frame.size <= 65535 -> { output.writeByte(126); output.writeShort(frame.size) }
                else -> { output.writeByte(127); output.writeLong(frame.size.toLong()) } }
            output.write(frame); output.flush()
        }
        fun reply(request: WireRequest, body: ByteArray, encrypted: Boolean = true, gzip: Boolean = false, command: Int = request.command) {
            var bytes = if(gzip) ByteArrayOutputStream().apply { GZIPOutputStream(this).use { it.write(body) } }.toByteArray() else body
            if(encrypted) bytes = PrivateChatProtocol.cipher(Cipher.ENCRYPT_MODE,bytes,sessionKey)
            val flag = 8 or (if(encrypted) 128 else 0) or (if(gzip) 64 else 0)
            writeFrame(ByteBuffer.allocate(9+bytes.size).put(flag.toByte()).putInt(command).putInt(request.id).put(bytes).array())
        }
        fun disconnect() { socket?.close() }
        override fun close() {
            connection.close(); socket?.close(); server.close()
            try { result.get(6,TimeUnit.SECONDS) } finally { worker.shutdownNow(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdownNow() }
        }
    }

    @Test fun keyDerivationMatchesIndependentPythonHashlibFixture() {
        assertEquals("74b65d14d634d0f850100e5beac20584373a9436594d0b75362cebeca3ac84c8",
            PrivateChatProtocol.derive(secret).joinToString("") { "%02x".format(it.toInt() and 255) })
    }

    @Test fun handshakeAndEncryptedGzipHistoryUseActualOkHttpWebSocket() = runBlocking {
        MockIm { request -> if(request.command == 202003) reply(request,history(),gzip = true) }.use { peer ->
            assertEquals(listOf(600L),withTimeout(8000) { peer.connection.login("42","local-fake-credential") })
            assertTrue(peer.headers.contains("Sec-WebSocket-Extensions: im_version=2.3"))
            val messages = peer.connection.messages("local-cuid")
            assertEquals(listOf("1","3"),messages.map { it.id })
            assertEquals("真实返回的昵称",messages.first().person.name)
            assertTrue(messages.first().person.avatar.endsWith("tb.1.test?t=12"))
            assertEquals(5,messages.last().type)
            val request = parse(parse(peer.commands.last().body).bytes(2))
            assertEquals(600L,parse(request.first { it.number == 6 }.bytes).number(1))
        }
    }

    @Test fun wrongAccountLoginNeverBecomesConnected() = runBlocking {
        MockIm(uid = 99).use { peer ->
            val result = runCatching { withTimeout(8000) { peer.connection.login("42","local-fake-credential") } }
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("账号")); assertFalse(peer.connection.connected)
            assertEquals(listOf(1001),peer.commands.map { it.command })
        }
    }

    @Test fun missingCredentialDoesNotStartAnyHandshake() = runBlocking {
        MockIm().use { peer ->
            assertTrue(runCatching { peer.connection.login("42","") }.isFailure)
            assertFalse(peer.connection.connected); assertTrue(peer.commands.isEmpty())
        }
    }

    @Test fun cancellingLoginClosesConnection() = runBlocking {
        MockIm(silentLogin = true).use { peer ->
            val task = launch { peer.connection.login("42","local-fake-credential") }
            withTimeout(8000) { while(peer.commands.isEmpty()) delay(10) }
            task.cancelAndJoin(); assertFalse(peer.connection.connected)
            assertEquals(1,peer.commands.size)
        }
    }

    @Test fun replyWithDifferentCommandDoesNotCompletePendingRequest() = runBlocking {
        MockIm { request ->
            if(request.command == 202003) {
                reply(request,encode(b(1,encode(n(1,400),s(2,"其他命令的错误")))),command = 999)
                reply(request,history())
            }
        }.use { peer ->
            peer.connection.login("42","local-fake-credential")
            assertEquals(2,withTimeout(8000) { peer.connection.messages("local-cuid") }.size)
        }
    }

    @Test fun localSendWaitsForMatchingReceiptAndDoesNotRetryAfterDisconnect() = runBlocking {
        MockIm { request ->
            if(request.command == 205001) {
                val sent = parse(parse(request.body).bytes(1))
                assertEquals("77",sent.number(2).toString()); assertEquals("仅发送到本地模拟器",sent.text(4))
                reply(request,encode(b(2,encode(n(1,1234),n(3,sent.number(6)),n(4,77)))))
            }
        }.use { peer ->
            peer.connection.login("42","local-fake-credential")
            assertEquals("1234",peer.connection.send("77","仅发送到本地模拟器",123))
            assertEquals(1,peer.commands.count { it.command == 205001 })
        }
        MockIm { request -> if(request.command == 205001) disconnect() }.use { peer ->
            peer.connection.login("42","local-fake-credential")
            assertTrue(runCatching { withTimeout(8000) { peer.connection.send("77","本地断线测试",124) } }.isFailure)
            assertEquals(1,peer.commands.count { it.command == 205001 })
        }
    }

    @Test fun failedMismatchedAndMissingReceiptsNeverBecomeSuccess() {
        val samples = listOf(encode(),encode(b(2,encode(n(1,123),n(4,88)))),encode(b(2,encode(n(1,123),n(3,999)))),
            encode(b(2,encode(n(1,123),b(6,encode(n(1,403),s(2,"拒绝发送")))))))
        samples.forEach { assertTrue(runCatching { PrivateChatProtocol.receipt(it,"77",123) }.isFailure) }
    }

    @Test fun invalidHistoryDoesNotMasqueradeAsEmptySuccess() {
        assertTrue(runCatching { PrivateChatProtocol.messages(encode(),"42") }.isFailure)
        assertTrue(runCatching { PrivateChatProtocol.messages(encode(b(1,encode(n(1,400),s(2,"失败")))),"42") }.isFailure)
        assertTrue(PrivateChatProtocol.messages(encode(b(2,encode())),"42").isEmpty())
    }

    @Test fun legacyClientRejectsOtherHostsBeforeAnyNetworkRequest() {
        val client = PrivateChatProtocol.client()
        try {
            val request = okhttp3.Request.Builder().url("http://other.invalid/").build()
            val failure = runCatching { client.newCall(request).execute().close() }.exceptionOrNull()
            assertTrue(failure?.message.orEmpty().contains("地址不匹配"))
            assertFalse(client.followRedirects); assertFalse(client.followSslRedirects)
        } finally { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdownNow() }
    }

    @Test fun manifestExceptionAppliesToExactlyOneHostWithoutSubdomains() {
        val xml = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("app/src/main/res/xml/network_security_config.xml"))
        val base = xml.getElementsByTagName("base-config").item(0) as org.w3c.dom.Element
        assertEquals("false",base.getAttribute("cleartextTrafficPermitted"))
        val configs = xml.getElementsByTagName("domain-config"); assertEquals(1,configs.length)
        assertEquals("true",(configs.item(0) as org.w3c.dom.Element).getAttribute("cleartextTrafficPermitted"))
        val domains = xml.getElementsByTagName("domain"); assertEquals(1,domains.length)
        val domain = domains.item(0) as org.w3c.dom.Element
        assertEquals(PrivateChatProtocol.HOST,domain.textContent.trim()); assertEquals("false",domain.getAttribute("includeSubdomains"))
    }
}
