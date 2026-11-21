package dev.hydrogen.tieba.data

import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest

internal class TiebaRejectedException(val code: String, message: String) : IOException("$message（贴吧错误码 $code）")
internal class CommentPreparationException(cause: Exception) : IOException(cause.message, cause)

internal object NativeProtocol {
    const val APP_HOST = "https://tiebac.baidu.com"
    const val WEB_HOST = "https://tieba.baidu.com"

    fun readingFields(id: String, page: Int, onlyAuthor: Boolean, sort: ReplySort): Map<String, String> = mapOf(
        "kz" to id, "pn" to "$page", "rn" to "20", "with_floor" to "1",
        "lz" to if (onlyAuthor) "1" else "0", "r" to sort.code,
    )

    fun agreeFields(threadId: String, postId: String, undo: Boolean, isComment: Boolean, forumId: String): Map<String, String> {
        require(threadId.toLongOrNull()?.let { it > 0 } == true)
        return buildMap {
            put("_client_version", "22.13.1.1")
            put("thread_id", threadId)
            put("op_type", if (undo) "1" else "0")
            put("obj_type", if (postId.isBlank()) "3" else if (isComment) "2" else "1")
            put("agree_type", "2")
            put("is_long_press_agree", "0")
            if (postId.isNotBlank()) put("post_id", postId)
            if (forumId.isNotBlank()) put("forum_id", forumId)
        }
    }

    fun sign(fields: Map<String, String>): String {
        val text = fields.toSortedMap().entries.joinToString("") { "${it.key}=${it.value}" } + "tiebaclient!!!"
        return MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02X".format(it.toInt() and 255) }
    }

    fun replyFields(parent: PostItem?, target: PostItem?): Map<String, String> = buildMap {
        require(parent != null || target == null) { "回复目标缺少所属楼层" }
        if (parent != null) {
            require(parent.id.toLongOrNull()?.let { it > 0 } == true && parent.floor > 0) { "回复楼层不可用，请刷新后重试" }
            put("quote_id", parent.id)
            put("floor_num", parent.floor.toString())
            if (parent.author.id.isNotBlank()) put("reply_uid", parent.author.id)
            if (target != null) {
                require(target.id.toLongOrNull()?.let { it > 0 } == true && target.floor == 0) { "楼中楼回复目标不可用" }
                put("sub_post_id", target.id)
                remove("reply_uid")
                if (target.author.id.isNotBlank()) put("reply_uid", target.author.id)
            }
        }
    }

    fun tbs(data: JSONObject): String = data.optJSONObject("anti")?.optString("tbs").orEmpty()
        .ifBlank { data.optString("tbs") }.takeUnless { it == "null" }.orEmpty()

    fun commentId(data: JSONObject): String = listOf(
        data.optString("pid"), data.optString("post_id"), data.optJSONObject("post")?.optString("id").orEmpty(),
        data.optJSONObject("post")?.optString("pid").orEmpty(), data.optJSONObject("data")?.optString("pid").orEmpty(),
        data.optJSONObject("data")?.optJSONObject("post")?.optString("id").orEmpty(),
    ).firstOrNull { it.toLongOrNull()?.let { id -> id > 0 } == true }.orEmpty()

    fun threadId(data: JSONObject): String = listOf(data.optString("tid"), data.optString("thread_id"),
        data.optJSONObject("thread")?.optString("id").orEmpty(), data.optJSONObject("thread")?.optString("tid").orEmpty(),
        data.optJSONObject("data")?.optString("tid").orEmpty()).firstOrNull { it.toLongOrNull()?.let { id -> id > 0 } == true }.orEmpty()

    fun ensureSuccess(data: JSONObject) {
        if (data.optJSONObject("info")?.optString("need_vcode") == "1" || data.optString("need_vcode") == "1")
            throw TiebaRejectedException("need_vcode", "百度要求安全验证，请在官方页完成后重试")
        val nested = data.optJSONObject("error")
        val code = data.optString("error_code").ifBlank { data.optString("no") }
            .ifBlank { if (nested?.has("errno") == true) nested.optString("errno") else "" }
        if (code.isBlank()) throw IOException("服务端没有确认操作结果，请先查看原帖")
        val nestedCode = nested?.optString("errno", "0") ?: "0"
        if (code != "0" || nestedCode != "0") {
            val message = data.optString("error_msg").ifBlank { nested?.optString("errmsg").orEmpty() }
                .ifBlank { data.optString("errmsg") }.ifBlank { "操作未完成，请稍后重试" }.take(160)
            throw TiebaRejectedException(if (code == "0") nestedCode else code, message)
        }
    }
}
