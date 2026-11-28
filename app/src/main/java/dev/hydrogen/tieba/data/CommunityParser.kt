package dev.hydrogen.tieba.data

import org.json.JSONArray
import org.json.JSONObject

/** Optional counts stay unknown when absent, rather than becoming invented zeroes. */
internal object CommunityParser {
    fun objects(array: JSONArray?): List<JSONObject> = if (array == null) emptyList() else
        (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    fun text(data: JSONObject?, vararg keys: String): String = keys.firstNotNullOfOrNull { key ->
        data?.takeUnless { it.isNull(key) }?.optString(key)?.trim()?.takeIf { it.isNotBlank() && it != "null" }
    }.orEmpty()
    fun number(data: JSONObject?, vararg keys: String): Long? = keys.firstNotNullOfOrNull { key ->
        data?.takeUnless { it.isNull(key) }?.optString(key)?.toLongOrNull()?.takeIf { it >= 0 }
    }
    fun clean(value: String) = value.replace(Regex("<[^>]*>"), "").replace("&nbsp;", " ")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&")
    fun forumExists(data: JSONObject, page: Int): Boolean {
        if (page > 1) return true // Subsequent pages may contain only placeholder metadata.
        val forum = data.optJSONObject("forum") ?: return false
        if (forum.optInt("is_exists", 1) != 0) return true
        // Newest sort also uses is_exists=0 on valid first pages. Require a real
        // forum identifier and at least one actual thread before accepting it.
        return number(forum, "id", "fid")?.let { it > 0 } == true &&
            objects(data.optJSONArray("thread_list")).any { number(it, "id", "tid")?.let { id -> id > 0 } == true }
    }
    fun contentEntries(raw: JSONObject, threads: Boolean): List<JSONObject> {
        val nested = objects(raw.optJSONArray("content")).filter { it.has("post_content") }
        if (threads || nested.isEmpty()) return listOf(raw)
        return nested.map { part -> JSONObject(raw.toString()).put("content", part.optJSONArray("post_content") ?: JSONArray())
            .put("post_id", text(part, "post_id", "pid")).apply { remove("pid") }
            .put("create_time", number(part, "create_time") ?: number(raw, "create_time") ?: 0) }
    }
    fun profile(raw: JSONObject): UserProfile {
        val user = raw.optJSONObject("user") ?: raw
        val normalized = JSONObject(user.toString())
        if (text(normalized, "name_show").isBlank()) normalized.put("name_show", text(user, "nick_name", "user_nickname", "name"))
        if (text(normalized, "id").isBlank()) normalized.put("id", text(user, "uid", "user_id"))
        return UserProfile(IdentityParser.person(normalized), text(user, "intro", "intro_text", "sign"), user.optInt("sex"),
            number(user, "concern_num", "follow_num", "follow_count"), number(user, "fans_num", "fans_count"),
            number(user, "thread_num"), number(user, "post_num"), text(user, "tb_age"),
            text(user.optJSONObject("ip_address"), "location", "ip_address").ifBlank { text(user, "ip_location").ifBlank { user.opt("ip_address")?.takeIf { it is String }?.toString().orEmpty() } },
            user.optInt("has_concerned") == 1 || user.optInt("is_follow") == 1, text(user, "portrait"),
            backgroundImage = IdentityParser.imageUrl(text(user, "bg_pic")))
    }
    fun forum(raw: JSONObject, fallback: String = ""): ForumInfo {
        val f = raw.optJSONObject("forum") ?: raw
        val level = f.optJSONObject("level_info") ?: f.takeIf { number(it, "level_id", "user_level") != null } ?: raw.optJSONObject("user")
        val managers = raw.optJSONObject("bawu")?.optJSONArray("manager_list") ?: f.optJSONArray("managers")
        return ForumInfo(text(f, "id", "fid", "forum_id"), text(f, "name", "forum_name").ifBlank { fallback },
            IdentityParser.imageUrl(text(f, "avatar", "avatar_origin")), text(f, "slogan", "intro", "description"),
            number(f, "member_num", "concern_num"), number(f, "post_num"), number(f, "thread_num"),
            number(level, "level_id", "user_level", "level")?.toInt() ?: 0, text(level, "level_name"), number(level, "cur_score", "score"),
            (f.optJSONObject("sign_in_info")?.optJSONObject("user_info")?.optInt("is_sign_in") == 1),
            objects(managers).map { IdentityParser.person(it) })
    }
    fun commentsCount(thread: JSONObject, includesRoot: Boolean): Int =
        ((number(thread, "reply_num") ?: 0) - if (includesRoot) 1 else 0).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    fun views(thread: JSONObject): Long? = number(thread, "view_num", "view_count", "read_num", "freq_num")
    fun more(data: JSONObject, page: Int, fallback: Boolean): Boolean {
        val p = data.optJSONObject("page") ?: data
        return when {
            p.has("has_more") -> p.optString("has_more") in listOf("1", "true")
            p.has("total_page") -> page < p.optInt("total_page")
            else -> fallback
        }
    }
}
