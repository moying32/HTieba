package dev.hydrogen.tieba.data

import kotlinx.coroutines.*
import okhttp3.FormBody
import okio.Buffer
import okio.ForwardingSink
import okio.buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class SubmissionSafetyTest {
    @Test fun submissionWaitsForDurableMarker() = runBlocking {
        val attempt = SubmissionAttempt()
        val events = mutableListOf<String>()
        attempt.beforeDispatch {
            events += "persist"
            assertFalse(attempt.dispatched)
            yield()
            true
        }
        events += "network"
        assertTrue(attempt.dispatched)
        assertEquals(listOf("persist", "network"), events)
    }

    @Test fun failedDurabilityPreventsDispatch() = runBlocking {
        val attempt = SubmissionAttempt()
        try { attempt.beforeDispatch { false }; fail("must not dispatch") } catch (_: IOException) { }
        assertFalse(attempt.dispatched)
        assertFalse(attempt.uncertain(IOException()))
    }

    @Test fun persistenceExceptionPreventsDispatch() = runBlocking {
        val attempt = SubmissionAttempt()
        try { attempt.beforeDispatch { throw IOException("storage") }; fail() } catch (_: IOException) { }
        assertFalse(attempt.dispatched)
    }

    @Test fun cancellationDuringPersistenceNeverStartsNetwork() = runBlocking {
        val attempt = SubmissionAttempt()
        val worker = launch(start = CoroutineStart.UNDISPATCHED) {
            attempt.beforeDispatch { currentCoroutineContext().cancel(); true }
            fail("network must not run after cancellation")
        }
        worker.join()
        assertTrue(worker.isCancelled)
        assertFalse(attempt.dispatched)
    }

    @Test fun transportFailureAndCancellationAfterDispatchRemainUncertain() = runBlocking {
        val attempt = SubmissionAttempt()
        attempt.beforeDispatch { true }
        assertTrue(attempt.uncertain(IOException("timeout")))
        assertTrue(attempt.uncertain(CancellationException()))
        assertFalse(attempt.uncertain(TiebaRejectedException("5", "拒绝")))
        assertFalse(attempt.uncertain(CommentPreparationException(IOException())))
        assertFalse(attempt.uncertain(LoginExpiredException()))
        assertFalse(attempt.uncertain(IllegalArgumentException()))
    }

    @Test fun allPreflightFailuresAreSafeToRetry() {
        val attempt = SubmissionAttempt()
        listOf(IOException(), CancellationException(), IllegalArgumentException()).forEach { assertFalse(attempt.uncertain(it)) }
    }

    @Test fun acknowledgedWriteStillNeedsReviewIfReadbackExpires() = runBlocking {
        val attempt = SubmissionAttempt()
        attempt.beforeDispatch { true }; attempt.serverAcknowledged()
        assertTrue(attempt.uncertain(LoginExpiredException()))
        assertTrue(attempt.uncertain(TiebaRejectedException("9", "回读失败")))
    }

    @Test fun attemptCannotDispatchTwice() = runBlocking {
        val attempt = SubmissionAttempt()
        attempt.beforeDispatch { true }
        var secondPersistence = false
        try { attempt.beforeDispatch { secondPersistence = true; true }; fail() } catch (_: IllegalStateException) { }
        assertFalse(secondPersistence)
    }

    @Test fun queueFailureKeepsFirstUploadAndRetryResumesAtMissingImage() = runBlocking {
        val source = (1..3).map { DraftImage("content://photos/$it") }
        val first = PublishingUploadQueue(source)
        val checkpoints = mutableListOf<List<DraftImage>>()
        try {
            first.run(upload = { _, i -> if (i == 1) throw IOException("offline") else "code-$i" },
                persist = { checkpoints += it; true }, changed = { _, _ -> })
            fail()
        } catch (_: IOException) { }
        assertEquals(1, first.activeIndex)
        assertEquals("code-0", first.images[0].code)
        assertEquals(1, checkpoints.size)
        val calls = mutableListOf<Int>()
        val result = PublishingUploadQueue(first.images).run(upload = { _, i -> calls += i; "code-$i" }, persist = { true }, changed = { _, _ -> })
        assertEquals(listOf(1, 2), calls)
        assertEquals(source.map { it.uri }, result.map { it.uri })
        assertTrue(result.all { it.stage == UploadStage.Uploaded })
    }

    @Test fun queueCancellationCheckpointsKnownImageAndDoesNotStartAnother() = runBlocking {
        val queue = PublishingUploadQueue((1..3).map { DraftImage("content://photos/$it") })
        var uploads = 0
        var checkpointed = false
        val worker = launch(start = CoroutineStart.UNDISPATCHED) {
            queue.run(upload = { _, _ -> uploads++; currentCoroutineContext().cancel(); "confirmed-code" },
                persist = { checkpointed = true; true }, changed = { _, _ -> })
            fail("cancelled queue must not complete")
        }
        worker.join()
        assertTrue(worker.isCancelled)
        assertTrue(checkpointed)
        assertEquals(1, uploads)
        assertEquals("confirmed-code", queue.images[0].code)
        assertEquals("", queue.images[1].code)
    }

    @Test fun queueStorageFailureStopsBeforeNextImageAndRetainsKnownCode() = runBlocking {
        val queue = PublishingUploadQueue(listOf(DraftImage("content://1"), DraftImage("content://2")))
        var uploads = 0
        try { queue.run(upload = { _, _ -> uploads++; "known" }, persist = { false }, changed = { _, _ -> }); fail() } catch (_: IOException) { }
        assertEquals(1, uploads)
        assertEquals("known", queue.images[0].code)
    }

    @Test fun fullyUploadedQueueDoesNotCallNetworkOrDuplicateCheckpoints() = runBlocking {
        val source = listOf(DraftImage("content://1", "known"))
        assertEquals(source, PublishingUploadQueue(source).run(upload = { _, _ -> error("duplicate upload") },
            persist = { error("unnecessary checkpoint") }, changed = { _, _ -> fail() }))
    }

    @Test fun missingCollectionEntryIsNotProofOfRemovalOnPartialPage() {
        assertFalse(cloudWriteConfirmed(remove = true, found = false, complete = false))
        assertTrue(cloudWriteConfirmed(remove = true, found = false, complete = true))
        assertFalse(cloudWriteConfirmed(remove = true, found = true, complete = true))
        assertTrue(cloudWriteConfirmed(remove = false, found = true, complete = false))
        assertFalse(cloudWriteConfirmed(remove = false, found = false, complete = true))
    }

    @Test fun cloudJournalRestoresOnlyAReviewRecordNeverAnActiveWrite() {
        val original = CloudWriteState("user-a", "123", "测试", true, busy = true)
        val restored = CloudWriteState.restore("user-a", original.encode())!!
        assertEquals("123", restored.threadId)
        assertEquals("user-a", restored.owner)
        assertTrue(restored.remove)
        assertTrue(restored.uncertain)
        assertFalse(restored.busy)
        assertFalse(restored.confirmed)
        assertFalse(original.encode().contains("user-a"))
    }

    @Test fun invalidCloudJournalCannotOfferAnInvalidMutation() {
        listOf("", "{", "{}", "{\"id\":\"0\"}", "{\"id\":\"url\"}").forEach { assertNull(CloudWriteState.restore("a", it)) }
    }

    @Test fun draftRestartRetainsOrderAndUploadedCodesButResetsTransientWork() {
        val original = PublishingDraft("owner", "测试", "标题", "正文", listOf(
            DraftImage("content://pictures/2", "#(pic,id,20,30)\n"),
            DraftImage("content://pictures/1", stage = UploadStage.Uploading, percent = 45),
        ), sending = true, progress = "上传中", uncertain = true)
        val restored = PublishingDraft.decode("owner", original.encode(), "default")
        assertEquals(listOf("content://pictures/2", "content://pictures/1"), restored.images.map { it.uri })
        assertEquals(original.images[0].code, restored.images[0].code)
        assertEquals(UploadStage.Uploaded, restored.images[0].stage)
        assertEquals(UploadStage.Queued, restored.images[1].stage)
        assertEquals(0, restored.images[1].percent)
        assertTrue(restored.uncertain)
        assertFalse(restored.sending)
        assertEquals("", restored.progress)
    }

    @Test fun restoredDraftCapsSelectionAndRejectsNonDocumentUris() {
        val selection = (1..12).map { DraftImage("content://photos/$it") } + DraftImage("file:///private")
        val draft = PublishingDraft("a", images = listOf(DraftImage("https://remote")) + selection + selection)
        val restored = PublishingDraft.decode("a", draft.encode(), "")
        assertEquals(9, restored.images.size)
        assertEquals(9, restored.images.distinctBy { it.uri }.size)
        assertTrue(restored.images.all { it.uri.startsWith("content://") })
        assertEquals(PublishingDraft("a", "默认"), PublishingDraft.decode("a", "broken", "默认"))
    }

    @Test fun uploadProgressIsMonotonicAndBodyIsUnchanged() {
        val form = FormBody.Builder().add("pic", "中+&".repeat(6000)).build()
        val expected = Buffer().also { form.writeTo(it) }.readByteArray()
        val progress = mutableListOf<Int>()
        val wrapped = UploadProgressBody(form) { progress += it }
        val actual = Buffer().also { wrapped.writeTo(it) }.readByteArray()
        assertArrayEquals(expected, actual)
        assertEquals(form.contentLength(), wrapped.contentLength())
        assertEquals(form.contentType(), wrapped.contentType())
        assertEquals(0, progress.first())
        assertEquals(100, progress.last())
        assertTrue(progress.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test fun uploadSinkFailureIsNotReportedAsCompletion() {
        val progress = mutableListOf<Int>()
        val body = UploadProgressBody(FormBody.Builder().add("pic", "x".repeat(20000)).build()) { progress += it }
        val sink = object : ForwardingSink(Buffer()) {
            override fun write(source: Buffer, byteCount: Long) { throw IOException("disconnected") }
        }.buffer()
        try { body.writeTo(sink); fail() } catch (_: IOException) { }
        assertFalse(progress.contains(100))
    }
}
