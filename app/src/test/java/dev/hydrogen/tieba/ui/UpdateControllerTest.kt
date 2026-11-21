package dev.hydrogen.tieba.ui

import dev.hydrogen.tieba.data.*
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class UpdateControllerTest {
    private fun release(v: String = "0.8.0") = AppRelease(requireNotNull(AppVersion.parse(v)), "v$v", "新版本", "", false,
        "HTieBa-$v.apk", "https://github.com/moying32/HTieba/releases/download/v$v/HTieBa-$v.apk", 4, null)
    private class Source(var candidate: AppRelease?) : UpdateSource {
        var requests = 0; var downloads = 0; var checks = 0
        var fetchBlock: (suspend () -> UpdateFeed)? = null
        var downloadBlock: (suspend ((Long, Long) -> Unit) -> File)? = null
        var verifyFailure = false
        override suspend fun fetch(): UpdateFeed { requests++; return fetchBlock?.invoke() ?: UpdateFeed(candidate, candidate?.version) }
        override suspend fun download(release: AppRelease, progress: (Long, Long) -> Unit): File {
            downloads++; return downloadBlock?.invoke(progress) ?: File("update-test.apk")
        }
        override suspend fun verify(file: File, release: AppRelease) { checks++; if (verifyFailure) throw IOException("校验失败") }
    }
    private fun controller(scope: CoroutineScope, source: Source, read: () -> String? = { null }, write: (String) -> Unit = {}) =
        UpdateController(scope, source, requireNotNull(AppVersion.parse("0.7.9")), read, write)

    @Test fun checksOncePerLaunchAndNeverDowngrades() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined); val s = Source(release("0.7.6")); val c = controller(scope, s)
        c.onLaunch(); c.onLaunch(); assertEquals(1, s.requests); assertNull(c.release); assertFalse(c.dialog); assertTrue(c.checked)
        scope.cancel()
    }
    @Test fun skippedVersionSurvivesANewControllerButManualCheckCanReopenIt() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined); val s = Source(release()); var skip: String? = null
        val c = controller(scope, s, { skip }, { skip = it }); c.onLaunch(); assertTrue(c.dialog); c.skip()
        assertEquals("0.8.0", skip); assertFalse(c.dialog); assertTrue(c.skipped)
        val next = controller(scope, s, { skip }, { skip = it }); next.onLaunch(); assertFalse(next.dialog)
        next.check(); assertTrue(next.dialog); assertTrue(next.skipped); scope.cancel()
    }
    @Test fun skippingOneVersionDoesNotHideAFutureRelease() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined); val c = controller(scope, Source(release("0.8.1")), { "0.8.0" })
        c.onLaunch(); assertTrue(c.dialog); assertFalse(c.skipped); scope.cancel()
    }
    @Test fun dismissingDoesNotPersistASkip() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined); var writes = 0
        val c = controller(scope, Source(release()), write = { writes++ }); c.onLaunch(); c.dismiss()
        assertFalse(c.dialog); assertEquals(0, writes); c.show(); assertTrue(c.dialog); scope.cancel()
    }
    @Test fun manualCheckPromotesAnInFlightAutomaticCheckWithoutDuplicateRequests() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined); val gate = CompletableDeferred<UpdateFeed>()
        val s = Source(release()).apply { fetchBlock = { gate.await() } }; val c = controller(scope, s, { "0.8.0" })
        c.onLaunch(); c.check(); gate.complete(UpdateFeed(release(), release().version)); yield()
        assertEquals(1, s.requests); assertTrue(c.dialog); assertFalse(c.checking); scope.cancel()
    }
    @Test fun startupFailureIsQuietAndCanRetryManually() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined); val s = Source(release()).apply { fetchBlock = { throw IOException("请求受限") } }
        val c = controller(scope, s); c.onLaunch(); assertFalse(c.dialog); assertNotNull(c.error); assertFalse(c.checking)
        s.fetchBlock = null; c.check(); assertNull(c.error); assertTrue(c.dialog); scope.cancel()
    }
    @Test fun missingNewerApkReportsUnavailableInsteadOfUpToDate() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined); val s = Source(null).apply { fetchBlock = { UpdateFeed(null, release().version) } }
        val c = controller(scope, s); c.onLaunch(); assertNotNull(c.error); assertNull(c.message); scope.cancel()
    }
    @Test fun downloadAndInstallerAreSeparateExplicitActionsAndReverifyBeforeInstall() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined); val s = Source(release()); val c = controller(scope, s)
        var installs = 0; c.onLaunch(); assertEquals(0, s.downloads); c.download()
        assertNotNull(c.ready); assertEquals(1, s.downloads); assertEquals(0, installs)
        c.install { installs++ }; assertEquals(1, s.checks); assertEquals(1, installs); scope.cancel()
    }
    @Test fun cancellationRejectsLateFileAndProgressEvenFromAnUncancellableSource() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined); val gate = CompletableDeferred<File>(); var progress: ((Long, Long) -> Unit)? = null
        val s = Source(release()).apply { downloadBlock = { p -> progress = p; withContext(NonCancellable) { gate.await() } } }
        val c = controller(scope, s); c.onLaunch(); c.download(); c.cancelDownload(); progress?.invoke(4, 4)
        gate.complete(File("obsolete.apk")); yield(); assertNull(c.ready); assertFalse(c.downloading); assertEquals(0L, c.downloaded); scope.cancel()
    }
    @Test fun invalidatedPackageIsDeletedWithoutOpeningInstaller() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val file = File.createTempFile("invalid-update", ".apk")
        try {
            val s = Source(release()).apply { downloadBlock = { file }; verifyFailure = true }
            val c = controller(scope, s); c.onLaunch(); c.download(); var installs = 0
            c.install { installs++ }
            assertEquals(0, installs); assertNull(c.ready); assertFalse(file.exists())
            assertNotNull(c.error); assertFalse(c.validating)
        } finally { file.delete(); scope.cancel() }
    }
    @Test fun unavailableInstallerKeepsVerifiedPackageForRetry() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val s = Source(release()); val c = controller(scope, s); c.onLaunch(); c.download()
        c.install { throw IllegalStateException("No installer") }
        assertNotNull(c.ready); assertNotNull(c.error); assertFalse(c.validating)
        var installs = 0; c.install { installs++ }
        assertEquals(1, installs); assertEquals(2, s.checks); assertNull(c.error); scope.cancel()
    }
    @Test fun closedControllerCannotReopenPromptAfterLateCheck() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined); val gate = CompletableDeferred<UpdateFeed>()
        val s = Source(release()).apply { fetchBlock = { withContext(NonCancellable) { gate.await() } } }
        val c = controller(scope, s); c.onLaunch(); c.close(); gate.complete(UpdateFeed(release(), release().version)); yield()
        assertNull(c.release); assertFalse(c.dialog); scope.cancel()
    }
}
