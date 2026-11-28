package dev.hydrogen.tieba.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.IOException

/** A write can only start after its recovery marker has reached durable storage. */
internal class SubmissionAttempt {
    var markerStarted = false
        private set
    var dispatched = false
        private set
    private var acknowledged = false
    fun serverAcknowledged() { acknowledged = true }

    suspend fun beforeDispatch(persist: suspend () -> Boolean) {
        check(!markerStarted) { "同一次操作不能重复派发" }
        currentCoroutineContext().ensureActive()
        markerStarted = true
        if (!persist()) throw IOException("无法保存提交状态，尚未发送。请检查存储空间后重试")
        currentCoroutineContext().ensureActive()
        dispatched = true
    }

    fun uncertain(error: Exception): Boolean = dispatched && (acknowledged || (error !is TiebaRejectedException &&
        error !is CommentPreparationException && error !is LoginExpiredException && error !is IllegalArgumentException))
}

/** Credentials are deliberately not a data class: never expose them via toString(). */
internal class WriteSession(val owner: String, val bduss: String, val cookies: String, val tbs: String) {
    fun requireCurrent() {
        if (bduss.isBlank() || BaiduSession.cookie("BDUSS") != bduss) throw LoginExpiredException()
    }
}

internal suspend fun TiebaApi.writeSession(owner: String): WriteSession {
    require(owner.isNotBlank() && owner != "guest") { "请先确认登录账号" }
    val snapshot = WriteSession(owner, BaiduSession.cookie("BDUSS"), BaiduSession.cookies(), "")
    snapshot.requireCurrent()
    val data = request("/c/s/login", mapOf("bdusstoken" to snapshot.bduss), authenticated = true, session = snapshot)
    if (data.optJSONObject("user")?.optString("id") != owner) throw LoginExpiredException()
    snapshot.requireCurrent()
    val token = NativeProtocol.tbs(data).ifBlank { throw IOException("账号安全校验未完成，请重新登录") }
    return WriteSession(owner, snapshot.bduss, snapshot.cookies, token)
}

enum class UploadStage { Queued, Preparing, Uploading, Uploaded, Failed }

data class CloudWriteState(
    val owner: String, val threadId: String, val title: String, val remove: Boolean,
    val busy: Boolean = false, val uncertain: Boolean = false, val confirmed: Boolean = false,
    val error: String? = null,
) {
    fun encode(): String = JSONObject().put("id", threadId).put("title", title).put("remove", remove).toString()
    companion object {
        fun restore(owner: String, value: String): CloudWriteState? = runCatching {
            val data = JSONObject(value)
            val id = data.getString("id")
            require(id.toLongOrNull()?.let { it > 0 } == true)
            CloudWriteState(owner, id, data.optString("title"), data.optBoolean("remove"), uncertain = true,
                error = "上次云收藏操作结果未确认，请先核对，应用不会自动重发")
        }.getOrNull()
    }
}

/** Absence only proves removal after the entire collection has been read. */
internal fun cloudWriteConfirmed(remove: Boolean, found: Boolean, complete: Boolean): Boolean =
    if (remove) !found && complete else found
