package dev.hydrogen.tieba.data

import java.io.IOException
import java.net.URI
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

/** Public GitHub pages do not consume the anonymous REST API quota. No third-party mirror. */
internal object PublicReleaseUpdates {
    const val ATOM = "${ReleaseUpdates.PAGE}.atom"
    private const val BASE = "https://github.com"
    internal data class Entry(val tag: String, val version: AppVersion, val title: String, val notes: String) {
        val page get() = "${ReleaseUpdates.PAGE}/tag/$tag"
        val assets get() = "${ReleaseUpdates.PAGE}/expanded_assets/$tag"
    }
    internal data class Asset(val name: String, val url: String, val hash: String?)

    fun entries(xml: String): List<Entry> {
        // jsoup parses local strings only and never resolves external XML entities.
        if (xml.contains("<!DOCTYPE", true) || xml.contains("<!ENTITY", true)) throw IOException("更新订阅格式不受支持")
        val document = Jsoup.parse(xml, "", Parser.xmlParser())
        val feed = document.children().singleOrNull()?.takeIf {
            it.tagName() == "feed" && it.attr("xmlns") == "http://www.w3.org/2005/Atom"
        } ?: throw IOException("GitHub 未返回有效的更新订阅")
        return feed.select("entry").take(30).mapNotNull { entry ->
            val link = entry.select("link[rel=alternate]").firstOrNull()?.attr("href") ?: return@mapNotNull null
            val prefix = "${ReleaseUpdates.PAGE}/tag/"
            if (!link.startsWith(prefix)) return@mapNotNull null
            val tag = link.removePrefix(prefix)
            // SemVer tags need no URL decoding; reject path/query/fragment characters.
            val version = AppVersion.parse(tag) ?: return@mapNotNull null
            if (tag.any { it !in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.+-" }) return@mapNotNull null
            Entry(tag, version, entry.selectFirst("title")?.text().orEmpty().ifBlank { tag }.take(160),
                Jsoup.parse(entry.selectFirst("content")?.text().orEmpty()).body().wholeText().trim().take(12000))
        }.distinctBy { it.tag }.sortedByDescending { it.version }
    }

    fun asset(entry: Entry, html: String): Asset? {
        val document = Jsoup.parse(html, BASE)
        if (document.select("ul > li").isEmpty()) throw IOException("GitHub 附件页面格式已变化，请重试")
        val assets = document.select("li").mapNotNull { row ->
            val links = row.select("a[href]").filter { it.attr("href").endsWith(".apk", true) }
            if (links.size != 1) return@mapNotNull null
            val link = links.single()
            val url = link.absUrl("href")
            if (!ReleaseUpdates.validAssetUrl(url)) return@mapNotNull null
            if (!URI(url).rawPath.startsWith("/${ReleaseUpdates.REPOSITORY}/releases/download/${entry.tag}/")) return@mapNotNull null
            val digest = row.select("clipboard-copy[value^=sha256:]").firstOrNull()?.attr("value")
                ?: Regex("sha256:[^\\s<]+").find(row.text())?.value
            val hash = digest?.removePrefix("sha256:")
            if (hash != null && !hash.matches(Regex("[a-fA-F0-9]{64}"))) throw IOException("GitHub 附件摘要格式无效")
            Asset(link.text().trim(), url, hash?.lowercase())
        }
        val named = assets.filter { it.name.equals("HTieBa-${entry.version.key}.apk", true) }
        val universal = assets.filter { it.name.contains("universal", true) }
        return (named.takeIf { it.size == 1 } ?: universal.takeIf { it.size == 1 } ?: assets.takeIf { it.size == 1 })?.single()
    }

    fun preview(entry: Entry, html: String): Boolean {
        val document = Jsoup.parse(html)
        if (document.select("include-fragment[src]").none { it.attr("src") == entry.assets })
            throw IOException("GitHub 发布页面格式已变化，请重试")
        return document.select(".Label").any { it.text().equals("Pre-release", true) }
    }
}

internal class UpdateFeedLoader(
    private val get: suspend (String, String) -> String,
    private val size: suspend (String) -> Long,
    private val api: suspend () -> UpdateFeed,
) {
    suspend fun fetch(): UpdateFeed {
        try {
            val entries = PublicReleaseUpdates.entries(get(PublicReleaseUpdates.ATOM, "application/atom+xml"))
            for (entry in entries) {
                val asset = PublicReleaseUpdates.asset(entry, get(entry.assets, "text/html")) ?: continue
                val preview = PublicReleaseUpdates.preview(entry, get(entry.page, "text/html"))
                // The UI's rounded KB/MB display is insufficient for exact byte validation.
                val bytes = size(asset.url)
                if (bytes !in 1..ReleaseUpdates.MAX_APK_BYTES) throw IOException("更新包大小无效")
                return UpdateFeed(AppRelease(entry.version, entry.tag, entry.title, entry.notes, preview,
                    asset.name, asset.url, bytes, asset.hash), entries.maxOfOrNull { it.version })
            }
            return UpdateFeed(null, entries.maxOfOrNull { it.version })
        } catch (publicError: IOException) {
            try { return api() }
            catch (apiError: IOException) {
                throw IOException("GitHub 更新服务暂不可用，公开发布页与备用接口均未成功，请稍后重试", publicError).apply {
                    addSuppressed(apiError)
                }
            }
        }
    }
}
