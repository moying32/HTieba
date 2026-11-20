package dev.hydrogen.tieba.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okio.ByteString.Companion.toByteString
import org.json.JSONObject

/** Small bounded protobuf codec for the documented IM fields. Unknown fields
 * are skipped, never interpreted as identities or successful receipts. */
internal object ChatProto {
    fun inflate(input: ByteArray): ByteArray = java.util.zip.GZIPInputStream(input.inputStream()).use { source ->
        ByteArrayOutputStream().use { output -> val buffer = ByteArray(8192)
            while(true) { val count = source.read(buffer); if(count < 0) break
                require(output.size() + count <= 4 * 1024 * 1024) { "消息响应过大" }; output.write(buffer,0,count) }
            output.toByteArray()
        }
    }
    data class Field(val number: Int, val value: Long = 0, val bytes: ByteArray = byteArrayOf(), val wire: Int = 0)
    fun encode(vararg fields: Field): ByteArray = ByteArrayOutputStream().apply {
        fun variable(input: Long) { var n = input; while (n and -128L != 0L) { write((n.toInt() and 127) or 128); n = n ushr 7 }; write(n.toInt()) }
        fields.forEach { f ->
            require(f.number in 1..536870911)
            variable((f.number.toLong() shl 3) or f.wire.toLong())
            if (f.wire == 2) { variable(f.bytes.size.toLong()); write(f.bytes) } else variable(f.value)
        }
    }.toByteArray()
    fun n(key: Int, value: Long) = Field(key, value)
    fun b(key: Int, value: ByteArray) = Field(key, bytes = value, wire = 2)
    fun s(key: Int, value: String) = b(key, value.toByteArray(Charsets.UTF_8))
    fun parse(data: ByteArray): List<Field> {
        require(data.size <= 4 * 1024 * 1024) { "消息响应过大" }
        var offset = 0
        fun variable(): Long {
            var result = 0L
            for (i in 0..9) { require(offset < data.size) { "消息响应不完整" }; val v = data[offset++].toInt() and 255
                require(i != 9 || v <= 1) { "无效消息整数" }; result = result or ((v and 127).toLong() shl (i * 7))
                if (v < 128) return result }
            throw IOException("无效消息整数")
        }
        val result = mutableListOf<Field>()
        while (offset < data.size) {
            val tag = variable(); require(tag in 1..0xffffffffL); val key = (tag ushr 3).toInt(); require(key > 0)
            when ((tag and 7).toInt()) {
                0 -> result += n(key, variable())
                2 -> { val length = variable(); require(length >= 0 && length <= data.size - offset); val end = offset + length.toInt()
                    result += b(key, data.copyOfRange(offset, end)); offset = end }
                1 -> { require(data.size - offset >= 8); offset += 8 }
                5 -> { require(data.size - offset >= 4); offset += 4 }
                else -> throw IOException("不支持的消息编码")
            }
        }
        return result
    }
    fun List<Field>.number(key: Int) = firstOrNull { it.number == key }?.value ?: 0L
    fun List<Field>.bytes(key: Int) = firstOrNull { it.number == key }?.bytes ?: byteArrayOf()
    fun List<Field>.text(key: Int) = bytes(key).toString(Charsets.UTF_8)
    fun check(data: List<Field>) {
        val error = parse(with(this) { data.bytes(1) })
        val code = with(this) { error.number(1) }
        if (code != 0L) throw IOException(with(this) { error.text(2) }.ifBlank { "私信请求失败（$code）" })
    }
}

internal object PrivateChatProtocol {
    const val HOST = "im.tieba.baidu.com"
    const val ENDPOINT = "ws://im.tieba.baidu.com:8000/"

