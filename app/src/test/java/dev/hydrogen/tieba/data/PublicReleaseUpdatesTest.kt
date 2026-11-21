package dev.hydrogen.tieba.data

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PublicReleaseUpdatesTest {
    private fun fixture(name: String) = javaClass.getResource("/updates/$name.txt")!!.readText()
    private val entries get() = PublicReleaseUpdates.entries(fixture("atom"))
    private val newest get() = entries.first()
    private val emptyAssets = "<ul><li><a href='/archive/source.zip'>Source code</a></li></ul>"
    private fun oneAsset(url: String, digest: String = "") = "<ul><li><a href='$url'>HTieBa-9.5.0.apk</a>$digest</li></ul>"

    @Test fun parsesRealPublicFeedAndKeepsTestWarning() {
        assertEquals(listOf("9.5.0", "0.7.6"), entries.map { it.version.key })
        assertTrue(newest.notes.contains("不是可用客户端"))
        assertFalse(newest.notes.contains("<li>"))
    }
    @Test fun parsesRealAssetHashAndPreview() {
        val asset = PublicReleaseUpdates.asset(newest, fixture("assets"))!!
        assertEquals("HTieBa-9.5.0.apk", asset.name)
        assertEquals("cd0275192c7339465d63ffe7338be13951d32ffc50e2acc89b0323b691d0f16e", asset.hash)
        assertTrue(PublicReleaseUpdates.preview(newest, fixture("page")))
    }
    @Test fun rejectsForeignFeedLinksAndExternalEntities() {
        assertTrue(PublicReleaseUpdates.entries(fixture("atom").replace("https://github.com/moying32/HTieba/releases/tag/", "https://evil.example/")).isEmpty())
        assertThrows(IOException::class.java) { PublicReleaseUpdates.entries("<!DOCTYPE feed [<!ENTITY x SYSTEM 'file:///secret'>]>${fixture("atom")}") }
        assertThrows(IOException::class.java) { PublicReleaseUpdates.entries("<html>Rate limited</html>") }
    }
    @Test fun rejectsAssetsFromAnotherRepositoryTagOrScheme() {
        val valid = "https://github.com/moying32/HTieba/releases/download/v9.5.0/app.apk"
        listOf(valid.replace("https:", "http:"), valid.replace("moying32", "someone"),
            valid.replace("v9.5.0/", "v0.7.6/"), "https://evil.example/app.apk").forEach {
            assertNull(PublicReleaseUpdates.asset(newest, oneAsset(it)))
        }
    }
    @Test fun rejectsBrokenDigestAndChangedHtml() {
        assertThrows(IOException::class.java) { PublicReleaseUpdates.asset(newest, fixture("assets").replace("sha256:cd0275192c7339465d63ffe7338be13951d32ffc50e2acc89b0323b691d0f16e", "sha256:bad")) }
        assertThrows(IOException::class.java) { PublicReleaseUpdates.asset(newest, "<html>Sign in</html>") }
        assertThrows(IOException::class.java) { PublicReleaseUpdates.preview(newest, "<html>Sign in</html>") }
    }
    @Test fun publicSuccessNeverCallsRateLimitedRestApi() = runBlocking {
        val calls = mutableListOf<String>()
        val feed = UpdateFeedLoader({ url, _ ->
            calls += url
            fixture(when (url) { PublicReleaseUpdates.ATOM -> "atom"; newest.assets -> "assets"; newest.page -> "page"; else -> error(url) })
        }, { assertTrue(ReleaseUpdates.validAssetUrl(it)); 8012 }, { error("REST API must not be called") }).fetch()
        assertEquals("9.5.0", feed.release!!.version.key)
        assertEquals(8012L, feed.release!!.bytes)
        assertEquals(3, calls.size)
    }
    @Test fun publicFailureUsesApiFallback() = runBlocking {
        val expected = UpdateFeed(null, AppVersion.parse("1.0.0"))
        assertEquals(expected, UpdateFeedLoader({ _, _ -> throw IOException("403") }, { error("unused") }, { expected }).fetch())
    }
    @Test fun bothFailuresRemainRetryableErrors() {
        val error = assertThrows(IOException::class.java) { runBlocking {
            UpdateFeedLoader({ _, _ -> throw IOException("public 429") }, { error("unused") }, { throw IOException("API 403") }).fetch()
        } }
        assertEquals("public 429", error.cause!!.message)
        assertEquals("API 403", error.suppressed.single().message)
    }
    @Test fun cancellationDoesNotStartAnotherRequest() {
        assertThrows(CancellationException::class.java) { runBlocking {
            UpdateFeedLoader({ _, _ -> throw CancellationException() }, { error("unused") }, { error("must not fall back") }).fetch()
        } }
    }
    @Test fun missingApkStillReportsNewerVersion() = runBlocking {
        val feed = UpdateFeedLoader({ url, _ -> if (url == PublicReleaseUpdates.ATOM) fixture("atom") else emptyAssets },
            { error("unused") }, { error("unused") }).fetch()
        assertNull(feed.release)
        assertEquals("9.5.0", feed.newest!!.key)
    }
    @Test fun invalidExactLengthDoesNotOfferDownload() = runBlocking {
        var fallback = false
        val feed = UpdateFeedLoader({ url, _ -> fixture(when (url) { PublicReleaseUpdates.ATOM -> "atom"; newest.assets -> "assets"; else -> "page" }) },
            { ReleaseUpdates.MAX_APK_BYTES + 1 }, { fallback = true; UpdateFeed(null, null) }).fetch()
        assertTrue(fallback)
        assertNull(feed.release)
    }
}
