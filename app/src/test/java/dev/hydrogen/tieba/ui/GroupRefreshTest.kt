package dev.hydrogen.tieba.ui

import dev.hydrogen.tieba.data.*
import java.io.IOException
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class GroupRefreshTest {
    private val room = ChatRoom("12", "旧群名", forumId = "34")
    private val page = ChatHistory(listOf(ChatMessage("4", "5", "消息", 10, 81)), false)

    @Test fun metadataFailureStillLoadsIndependentHistoryAndPreservesPreviousRoom() = runBlocking {
        val result = loadGroupRefresh(room, false, { throw IOException("资料接口异常") }, { page })
        assertEquals(room, result.room)
        assertEquals(page, result.history)
        assertTrue(result.detailError!!.contains("资料接口异常"))
    }

    @Test fun olderPagesDoNotFetchMetadataAndSuccessfulRefreshClearsWarning() = runBlocking {
        assertNull(loadGroupRefresh(room, true, { error("不应请求") }, { page }).detailError)
        val fresh = room.copy(name = "新群名", detailsLoaded = true)
        val result = loadGroupRefresh(room, false, { fresh }, { page })
        assertEquals(fresh, result.room); assertNull(result.detailError)
    }

    @Test fun cancellationDoesNotStartAnotherRequestAndHistoryFailureIsNotSuccess() = runBlocking {
        var messagesRequested = false
        try {
            loadGroupRefresh(room, false, { throw CancellationException() }, { messagesRequested = true; page })
            fail("cancellation must propagate")
        } catch (_: CancellationException) { }
        assertFalse(messagesRequested)
        try {
            loadGroupRefresh(room, false, { room }, { throw IOException("历史读取失败") })
            fail("history failure must propagate")
        } catch (error: IOException) { assertEquals("历史读取失败", error.message) }
    }
}
