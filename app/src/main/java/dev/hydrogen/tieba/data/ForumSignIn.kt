package dev.hydrogen.tieba.data

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

internal data class FollowedForumPage(val forums: List<ForumItem>, val hasMore: Boolean)
enum class ForumSignResult { Signed, AlreadySigned }
enum class ForumSignStatus { Waiting, Signing, Signed, AlreadySigned, Failed }
data class ForumSignEntry(val forum: ForumItem, val status: ForumSignStatus = ForumSignStatus.Waiting, val message: String = "")
data class ForumSignState(
    val owner: String = "", val day: String = "", val running: Boolean = false,
    val loading: Boolean = false, val stopRequested: Boolean = false, val stopped: Boolean = false,
    val entries: List<ForumSignEntry> = emptyList(), val error: String? = null,
) {
    val completed get() = entries.count { it.status in setOf(ForumSignStatus.Signed, ForumSignStatus.AlreadySigned, ForumSignStatus.Failed) }
    val signed get() = entries.count { it.status == ForumSignStatus.Signed }
    val alreadySigned get() = entries.count { it.status == ForumSignStatus.AlreadySigned }
    val failed get() = entries.count { it.status == ForumSignStatus.Failed }
    val waiting get() = entries.count { it.status == ForumSignStatus.Waiting }
}

internal object ForumSignParser {
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
                IdentityParser.imageUrl(CommunityParser.text(it, "avatar")), CommunityParser.text(it, "slogan", "intro"))
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
            else -> throw IOException("贴吧未确认签到结果，请查看该吧的签到状态后重试")
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

internal suspend fun TiebaApi.followedForums(uid: String): List<ForumItem> = allFollowedForums { page ->
    val data = request("/c/f/forum/like", mapOf("friend_uid" to uid, "page_no" to "$page", "page_size" to "50",
        "_client_version" to "22.13.1.1"), authenticated = true)
    ForumSignParser.followed(data)
}

internal data class ForumSignBatchResult(val entries: List<ForumSignEntry>, val stopped: Boolean, val error: Exception? = null)
internal enum class ForumSignMode { All, Failed, Remaining }
internal fun prepareForumSigns(forums: List<ForumItem>, previous: List<ForumSignEntry>, mode: ForumSignMode): List<ForumSignEntry> {
    val old = previous.associateBy { it.forum.name }
    return forums.mapNotNull { item ->
        val before = old[item.name]
        when (mode) {
            ForumSignMode.All -> before?.takeIf { it.status in setOf(ForumSignStatus.Signed, ForumSignStatus.AlreadySigned) }
                ?.copy(forum = item) ?: ForumSignEntry(item)
            ForumSignMode.Failed -> before?.takeIf { it.status == ForumSignStatus.Failed }?.copy(forum = item, status = ForumSignStatus.Waiting, message = "")
            ForumSignMode.Remaining -> before?.takeIf { it.status == ForumSignStatus.Waiting }?.copy(forum = item)
        }
    }
}

/** Serial requests; a stop finishes the current request, and no failed write auto-retries. */
internal suspend fun runForumSignBatch(
    entries: List<ForumSignEntry>, shouldStop: () -> Boolean,
    sign: suspend (ForumItem) -> ForumSignResult, describe: (Exception) -> String,
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
            if (e is LoginExpiredException || (e is TiebaRejectedException &&
                    (e.code == "need_vcode" || listOf("验证", "频繁", "登录", "封禁").any { e.message.orEmpty().contains(it) }))) fatal = e
            current[index].copy(status = ForumSignStatus.Failed, message = describe(e))
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
