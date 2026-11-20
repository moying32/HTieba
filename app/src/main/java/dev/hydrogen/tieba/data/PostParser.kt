package dev.hydrogen.tieba.data

import org.json.JSONArray
import org.json.JSONObject

/** Same parser for complete floors and the inline preview; previews never trigger extra requests. */
internal object PostParser {
    fun location(data: JSONObject?): String {
        fun scalar(key: String) = (data?.opt(key) as? String)?.trim()?.takeUnless { it == "null" }.orEmpty()
        return scalar("ip_address").ifBlank { scalar("ip_location") }
            .ifBlank { CommunityParser.text(data?.optJSONObject("ip_address"), "location", "ip_address") }
    }

    fun post(raw: JSONObject, people: Map<String, Person>, content: (JSONArray?) -> List<ContentPart>,
             nested: Boolean = false): PostItem {
        val inlineAuthor = IdentityParser.person(raw.optJSONObject("author"))
        val author = people[CommunityParser.text(raw, "author_id")] ?: inlineAuthor
        val previews = if (nested) emptyList() else CommunityParser.objects(
            raw.optJSONObject("sub_post_list")?.optJSONArray("sub_post_list") ?: raw.optJSONArray("sub_post_list")
        ).map { post(it, people, content, nested = true) }.filter { it.id.isNotBlank() }.distinctBy { it.id }
        return PostItem(
            id = CommunityParser.text(raw, "id", "pid"), floor = if (nested) 0 else raw.optInt("floor"),
            author = author, content = content(raw.optJSONArray("content")), timestamp = raw.optLong("time"),
            likes = raw.optJSONObject("agree")?.optInt("agree_num")?.coerceAtLeast(0) ?: 0,
            subReplies = maxOf(raw.optInt("sub_post_number"), previews.size),
            liked = raw.optJSONObject("agree")?.optInt("has_agree") == 1,
            location = location(raw).ifBlank { author.location }, previewReplies = previews,
        )
    }
}

fun PostItem.copyableText(): String = content.joinToString("") {
    when {
        it.isVideo || it.video.isNotBlank() -> "[视频]"
        it.image.isNotBlank() -> "[图片]"
        it.text.isNotBlank() -> it.text
        else -> it.link
    }
}.trim()
