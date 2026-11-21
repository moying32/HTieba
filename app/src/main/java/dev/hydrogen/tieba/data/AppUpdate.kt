package dev.hydrogen.tieba.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** Release tags, not publication date or lexical order, determine upgrades. */
internal data class AppVersion(val major: Long, val minor: Long, val patch: Long, val pre: List<String> = emptyList()) : Comparable<AppVersion> {
    val key get() = "$major.$minor.$patch" + if (pre.isEmpty()) "" else "-${pre.joinToString(".")}"
    override fun compareTo(other: AppVersion): Int {
        for ((a, b) in listOf(major to other.major, minor to other.minor, patch to other.patch)) if (a != b) return a.compareTo(b)
        if (pre.isEmpty() != other.pre.isEmpty()) return if (pre.isEmpty()) 1 else -1
        for (i in 0 until minOf(pre.size, other.pre.size)) {
            val a = pre[i]; val b = other.pre[i]
            val an = a.all(Char::isDigit); val bn = b.all(Char::isDigit)
            val compared = when {
                an && bn -> a.length.compareTo(b.length).takeIf { it != 0 } ?: a.compareTo(b)
                an != bn -> if (an) -1 else 1
                else -> a.compareTo(b)
            }
            if (compared != 0) return compared
        }
        return pre.size.compareTo(other.pre.size)
    }
    companion object {
        fun parse(value: String): AppVersion? {
            val match = Regex("^[vV]?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$")
                .matchEntire(value.trim()) ?: return null
            val pre = match.groupValues[4].takeIf(String::isNotBlank)?.split('.') ?: emptyList()
            if (pre.any { it.all(Char::isDigit) && it.length > 1 && it.startsWith('0') }) return null
            return AppVersion(match.groupValues[1].toLongOrNull() ?: return null,
                match.groupValues[2].toLongOrNull() ?: return null, match.groupValues[3].toLongOrNull() ?: return null, pre)
        }
    }
}

internal data class AppRelease(val version: AppVersion, val tag: String, val title: String, val notes: String,
    val preview: Boolean, val assetName: String, val url: String, val bytes: Long, val sha256: String?)
internal data class UpdateFeed(val release: AppRelease?, val newest: AppVersion?)

internal object ReleaseUpdates {
    const val REPOSITORY = "moying32/HTieba"
    const val API = "https://api.github.com/repos/$REPOSITORY/releases?per_page=30"
    const val PAGE = "https://github.com/$REPOSITORY/releases"
    const val MAX_APK_BYTES = 200L * 1024 * 1024
    fun parse(json: String): UpdateFeed {
        val releases = JSONArray(json)
        val versions = mutableListOf<AppVersion>()
        val candidates = mutableListOf<AppRelease>()
        for (i in 0 until releases.length()) {
            val raw = releases.optJSONObject(i) ?: continue
            if (raw.optBoolean("draft")) continue
            val tag = raw.optString("tag_name")
            val version = AppVersion.parse(tag) ?: continue
            versions += version
            val assets = raw.optJSONArray("assets") ?: continue
            val apks = (0 until assets.length()).mapNotNull(assets::optJSONObject).filter {
                it.optString("name").endsWith(".apk", ignoreCase = true) && it.optString("state", "uploaded") == "uploaded"
            }
            val named = apks.filter { it.optString("name").equals("HTieBa-${version.key}.apk", ignoreCase = true) }
            val universal = apks.filter { it.optString("name").contains("universal", ignoreCase = true) }
            val asset = (named.takeIf { it.size == 1 } ?: universal.takeIf { it.size == 1 } ?: apks.takeIf { it.size == 1 })?.single() ?: continue
            val size = asset.optLong("size")
            val url = asset.optString("browser_download_url")
            if (size !in 1..MAX_APK_BYTES || !validAssetUrl(url)) continue
            val digest = asset.optString("digest").takeIf { it.isNotBlank() && it != "null" }
            val hash = digest?.removePrefix("sha256:")
            if (digest != null && (!digest.startsWith("sha256:") || hash?.matches(Regex("[a-fA-F0-9]{64}")) != true)) continue
            candidates += AppRelease(version, tag, raw.optString("name").ifBlank { tag }.take(160),
                raw.optString("body").takeUnless { it == "null" }.orEmpty().take(12000), raw.optBoolean("prerelease"),
                asset.optString("name"), url, size, hash?.lowercase())
        }
        return UpdateFeed(candidates.maxByOrNull { it.version }, versions.maxOrNull())
    }
    fun validAssetUrl(value: String): Boolean = runCatching {
        val uri = java.net.URI(value)
        uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null && uri.port == -1 &&
            uri.rawPath.startsWith("/$REPOSITORY/releases/download/") && uri.rawPath.endsWith(".apk", ignoreCase = true) &&
            uri.fragment == null && uri.query == null && !uri.path.split('/').any { it == ".." || it == "." }
    }.getOrDefault(false)

    fun copyApk(input: InputStream, output: OutputStream, release: AppRelease, active: () -> Boolean = { true },
                progress: (Long, Long) -> Unit = { _, _ -> }) {
        val hash = MessageDigest.getInstance("SHA-256")
        var count = 0L; var lastProgress = 0L
        val buffer = ByteArray(64 * 1024)
        while (true) {
            if (!active()) throw java.util.concurrent.CancellationException()
            val n = input.read(buffer)
            if (n < 0) break
            count += n
            if (count > release.bytes || count > MAX_APK_BYTES) throw IOException("更新包大小与发布记录不符，请重试")
            output.write(buffer, 0, n); hash.update(buffer, 0, n)
            val now = System.nanoTime()
            if (now - lastProgress >= 100_000_000L) { progress(count, release.bytes); lastProgress = now }
        }
        if (count != release.bytes) throw IOException("更新包下载不完整，请重试")
        val digest = hash.digest().joinToString("") { "%02x".format(it) }
        if (release.sha256 != null && !digest.equals(release.sha256, ignoreCase = true)) throw IOException("更新包校验失败，请重新下载")
        progress(count, release.bytes)
    }
}

internal data class ApkIdentity(val packageName: String, val code: Long, val version: AppVersion?, val signers: Set<String>, val minSdk: Int)
internal object ApkCompatibility {
    fun requireUpgrade(installed: ApkIdentity, archive: ApkIdentity, release: AppRelease, sdk: Int) {
        if (archive.packageName != installed.packageName) throw IOException("更新包不属于氢贴吧，已停止安装")
        if (archive.code <= installed.code || archive.version != release.version) throw IOException("更新包版本与发布记录不符，已停止安装")
        if (archive.signers.isEmpty() || archive.signers != installed.signers) throw IOException("更新包签名与当前应用不同，无法保留数据升级")
        if (archive.minSdk > sdk) throw IOException("此版本不支持当前 Android 系统")
    }
}
