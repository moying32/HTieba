package dev.hydrogen.tieba.data

import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal data class FollowedForumPage(val forums: List<ForumItem>, val hasMore: Boolean)
enum class ForumSignResult { Signed, AlreadySigned }
enum class ForumSignStatus { Waiting, Signing, Signed, AlreadySigned, Failed }
data class ForumSignEntry(val forum: ForumItem, val status: ForumSignStatus = ForumSignStatus.Waiting, val message: String = "")
data class ForumSignState(
    val owner: String = "", val day: String = "", val running: Boolean = false,
    val loading: Boolean = false, val stopRequested: Boolean = false, val stopped: Boolean = false, val officialBatch: Boolean = false,
    val entries: List<ForumSignEntry> = emptyList(), val error: String? = null, val batchAttempted: Boolean = false,
) {
    val completed get() = entries.count { it.status in setOf(ForumSignStatus.Signed, ForumSignStatus.AlreadySigned, ForumSignStatus.Failed) }
    val signed get() = entries.count { it.status == ForumSignStatus.Signed }
    val alreadySigned get() = entries.count { it.status == ForumSignStatus.AlreadySigned }
    val failed get() = entries.count { it.status == ForumSignStatus.Failed }
    val waiting get() = entries.count { it.status == ForumSignStatus.Waiting }
}

internal object ForumSignParser {
    private fun signFlag(forum: JSONObject): Boolean? = when (forum.optString("is_sign")) {
        "1", "true" -> true
        "0", "false" -> false
        else -> null
    }

    fun guideStatuses(data: JSONObject): Map<String, Boolean> {
        val forums = data.optJSONArray("like_forum") ?: throw IOException("贴吧未返回签到状态")
        return CommunityParser.objects(forums).mapNotNull { forum ->
            val id = CommunityParser.text(forum, "forum_id")
            signFlag(forum)?.takeIf { id.isNotBlank() }?.let { id to it }
        }.toMap()
    }

    fun followed(data: JSONObject): FollowedForumPage {
        if (!data.has("forum_list") && !data.has("common_forum_list")) throw IOException("贴吧未返回关注吧列表，请重试")
        fun entries(value: Any?): List<JSONObject> = when (value) {
            is JSONArray -> CommunityParser.objects(value).also {
                if (it.size != value.length()) throw IOException("关注吧列表格式不完整，请重试")
            }
            is JSONObject -> {
                if (value.length() > 0 && !value.has("non-gconforum") && !value.has("gconforum")) throw IOException("关注吧分组格式暂不支持，尚未开始签到")
                entries(value.opt("non-gconforum")) + entries(value.opt("gconforum"))
            }
            null, JSONObject.NULL -> emptyList()
            else -> throw IOException("关注吧列表格式暂不支持，尚未开始签到")
        }
        val raw = entries(data.opt("forum_list")) + entries(data.opt("common_forum_list"))
        val forums = raw.map {
            ForumItem(CommunityParser.text(it, "id", "forum_id"), CommunityParser.text(it, "name", "forum_name"),
                IdentityParser.imageUrl(CommunityParser.text(it, "avatar")), CommunityParser.text(it, "slogan", "intro"),
                signFlag(it))
        }
        if (forums.any { it.name.isBlank() }) throw IOException("关注吧信息不完整，请刷新后重试")
        val more = when (data.optString("has_more")) {
            "1", "true" -> true
            "0", "false" -> false
            else -> throw IOException("贴吧未确认关注吧列表是否完整，请重试")
        }
        return FollowedForumPage(forums.distinctBy { it.id.ifBlank { it.name } }, more)
    }

    fun result(data: JSONObject): ForumSignResult {
        NativeProtocol.ensureSuccess(data)
        val info = data.optJSONObject("user_info") ?: data.optJSONObject("data")?.optJSONObject("user_info")
        val points = CommunityParser.number(info, "sign_bonus_point")
        return when {
            points != null && points > 0 -> ForumSignResult.Signed
            info?.optString("is_sign_in") == "1" -> ForumSignResult.AlreadySigned
            else -> throw UnconfirmedSignResponse()
        }
    }
}

/** Never start writing from a truncated discovery/recommendation list. */
internal suspend fun allFollowedForums(load: suspend (Int) -> FollowedForumPage): List<ForumItem> {
    val forums = linkedMapOf<String, ForumItem>()
    for (page in 1..100) {
        val result = load(page)
        val before = forums.size
        result.forums.forEach { forums.putIfAbsent(it.id.ifBlank { it.name }, it) }
        if (!result.hasMore) return forums.values.toList()
        if (forums.size == before) throw IOException("关注吧分页没有继续返回新记录，尚未开始签到，请稍后重试")
    }
    throw IOException("关注吧列表过长，未能确认读取完整，尚未开始签到")
}

internal suspend fun TiebaApi.followedForums(uid: String, session: WriteSession? = null): List<ForumItem> = allFollowedForums { page ->
    val data = request("/c/f/forum/like", mapOf("friend_uid" to uid, "page_no" to "$page", "page_size" to "50",
        "_client_version" to "22.13.1.1"), authenticated = true, session = session)
    ForumSignParser.followed(data)
}

