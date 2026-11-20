package dev.hydrogen.tieba.data

import org.json.JSONObject
import java.net.URI

internal object EmojiParser {
    fun parse(raw: JSONObject): ContentPart {
        val id = CommunityParser.text(raw, "text")
        val label = CommunityParser.text(raw, "c").ifBlank { "表情" }
        val explicit = listOf("origin_src", "dynamic", "static", "src", "icon").firstNotNullOfOrNull { key ->
            resource(CommunityParser.text(raw, key)).takeIf(String::isNotBlank)
        }.orEmpty().ifBlank { resource(CommunityParser.text(raw.optJSONObject("meme_info"), "pic_url", "thumbnail")) }
        // The API's classic identifier omits the suffix for the first face.
        val classic = Regex("image_emoticon(\\d{0,3})").matchEntire(id)?.let {
            val number = it.groupValues[1].ifBlank { "1" }
            "https://tb2.bdstatic.com/tb/editor/images/client/image_emoticon$number.png"
        }.orEmpty()
        return ContentPart(text = if (label.startsWith("[")) label else "[$label]", emoji = explicit.ifBlank { classic })
    }

    fun resource(value: String): String {
        val secure = IdentityParser.imageUrl(value)
        if (secure.isBlank()) return ""
        val uri = runCatching { URI(secure) }.getOrNull() ?: return ""
        val host = uri.host.lowercase()
        if (!(host == "baidu.com" || host.endsWith(".baidu.com") || host == "bdimg.com" || host.endsWith(".bdimg.com") ||
            host == "bdstatic.com" || host.endsWith(".bdstatic.com") || host.endsWith(".bcebos.com"))) return ""
        // Legacy static.tieba.baidu.com does not have a valid matching TLS cert.
        return if (host == "static.tieba.baidu.com" && uri.rawPath.startsWith("/tb/editor/images/"))
            "https://tb2.bdstatic.com${uri.rawPath}${uri.rawQuery?.let { "?$it" }.orEmpty()}" else secure
    }
}
