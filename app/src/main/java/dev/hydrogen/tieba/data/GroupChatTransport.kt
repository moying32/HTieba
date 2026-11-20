package dev.hydrogen.tieba.data

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import dev.hydrogen.tieba.data.ChatProto.encode
import dev.hydrogen.tieba.data.ChatProto.parse
import dev.hydrogen.tieba.data.ChatProto.n
import dev.hydrogen.tieba.data.ChatProto.s
import dev.hydrogen.tieba.data.ChatProto.b
import dev.hydrogen.tieba.data.ChatProto.number
import dev.hydrogen.tieba.data.ChatProto.bytes
import dev.hydrogen.tieba.data.ChatProto.text

/** One authenticated TLS transaction per explicit send or join request.
 * No implicit join, no automatic write retries; TLS hostnames are validated. */
internal data class GroupSendReceipt(val id: String, val msgKey: String)

internal suspend fun TiebaApi.sendGroupText(room: ChatRoom, person: Person, cuid: String, text: String): GroupSendReceipt {
    val key = GroupChatProtocol.bduk(person.id) + System.currentTimeMillis() * 1000 + java.util.UUID.randomUUID().toString()
    val result = groupTransaction(room,person,cuid,text,key,join = false)
    val id = result.optString("msgid").takeIf { it.toLongOrNull()?.let { id -> id > 0 } == true } ?: "ack:$key"
    return GroupSendReceipt(id,key)
}

internal suspend fun TiebaApi.joinGroup(room: ChatRoom, person: Person, cuid: String) {
    groupTransaction(room,person,cuid,"","",join = true)
}

private suspend fun TiebaApi.groupTransaction(room: ChatRoom, person: Person, cuid: String, text: String, msgKey: String, join: Boolean): JSONObject {
    require(room.id.toLongOrNull()?.let { it > 0 } == true && (join || text.isNotBlank()))
    val uid = person.id; tbs(uid)
    val bduss = BaiduSession.cookie("BDUSS")
    val now = System.currentTimeMillis()
    val fields = linkedMapOf("app_id" to "10773430", "app_version" to "22.13.1.1", "cuid" to cuid,
        "device_type" to "android", "manufacture" to "", "model_type" to "", "request_id" to "$now",
        "sdk_version" to "3460016", "ts" to "$now", "user_key" to "")
    fields["sign"] = MessageDigest.getInstance("MD5").digest("10773430${cuid}android$now".toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    val tokenResult = JSONObject(execute(Request.Builder().url("https://pim.baidu.com/rest/5.0/generate_lcm_token")
        .post(FormBody.Builder().apply { fields.forEach { (k,v) -> add(k,v) } }.build()).build()))
    val token = tokenResult.optString("token"); require(token.isNotBlank() && token != "null") { "百度未返回群连接凭据" }
    val bduk = GroupChatProtocol.bduk(uid)
    return withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val raw = Socket()
            continuation.invokeOnCancellation { runCatching { raw.close() } }
            try {
                raw.connect(InetSocketAddress("common.lcs.baidu.com", 443), 12_000)
                ((SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(raw, "common.lcs.baidu.com", 443, true) as SSLSocket).use { socket ->
                    socket.soTimeout = 20_000
                    socket.sslParameters = socket.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                    socket.startHandshake()
                    val input = DataInputStream(socket.inputStream); val output = DataOutputStream(socket.outputStream)
                    var sequence = 0L
                    fun call(service: Long, method: Long, body: ByteArray): ByteArray {
                        val id = ++sequence
                        val header = encode(b(1, encode(n(1, service), n(2, method), n(3,id), n(4,1))), n(4,id), n(9,0))
                        output.write(byteArrayOf(108,99,112,1)); output.writeInt(header.size + body.size); output.writeInt(header.size)
                        output.write(header); output.write(body); output.flush()
                        repeat(100) {
                            val prefix = ByteArray(4); input.readFully(prefix); require(prefix.contentEquals(byteArrayOf(108,99,112,1))) { "群连接响应格式不匹配" }
                            val total = input.readInt(); val length = input.readInt()
                            require(total in 1..4 * 1024 * 1024 && length in 1..total) { "群消息响应大小无效" }
                            val meta = ByteArray(length); input.readFully(meta); var response = ByteArray(total-length); input.readFully(response)
                            val parsed = parse(meta)
                            if (parsed.number(4) == id && parsed.bytes(2).isNotEmpty()) {
                                val status = parse(parsed.bytes(2))
                                if (status.number(4) != 0L) throw IOException(status.text(5).ifBlank { "群连接被百度拒绝（${status.number(4)}）" })
                                if (parsed.number(3) == 1L) response = ChatProto.inflate(response)
                                require(response.size <= 4 * 1024 * 1024)
                                return response
                            }
                        }
                        throw IOException("群连接未返回对应回执")
                    }
                    val common = encode(s(1,cuid),s(2,"android"),s(6,"10773430"),s(7,"22.13.1.1"),s(8,"3460016"),s(9,"wifi"))
                    val lcmBytes = parse(call(1,1,encode(b(1,encode(n(1,1),s(2,token),b(3,common),n(4,now),n(6,-1),n(7,1)))))).bytes(2)
                    require(lcmBytes.isNotEmpty()) { "百度未确认群连接登录" }
                    val lcm = parse(lcmBytes)
                    if (lcm.number(2) != 0L) throw IOException(lcm.text(3).ifBlank { "群连接登录失败" })
                    fun jsonCall(service: Long, method: Long, body: JSONObject): JSONObject {
                        val result = JSONObject(call(service,method,body.toString().toByteArray(Charsets.UTF_8)).toString(Charsets.UTF_8))
                        require(result.has("err_code")) { "百度未确认群操作结果，请核对群消息后再试" }
                        if (result.optInt("err_code",-1) != 0) throw IOException(result.optString("msg").ifBlank { "群操作失败（${result.optString("err_code")}）" })
                        return result
                    }
                    val login = jsonCall(2,50, JSONObject().put("method",50).put("appid",10773430).put("device_id","android_$cuid")
                        .put("account_type",1).put("token",bduss).put("version",4).put("sdk_version",11250036).put("app_version","22.13.1.1")
                        .put("app_open_type",0).put("client_identifier","{\"zid\":\"\",\"version_code\":\"\"}")
                        .put("tail",0).put("timeout",10).put("cookie","").put("user_type",0)
                        .put("device_info",JSONObject().put("app_version","22.13.1.1").put("platform","android").put("os_version","32").put("appid","10773430"))
                        .put("rpc","{\"rpc_retry_time\":0}").put("client_logid",now*1000))
                    val trigger = login.optJSONArray("trigger_id")?.optLong(0) ?: 0
                    val uk = login.optLong("uk"); require(trigger != 0L && uk != 0L) { "群账号身份不完整" }
                    val result = if(join) jsonCall(3,201,GroupChatProtocol.joinFields(room.id,uk)) else {
                    val content = GroupChatProtocol.content(room,person,text,bduk)
                    jsonCall(3,185,JSONObject().put("method",185).put("mcast_id",room.id.toLong()).put("role",3).put("token",bduss)
                        .put("appid",10773430).put("uk",uk).put("origin_id",trigger).put("type",81)
                        .put("app_safe_ext","{\"haotianjing\":{\"zid\":\"\"}}").put("content",content.toString())
                        .put("msg_key",msgKey).put("account_type",1).put("sdk_version",11250036)
                        .put("event_list",JSONArray().put(JSONObject().put("event","CIMSendBegin").put("timestamp_ms",now)))
                        .put("client_logid",now*1000).put("rpc","{\"rpc_retry_time\":0}"))
                    }
                    if (continuation.isActive) continuation.resume(result)
                }
            } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
            finally { runCatching { raw.close() } }
        }
    }
}