internal data class ForumSignReadback(val forums: List<ForumItem>, val error: Exception? = null)

internal fun mergeForumSignStatuses(
    forums: List<ForumItem>, guide: Map<String, Boolean>, verifyUnsigned: Boolean,
): List<ForumItem> = forums.map { forum ->
    val checked = guide[forum.id]
    forum.copy(signedToday = when {
        forum.signedToday == true || checked == true -> true
        verifyUnsigned -> checked
        else -> forum.signedToday ?: checked
    })
}

internal suspend fun TiebaApi.followedForumsWithSigns(session: WriteSession): List<ForumItem> =
    readForumSignStatuses(session, verifyUnsigned = false).forums

internal suspend fun TiebaApi.verifyForumSignStatuses(session: WriteSession): ForumSignReadback =
    readForumSignStatuses(session, verifyUnsigned = true)

private suspend fun TiebaApi.readForumSignStatuses(session: WriteSession, verifyUnsigned: Boolean): ForumSignReadback {
    val forums = try {
        followedForums(session.owner, session)
    } catch (error: IOException) {
        if (!verifyUnsigned) throw error
        return ForumSignReadback(emptyList(), describeSignFailure("读取关注吧", error))
    }
    val needsGuide = forums.any { it.id.isNotBlank() && it.signedToday != true && (verifyUnsigned || it.signedToday == null) }
    var failure: Exception? = null
    val guide = try {
        if (needsGuide) {
            ForumSignParser.guideStatuses(signHybridRequest("/c/f/forum/forumGuide", session, mapOf("sort_type" to "3", "call_from" to "3")))
        } else emptyMap()
    } catch (error: LoginExpiredException) {
        throw error
    } catch (error: IOException) {
        if (verifyUnsigned) failure = describeSignFailure("核对签到状态", error)
        emptyMap()
    }
    session.requireCurrent()
    return ForumSignReadback(mergeForumSignStatuses(forums, guide, verifyUnsigned), failure)
}

internal suspend fun TiebaApi.signOfficialBatch(session: WriteSession) {
    // The service applies its current time, level and membership rules.
    signHybridRequest("/c/c/forum/msign", session, emptyMap())
}

private class UnconfirmedSignResponse(cause: Exception? = null) : IOException("贴吧未确认签到结果", cause)

private suspend fun TiebaApi.signHybridRequest(path: String, session: WriteSession, fields: Map<String, String>): JSONObject {
    session.requireCurrent()
    val body = FormBody.Builder().add("subapp_type", "hybrid").add("_client_version", "22.13.1.1").apply {
        fields.forEach { (key, value) -> add(key, value) }
    }.build()
    val response = execute(Request.Builder().url(NativeProtocol.WEB_HOST + path)
        .header("Cookie", session.cookies).header("Subapp-Type", "hybrid")
        .header("Accept", "application/json").post(body).build())
    session.requireCurrent()
    val data = try {
        JSONObject(response).also(NativeProtocol::ensureSuccess)
    } catch (error: JSONException) {
        throw UnconfirmedSignResponse(error)
    } catch (error: TiebaRejectedException) {
        throw error
    } catch (error: IOException) {
        throw UnconfirmedSignResponse(error)
    }
    return data
}

internal data class ForumSignBatchResult(val entries: List<ForumSignEntry>, val stopped: Boolean, val error: Exception? = null)
internal enum class ForumSignMode { All, Failed, Remaining }
internal fun prepareForumSigns(forums: List<ForumItem>, previous: List<ForumSignEntry>, mode: ForumSignMode): List<ForumSignEntry> {
    val old = previous.associateBy { it.forum.name }
    return forums.mapNotNull { item ->
        val before = old[item.name]
        val selected = when (mode) {
            ForumSignMode.All -> before?.takeIf { it.status == ForumSignStatus.Signed || it.status == ForumSignStatus.AlreadySigned }
                ?.copy(forum = item) ?: ForumSignEntry(item)
            ForumSignMode.Failed -> before?.takeIf { it.status == ForumSignStatus.Failed }?.let { ForumSignEntry(item) }
            ForumSignMode.Remaining -> before?.takeIf { it.status == ForumSignStatus.Waiting }?.let { ForumSignEntry(item) }
        }
        if (item.signedToday == true && selected?.status == ForumSignStatus.Waiting) {
            selected.copy(status = ForumSignStatus.AlreadySigned)
        } else selected
    }
}

internal data class OfficialSignResult(val entries: List<ForumSignEntry>, val error: Exception? = null)

