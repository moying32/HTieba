package dev.hydrogen.tieba.data

import java.io.IOException
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import dev.hydrogen.tieba.data.ChatProto.b
import dev.hydrogen.tieba.data.ChatProto.n
import dev.hydrogen.tieba.data.ChatProto.s
import dev.hydrogen.tieba.data.ChatProto.bytes
import dev.hydrogen.tieba.data.ChatProto.number
import dev.hydrogen.tieba.data.ChatProto.text

/** Baidu ProfileReqIdl / ProfileResIdl, cmd 303012; only mapped public fields. */
internal object ProfileProtocol {
    fun request(uid: String, bduss: String): ByteArray {
        require(uid.toLongOrNull()?.let { it > 0 } == true) { "请输入有效的贴吧 UID" }
        val common = ChatProto.encode(n(1, 2), s(2, "22.13.1.1"), s(10, bduss))
        return ChatProto.encode(b(1, ChatProto.encode(n(1, uid.toLong()), n(2, 1), b(9, common), n(15, 1))))
    }

    fun profile(bytes: ByteArray, requested: String): UserProfile {
        val root = ChatProto.parse(bytes)
        val error = ChatProto.parse(root.bytes(1))
        if (error.number(1) != 0L) throw TiebaRejectedException(error.number(1).toString(), error.text(2).ifBlank { "用户资料暂不可用" })
        val user = ChatProto.parse(ChatProto.parse(root.bytes(2)).bytes(1))
        if (user.number(2).toString() != requested) throw IOException("百度返回的用户资料与目标 UID 不一致")
        val normalized = JSONObject().put("id", requested)
        mapOf(3 to "name", 4 to "name_show", 5 to "portrait", 34 to "intro", 38 to "tb_age", 127 to "ip_address").forEach { (field, name) ->
            if (user.any { it.number == field }) normalized.put(name, user.text(field))
        }
        mapOf(30 to "fans_num", 31 to "concern_num", 32 to "sex", 37 to "post_num", 87 to "thread_num", 46 to "has_concerned").forEach { (field, name) ->
            if (user.any { it.number == field }) normalized.put(name, user.number(field))
        }
        return CommunityParser.profile(normalized)
    }
}

internal suspend fun TiebaApi.binaryProfile(uid: String): UserProfile {
    val body = MultipartBody.Builder().setType(MultipartBody.FORM)
        .addFormDataPart("data", "file", ProfileProtocol.request(uid, BaiduSession.cookie("BDUSS")).toRequestBody(null)).build()
    val request = Request.Builder().url("${NativeProtocol.APP_HOST}/c/u/user/profile?cmd=303012")
        .header("User-Agent", "bdtb for Android 22.13.1.1").header("Accept", "application/octet-stream")
        .post(body).build()
    return ProfileProtocol.profile(executeBinary(request), uid)
}