internal object GroupChatProtocol {
    fun joinFields(roomId: String, uk: Long): JSONObject {
        require(roomId.toLongOrNull()?.let { it > 0 } == true && uk != 0L)
        return JSONObject().put("method",201).put("mcast_id",roomId.toLong()).put("appid",10773430).put("uk",uk)
    }
    fun content(room: ChatRoom, person: Person, text: String, bduk: String): JSONObject {
        require(text.isNotBlank() && text.length <= 2000)
        val portrait = runCatching { java.net.URI(person.avatar).rawPath.substringAfterLast('/') }.getOrDefault("")
        val ext = JSONObject().put("user_name",person.name).put("portrait",portrait).put("from","android")
            .put("session_id",room.id.toLong()).put("type",1).put("content",JSONObject()).put("is_sys_msg",0)
        if(room.forumId.toLongOrNull()?.let { it > 0 } == true) ext.put("forum_id",room.forumId.toLong())
        if(person.level > 0) ext.put("level",person.level)
        return JSONObject().put("text",JSONObject().put("room_id",room.id).put("type","0").put("to_uid","0")
            .put("vip","0").put("name",person.name).put("portrait",portrait).put("content_type","0")
            .put("content_body",JSONObject().put("text",text).toString()).put("src","").put("baidu_uk",bduk).put("ext",ext.toString()).toString())
    }
    fun userId(bduk: String): String = runCatching {
        val plain = Cipher.getInstance("AES/CBC/PKCS5Padding").run {
            init(Cipher.DECRYPT_MODE,SecretKeySpec("AFD311832EDEEAEF".toByteArray(),"AES"),IvParameterSpec("2011121211143000".toByteArray()))
            doFinal(Base64.getUrlDecoder().decode(bduk))
        }.toString(Charsets.UTF_8)
        plain.takeIf { it.toLongOrNull()?.let { id -> id > 0 } == true }.orEmpty()
    }.getOrDefault("")
    fun bduk(uid: String): String {
        require(uid.toLongOrNull()?.let { it > 0 } == true)
        val bytes = Cipher.getInstance("AES/CBC/PKCS5Padding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec("AFD311832EDEEAEF".toByteArray(),"AES"), IvParameterSpec("2011121211143000".toByteArray()))
            doFinal(uid.toByteArray())
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