internal suspend fun runOfficialSign(
    entries: List<ForumSignEntry>, sign: suspend () -> Unit, readBack: suspend () -> ForumSignReadback,
): OfficialSignResult {
    val requestError = try {
        sign()
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        e
    }
    if (requestError != null && requestError.requiresSignStop()) {
        return OfficialSignResult(entries, describeSignFailure("批量签到", requestError))
    }
    val readback = try {
        readBack()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        return OfficialSignResult(entries, describeSignFailure("核对签到状态", e))
    }
    val statuses = readback.forums.associateBy { it.id.ifBlank { it.name } }
    val reconciled = entries.map { entry ->
        if (entry.status != ForumSignStatus.Waiting) return@map entry
        val forum = statuses[entry.forum.id.ifBlank { entry.forum.name }]
        if (forum?.signedToday == true) {
            val status = if (requestError == null && entry.forum.signedToday == false) ForumSignStatus.Signed else ForumSignStatus.AlreadySigned
            entry.copy(forum = forum, status = status)
        } else entry.copy(forum = entry.forum.copy(signedToday = forum?.signedToday))
    }
    val error = readback.error ?: requestError?.takeIf { failure ->
        !failure.isBatchUnavailable() && reconciled.any { it.status == ForumSignStatus.Waiting }
    }?.let { describeSignFailure("批量签到", it) }
    return OfficialSignResult(reconciled, error)
}

internal fun requireKnownUnsigned(entries: List<ForumSignEntry>): List<ForumSignEntry> = entries.map {
    if (it.status == ForumSignStatus.Waiting && it.forum.signedToday != false) {
        it.copy(status = ForumSignStatus.Failed, message = "未能确认签到状态，请刷新后重试")
    } else it
}

private fun Exception.isBatchUnavailable(): Boolean {
    if (this !is TiebaRejectedException) return false
    val reason = message.orEmpty()
    return ("批量签到" in reason || "一键签到" in reason) &&
        listOf("等级", "时段", "时间", "不支持", "不可用", "不满足", "没有可", "没有符合").any { it in reason }
}

internal fun Exception.requiresSignStop(): Boolean = when (this) {
    is LoginExpiredException -> true
    is TiebaHttpException -> status in setOf(401, 403, 429)
    is TiebaRejectedException -> code == "need_vcode" ||
        listOf("验证", "频繁", "登录", "封禁", "风控", "限流", "账号受限", "账号限制").any { it in message.orEmpty() }
    else -> false
}

private val safeSignCode = Regex("-?[0-9]{1,10}|need_vcode")

private fun describeSignFailure(stage: String, error: Exception): IOException {
    if (error is LoginExpiredException) return error
    val reason = when (error) {
        is TiebaRejectedException -> {
            val message = when {
                error.code == "need_vcode" || "验证" in error.message.orEmpty() -> "请在百度贴吧完成安全验证"
                error.requiresSignStop() -> "贴吧限制了请求，请稍后重试或检查登录"
                else -> "贴吧拒绝了请求"
            }
            val code = error.code.takeIf(safeSignCode::matches)
            if (code == null) message else "$message（贴吧错误码 $code）"
        }
        is TiebaHttpException -> when (error.status) {
            401, 403 -> "贴吧拒绝了访问，请检查登录或安全验证（HTTP ${error.status}）"
            429 -> "请求过于频繁，请稍后重试（HTTP 429）"
            else -> "贴吧连接失败，结果未确认（HTTP ${error.status}）"
        }
        is SocketTimeoutException -> "请求超时，结果未确认，请稍后继续"
        is UnconfirmedSignResponse -> "贴吧响应无法确认结果，请稍后继续"
        else -> "未能确认结果，请检查网络后继续"
    }
    return IOException("$stage：$reason")
}

/** A stop finishes the current request. An unconfirmed write pauses the queue. */
internal suspend fun runForumSignBatch(
    entries: List<ForumSignEntry>, shouldStop: () -> Boolean,
    sign: suspend (ForumItem) -> ForumSignResult,
    onProgress: (List<ForumSignEntry>) -> Unit,
    pause: suspend () -> Unit = { delay(650) },
): ForumSignBatchResult {
    var current = entries
    for (index in current.indices) {
        if (current[index].status != ForumSignStatus.Waiting) continue
        if (shouldStop()) return ForumSignBatchResult(current, true)
        current = current.toMutableList().apply { this[index] = this[index].copy(status = ForumSignStatus.Signing, message = "") }
        onProgress(current)
        var fatal: Exception? = null
        val updated = try {
            val result = sign(current[index].forum)
            current[index].copy(status = if (result == ForumSignResult.Signed) ForumSignStatus.Signed else ForumSignStatus.AlreadySigned)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            val failure = describeSignFailure("单吧签到", e)
            if (e !is TiebaRejectedException || e.requiresSignStop()) fatal = failure
            current[index].copy(status = ForumSignStatus.Failed, message = failure.message.orEmpty())
        }
        current = current.toMutableList().apply { this[index] = updated }
        onProgress(current)
        if (fatal != null) return ForumSignBatchResult(current, false, fatal)
        if (current.drop(index + 1).any { it.status == ForumSignStatus.Waiting }) {
            if (shouldStop()) return ForumSignBatchResult(current, true)
            pause()
        }
    }
    return ForumSignBatchResult(current, false)
}
