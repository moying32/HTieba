package dev.hydrogen.tieba.data

import org.json.JSONObject

internal object NoticeParser {
    fun parse(raw: JSONObject): Notice {
        val thread = raw.optJSONObject("thread_info")
        val post = raw.optJSONObject("post_info")
        val user = raw.optJSONObject("agreeer") ?: raw.optJSONObject("replyer")
            ?: raw.optJSONObject("user") ?: raw.optJSONObject("agree_user")
        fun text(data: JSONObject?, vararg keys: String) = CommunityParser.text(data, *keys)
        val body = (raw.opt("content") as? String).orEmpty().ifBlank {
            CommunityParser.objects(post?.optJSONArray("content")).joinToString("") { text(it, "text") }
        }.ifBlank { text(raw, "agree_content") }
        return Notice(text(raw, "id", "post_id", "agree_id"),
            text(raw, "thread_id", "tid").ifBlank { text(thread, "id", "tid") },
            text(raw, "post_id", "pid").ifBlank { text(post, "id", "pid") },
            IdentityParser.person(user), text(raw, "title").ifBlank { text(thread, "title") },
            body, text(raw, "quote_content"), raw.optLong("time"), raw.optInt("unread") == 1,
            isFloor = raw.optInt("is_floor") == 1)
    }

    fun action(kind: NoticeKind) = when (kind) {
        NoticeKind.Reply -> "回复我："
        NoticeKind.At -> "提到了我："
        NoticeKind.Like -> "赞了我的内容"
    }

    /** Normalize only the known self-address; quoted body and third-party replies stay intact. */
    fun body(notice: Notice, kind: NoticeKind, selfName: String): String {
        if (kind != NoticeKind.Reply) return notice.text
        val targets = (listOf("我") + if (selfName.isNotBlank()) listOf(selfName, "[$selfName]") else emptyList())
            .joinToString("|") { Regex.escape(it) }
        return notice.text.replaceFirst(Regex("^回复\\s*(?:$targets)\\s*[:：]\\s*"), "")
    }
}