    fun client() = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS).pingInterval(25, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .addInterceptor { chain ->
            val request = chain.request()
            require(request.url.host == HOST && request.url.scheme == "http") { "私信连接地址不匹配" }
            // RealWebSocket rejects caller-supplied extensions before connecting.
            // Add Baidu's version marker after OkHttp creates its upgrade request.
            chain.proceed(request.newBuilder().header("Sec-WebSocket-Extensions", "im_version=2.3")
                .header("Accept-Encoding", "gzip").build())
        }.build()

    fun derive(secret: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(secret, "HmacSHA1")) }
        val salt = byteArrayOf(0xa4.toByte(), 0x0b, 0xc8.toByte(), 0x34, 0xd6.toByte(), 0x95.toByte(), 0xf3.toByte(), 0x13)
        return (1..2).flatMap { block ->
            var u = mac.doFinal(salt + ByteBuffer.allocate(4).putInt(block).array()); val x = u.clone()
            repeat(4) { u = mac.doFinal(u); x.indices.forEach { i -> x[i] = (x[i].toInt() xor u[i].toInt()).toByte() } }; x.toList()
        }.toByteArray().copyOf(32)
    }

    fun cipher(mode: Int, bytes: ByteArray, key: ByteArray): ByteArray = Cipher.getInstance("AES/ECB/PKCS5Padding").run {
        init(mode, SecretKeySpec(key, "AES")); doFinal(bytes)
    }

    fun messages(body: ByteArray, owner: String): List<ChatMessage> = ChatProto.run {
        val root = parse(body); check(root)
        require(root.any { it.number == 2 && it.wire == 2 }) { "百度未返回私信消息数据" }
        parse(root.bytes(2)).filter { it.number == 1 }.flatMap { group ->
            val fields = parse(group.bytes); val groupInfo = parse(fields.bytes(1))
            if(groupInfo.number(20) != 6L) emptyList() else fields.filter { it.number == 2 }.mapNotNull { field ->
                val m = parse(field.bytes); val user = parse(m.bytes(10)); val sender = user.number(1).toString()
                // This endpoint is a personal inbox; outgoing records have no
                // recipient field. Never invent a conversation with oneself.
                if(m.number(1) <= 0 || sender == "0" || sender == owner) null else
                    ChatMessage(m.number(1).toString(), sender, m.text(5), m.number(8), m.number(3).toInt(),
                        Person(sender,user.text(2).ifBlank { "吧友" },IdentityParser.chatAvatar(user.text(4))))
            }
        }.distinctBy { "${it.senderId}:${it.id}" }.sortedBy { it.time }
    }

    fun receipt(body: ByteArray, to: String, record: Long): String = ChatProto.run {
        val root = parse(body); check(root)
        val data = parse(root.bytes(2)); val block = parse(data.bytes(6)); val code = block.number(1)
        if(code != 0L) throw IOException(block.text(2).ifBlank { "私信被百度拒绝（$code）" })
        require(data.number(1) > 0) { "百度未确认发送结果，请核对会话后再重试" }
        require(data.none { it.number == 4 } || data.number(4).toString() == to) { "私信回执收件人不匹配，请核对会话" }
        require(data.none { it.number == 3 } || data.number(3) == record) { "私信回执标识不匹配，请核对会话" }
        data.number(1).toString()
    }
}

/** Baidu's legacy IM websocket: RSA key exchange then AES protected payloads.
 * No automatic resend: a timeout after submission is an uncertain result. */
