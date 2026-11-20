package dev.hydrogen.tieba.data

import org.json.JSONObject

/** Keep the service's signed URLs intact. Thumbnail signatures cannot be reused for originals. */
internal object MediaParser {
    private fun url(data: JSONObject?, vararg keys: String): String = keys.asSequence()
        .map { IdentityParser.imageUrl(CommunityParser.text(data,it)) }.firstOrNull { it.isNotBlank() }.orEmpty()
    fun image(data: JSONObject, feed: Boolean): ImageSource = if(feed) ImageSource(
        url(data,"small_pic","big_pic","src_pic","origin_pic"),
        url(data,"src_pic","origin_pic","big_pic"), url(data,"src_pic","origin_pic"), url(data,"origin_pic","original_pic"))
    else ImageSource(url(data,"cdn_src_active","cdn_src","big_cdn_src","src","origin_src"),
        url(data,"cdn_src","big_cdn_src"),url(data,"big_cdn_src","origin_src"),url(data,"origin_src","original_src"))
    fun playable(value: String): String = IdentityParser.imageUrl(value).takeUnless {
        it.isBlank() || runCatching {
            val uri=java.net.URI(it)
            uri.host.lowercase() in setOf("tieba.baidu.com","www.tieba.com") ||
                Regex("\\.(?:jpe?g|png|webp|gif|avif)$",RegexOption.IGNORE_CASE).containsMatchIn(uri.path.orEmpty())
        }.getOrDefault(true)
    }.orEmpty()
    fun video(data: JSONObject?): String = listOf("origin_video_url","video_url","video_src","play_url","link","src")
        .asSequence().map { playable(CommunityParser.text(data,it)) }.firstOrNull(String::isNotBlank).orEmpty()
}
