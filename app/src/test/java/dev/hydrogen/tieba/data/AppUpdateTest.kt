package dev.hydrogen.tieba.data

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AppUpdateTest {
    private fun version(s: String) = requireNotNull(AppVersion.parse(s))
    private fun raw(tag: String, names: List<String> = listOf("HTieBa-${tag.removePrefix("v")}.apk")) = JSONObject()
        .put("tag_name", tag).put("name", "发布 $tag").put("prerelease", true).put("body", "更新说明")
        .put("assets", JSONArray(names.map { name -> JSONObject().put("name", name).put("size", 4)
            .put("browser_download_url", "https://github.com/moying32/HTieba/releases/download/$tag/$name") }))
    private fun parse(vararg items: JSONObject) = ReleaseUpdates.parse(JSONArray(items.toList()).toString())
    private fun release(hash: String? = null, size: Long = 4) = AppRelease(version("0.7.9"), "v0.7.9", "新版", "", false,
        "HTieBa-0.7.9.apk", "https://github.com/moying32/HTieba/releases/download/v0.7.9/HTieBa-0.7.9.apk", size, hash)

    @Test fun comparesNumbersInsteadOfStrings() { assertTrue(version("0.10.0") > version("0.9.99")); assertTrue(version("1.0.0") > version("0.99.99")) }
    @Test fun semverPrereleaseOrderAndBuildMetadata() {
        val values = listOf("1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-alpha.beta", "1.0.0-beta", "1.0.0-beta.2", "1.0.0-beta.11", "1.0.0-rc.1", "1.0.0").map(::version)
        values.zipWithNext().forEach { (a, b) -> assertTrue(a < b) }
        assertEquals(version("v1.0.0+build.3"), version("1.0.0"))
    }
    @Test fun rejectsAmbiguousAndOverflowingVersions() {
        listOf("latest", "0.7", "1.0.0.1", "01.0.0", "1.0.0-01", "9999999999999999999999999.0.0", "1.0.0-").forEach { assertNull(AppVersion.parse(it)) }
    }
    @Test fun selectsHighestTagIncludingGithubPrereleases() {
        val feed = parse(raw("v0.7.9"), raw("v0.10.0"), raw("v0.8.0"))
        assertEquals("0.10.0", feed.release!!.version.key); assertTrue(feed.release!!.preview)
    }
    @Test fun ignoresDraftsAndMalformedTags() {
        assertEquals("0.7.9", parse(raw("v0.7.9"), raw("v9.0.0").put("draft", true), raw("nightly")).release!!.version.key)
    }
    @Test fun prefersNamedApkAndRejectsAmbiguousSplits() {
        assertEquals("HTieBa-0.7.9.apk", parse(raw("v0.7.9", listOf("arm64.apk", "HTieBa-0.7.9.apk", "arm32.apk"))).release!!.assetName)
        assertNull(parse(raw("v0.7.9", listOf("arm64.apk", "arm32.apk"))).release)
        assertNotNull(parse(raw("v0.7.9", listOf("arm64.apk", "app-universal.apk"))).release)
    }
    @Test fun newerReleaseWithoutApkDoesNotBecomeUpToDate() {
        val feed = parse(raw("v0.7.9", listOf("source.zip")))
        assertNull(feed.release); assertEquals("0.7.9", feed.newest!!.key)
    }
    @Test fun rejectsForeignOrInsecureOrMalformedAssetUrls() {
        listOf("http://github.com/moying32/HTieba/releases/download/v1/a.apk", "https://github.com/other/HTieba/releases/download/v1/a.apk",
            "https://evil.example/a.apk", "https://github.com.evil.example/moying32/HTieba/releases/download/v1/a.apk",
            "https://user@github.com/moying32/HTieba/releases/download/v1/a.apk", "https://github.com/moying32/HTieba/releases/download/../a.apk").forEach {
            assertFalse(ReleaseUpdates.validAssetUrl(it))
        }
    }
    @Test fun rejectsBrokenDigestsAndImpossibleSizes() {
        val item = raw("v0.7.9"); val asset = item.getJSONArray("assets").getJSONObject(0)
        asset.put("digest", "sha256:bad"); assertNull(parse(item).release)
        asset.put("digest", JSONObject.NULL).put("size", 0); assertNull(parse(item).release)
        asset.put("size", ReleaseUpdates.MAX_APK_BYTES + 1); assertNull(parse(item).release)
    }
    @Test fun downloadsExactBytesAndVerifiesGithubDigest() {
        val data = byteArrayOf(1, 2, 3, 4)
        val digest = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
        val output = ByteArrayOutputStream(); var received = 0L
        ReleaseUpdates.copyApk(data.inputStream(), output, release(digest), progress = { count, _ -> received = count })
        assertArrayEquals(data, output.toByteArray()); assertEquals(4L, received)
    }
    @Test fun rejectsTruncatedOversizedOrTamperedDownloads() {
        listOf(byteArrayOf(1, 2), byteArrayOf(1, 2, 3, 4, 5), byteArrayOf(1, 2, 3, 4)).forEach { data ->
            assertThrows(java.io.IOException::class.java) { ReleaseUpdates.copyApk(data.inputStream(), ByteArrayOutputStream(), release("0".repeat(64))) }
        }
    }
    @Test fun cancelledDownloadStopsBeforeReadingOrProgress() {
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            ReleaseUpdates.copyApk(byteArrayOf(1, 2, 3, 4).inputStream(), ByteArrayOutputStream(), release(), { false })
        }
    }
    private val installed = ApkIdentity("dev.hydrogen.tieba", 32, version("0.7.8"), setOf("trusted"), 26)
    private val archive get() = installed.copy(code = 33, version = version("0.7.9"))
    @Test fun acceptsOnlyNewerMatchingSignedArchive() { ApkCompatibility.requireUpgrade(installed, archive, release(), 36) }
    @Test fun rejectsWrongPackageSignatureVersionAndUnsupportedSdk() {
        listOf(archive.copy(packageName = "other.app"), archive.copy(signers = emptySet()), archive.copy(signers = setOf("untrusted")),
            archive.copy(code = 31), archive.copy(code = 32), archive.copy(version = version("0.8.0")), archive.copy(minSdk = 37)).forEach {
            assertThrows(java.io.IOException::class.java) { ApkCompatibility.requireUpgrade(installed, it, release(), 36) }
        }
    }
}
