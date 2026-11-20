package dev.hydrogen.tieba.data

import org.json.JSONObject
import java.net.URI

/** Public profile metadata only; missing badges or levels must stay missing. */
internal object IdentityParser {
    fun chatAvatar(value: String): String = portraitUrl(value)
    fun imageUrl(value: String): String {
        val source = value.trim().let { if (it.startsWith("//")) "https:$it" else it }
        return runCatching {
            val uri = URI(source)
            if (uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank() || uri.rawUserInfo != null) return ""
            // The legacy avatar hostname no longer presents a matching TLS certificate.
            val host = if (uri.host.equals("tb.himg.baidu.com", true)) "himg.bdimg.com" else uri.host
            "https://$host${if (uri.port > 0 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""}${uri.rawPath.orEmpty()}${uri.rawQuery?.let { "?$it" }.orEmpty()}"
        }.getOrDefault("")
    }

    fun portraitUrl(value: String): String {
        val portrait = value.trim()
        if (portrait.isBlank() || portrait == "null") return ""
        if (portrait.startsWith("//") || portrait.contains("://")) return imageUrl(portrait)
        if (!Regex("[A-Za-z0-9._~-]+(?:\\?[^\\s#]*)?").matches(portrait)) return ""
        return imageUrl("https://himg.bdimg.com/sys/portrait/item/$portrait")
    }

    private fun JSONObject.text(key: String): String = if (isNull(key)) "" else optString(key).trim()

    fun person(data: JSONObject?): Person {
        if (data == null) return Person()
        val badges = data.optJSONArray("iconinfo")
        val parsed = (0 until (badges?.length() ?: 0)).mapNotNull { index ->
            val badge = badges?.optJSONObject(index) ?: return@mapNotNull null
            val terminal = badge.optJSONObject("terminal")
            if (terminal != null && terminal.optInt("client", 0) != 1) return@mapNotNull null
            val image = imageUrl(badge.text("icon"))
            if (image.isBlank()) return@mapNotNull null
            val positions = badge.optJSONObject("position")
            val surfaces = IdentitySurface.entries.filter { positions == null || positions.optInt(it.apiKey, 0) == 1 }.toSet()
            if (surfaces.isEmpty()) return@mapNotNull null
            UserBadge(badge.text("name"), image,
                badge.text("title").ifBlank { badge.text("desc") }.ifBlank { "贴吧徽章" }, surfaces)
        }.distinctBy { it.image }
        return Person(
            id = data.text("id").ifBlank { data.text("uid") },
            name = data.text("name_show").ifBlank { data.text("name") }.ifBlank { "吧友" },
            avatar = portraitUrl(data.text("portrait").ifBlank { data.text("avatar") }),
            level = data.optInt("level_id").coerceIn(0, 99), levelName = data.text("level_name"),
            role = if (data.optInt("is_bawu") == 1) when (data.text("bawu_type")) {
                "manager" -> "吧主"
                "assist" -> "小吧主"
                else -> "吧务"
            } else "",
            badges = parsed,
            location = PostParser.location(data),
        )
    }
}