internal class PrivateChatConnection(
    private val endpoint: String = PrivateChatProtocol.ENDPOINT,
    private val client: OkHttpClient = PrivateChatProtocol.client(),
    private val secretFactory: () -> ByteArray = { ByteArray(31).also { SecureRandom().nextBytes(it) } }
) {
    companion object {
        // Explicit domain-scoped authorization received on 2026-11-14.
        const val ENABLED = true
        private const val PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAwQpwBZxXJV/JVRF/uNfyMSdu7YWwRNLM8+2xbniGp2iIQHOikPpTYQjlQgMi1uvq1kZpJ32rHo3hkwjy2l0lFwr3u4Hk2Wk7vnsqYQjAlYlK0TCzjpmiI+OiPOUNVtbWHQiLiVqFtzvpvi4AU7C1iKGvc/4IS45WjHxeScHhnZZ7njS4S1UgNP/GflRIbzgbBhyZ9kEW5/OO5YfG1fy6r4KSlDJw4o/mw5XhftyIpL+5ZBVBC6E1EIiP/dd9AbK62VV1PByfPMHMixpxI3GM2qwcmFsXcCcgvUXJBa9k6zP8dDQ3csCM2QNT+CQAOxthjtp/TFWaD7MzOdsIYb3THwIDAQAB"
    }
    private var socket: WebSocket? = null
    private data class Pending(val command: Int, val response: CompletableDeferred<ByteArray>)
    private val pending = ConcurrentHashMap<Int, Pending>()
    private val loginMutex = Mutex()
    private val sequence = AtomicInteger(0)
    private var key = byteArrayOf()
    private var owner = ""
    private var groups = emptyList<Long>()
    @Volatile private var generation = 0L
    @Volatile var connected = false; private set
    suspend fun login(uid: String, bduss: String): List<Long> = loginMutex.withLock { loginLocked(uid, bduss) }
    private suspend fun loginLocked(uid: String, bduss: String): List<Long> {
        check(ENABLED) { "原生私信旧协议连接尚未启用，可先使用官方私信入口" }
        require(uid.toLongOrNull()?.let { it > 0 } == true && bduss.isNotBlank()) { "登录状态已失效，请重新登录百度账号" }
        if (connected && uid == owner) return groups
        close(); owner = uid
        val epoch = generation
        val secret = secretFactory(); val sessionKey = PrivateChatProtocol.derive(secret); key = sessionKey
        val opened = CompletableDeferred<Unit>()
        socket = client.newWebSocket(Request.Builder().url(endpoint).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if(epoch == generation) opened.complete(Unit) else { opened.completeExceptionally(IOException("私信连接已取消")); webSocket.cancel() }
            }
            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                if(epoch != generation) return
                try {
                    val raw = bytes.toByteArray(); require(raw.size in 9..4 * 1024 * 1024)
                    val header = ByteBuffer.wrap(raw); val flag = header.get().toInt() and 255
                    val command = header.int; val id = header.int; var body = raw.copyOfRange(9, raw.size)
                    if (flag and 128 != 0) body = PrivateChatProtocol.cipher(Cipher.DECRYPT_MODE, body, sessionKey)
                    if(epoch != generation) return
                    if (flag and 64 != 0) body = ChatProto.inflate(body)
                    require(body.size <= 4 * 1024 * 1024)
                    pending[id]?.takeIf { it.command == command }?.let { if(pending.remove(id,it)) it.response.complete(body) }
                } catch (_: Exception) { if(epoch == generation) fail(IOException("私信响应无法解析")); webSocket.cancel() }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val error = IOException("私信连接已断开，请重试")
                opened.completeExceptionally(error); if(epoch == generation) fail(error)
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { if(epoch == generation) fail(IOException("私信连接已关闭")) }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code,reason); if(epoch == generation) fail(IOException("私信连接已关闭")) }
        })
        try {
            withTimeout(15_000) { opened.await() }
            val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(PUBLIC_KEY)))
            val encrypted = Cipher.getInstance("RSA/ECB/PKCS1Padding").run { init(Cipher.ENCRYPT_MODE, publicKey); doFinal(secret) }
            val request = ChatProto.run { encode(s(1, "-|com.baidu.tieba22.13.1.1"), b(2, encode(s(1, bduss),
                s(2, JSONObject().put("_client_type", "2").put("_client_version", "22.13.1.1").toString()), b(3, encrypted)))) }
            val root = ChatProto.parse(call(1001, request, false)); ChatProto.check(root)
            val data = ChatProto.run { parse(root.bytes(2)) }
            val user = ChatProto.run { parse(data.bytes(2)) }
            val actual = ChatProto.run { user.number(1) }
            require(actual.toString() == uid && ChatProto.run { data.number(6) } == actual) { "私信账号与当前登录账号不一致" }
            groups = data.filter { it.number == 1 }.map { ChatProto.parse(it.bytes) }
                .filter { ChatProto.run { it.number(20) } == 6L }.map { ChatProto.run { it.number(1) } }.filter { it > 0 }
            connected = true; return groups
        } catch (e: Exception) { if(epoch == generation) close(); throw e }
    }
    private fun fail(error: Exception) { connected = false; pending.values.forEach { it.response.completeExceptionally(error) }; pending.clear() }
    fun close() { generation++; socket?.cancel(); socket = null; fail(IOException("私信连接已关闭")); key = byteArrayOf(); owner = ""; groups = emptyList() }
    private suspend fun call(command: Int, data: ByteArray, encrypt: Boolean = true): ByteArray {
        val id = sequence.incrementAndGet(); val response = CompletableDeferred<ByteArray>(); pending[id] = Pending(command,response)
        try {
            val payload = if (encrypt) PrivateChatProtocol.cipher(Cipher.ENCRYPT_MODE, data, key) else data
            val frame = ByteBuffer.allocate(9 + payload.size).put(if (encrypt) 0x88.toByte() else 8.toByte()).putInt(command).putInt(id).put(payload).array()
            if (socket?.send(frame.toByteString()) != true) throw IOException("私信尚未连接")
            return withTimeout(20_000) { response.await() }
        } finally { pending.remove(id) }
    }
    suspend fun messages(cuid: String): List<ChatMessage> {
        if (groups.isEmpty()) return emptyList()
        val request = ChatProto.run { encode(s(1, "$cuid|com.baidu.tieba_mini12.35.1.0"), b(2,
            encode(*(groups.map { b(6, encode(n(1, it), n(2, 0))) } + s(7, "1")).toTypedArray()))) }
        return PrivateChatProtocol.messages(call(202003, request), owner)
    }
    suspend fun send(to: String, text: String, record: Long): String {
        require(to.toLongOrNull()?.let { it > 0 } == true && text.isNotBlank())
        val request = ChatProto.run { encode(b(1, encode(n(2, to.toLong()), n(3, 1), s(4, text), n(6, record)))) }
        val response = call(205001, request)
        val id = PrivateChatProtocol.receipt(response,to,record)
        val group = ChatProto.run { parse(parse(response).bytes(2)).number(2) }
        if(group > 0) groups = (groups + group).distinct()
        return id
    }
}
