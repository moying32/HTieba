package dev.hydrogen.tieba.ui

import dev.hydrogen.tieba.data.ChatHistory
import dev.hydrogen.tieba.data.ChatMessage
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ChatReadStateTest {
    private fun messages(ids: Iterable<Number>) = ids.map { val id = it.toLong(); ChatMessage("$id", "42", "消息 $id", id, 1) }
    private fun page(ids: Iterable<Number>, more: Boolean) = ChatHistory(messages(ids), more)

    @Test fun readingOlderMessagesCannotMarkAnUnseenLatestMessageRead() {
        val messages = messages(1..100)
        assertEquals(12L, visibleChatSeen(messages, setOf("42:11", "42:12"), 10L))
        assertEquals(12L, visibleChatSeen(messages, setOf("42:1", "42:2"), 12L))
        assertTrue(messages.last().time > visibleChatSeen(messages, setOf("42:12"), 12L))
    }

    @Test fun unknownOrWrongSenderVisibleKeysCannotAdvanceReadMarker() {
        assertEquals(10L, visibleChatSeen(messages(1..100), setOf("43:100", "42:unknown"), 10L))
        assertEquals(10L, visibleChatSeen(messages(1..100), emptySet(), 10L))
        assertEquals(100L, visibleChatSeen(messages(1..100), setOf("42:100"), 10L))
    }

    @Test fun exhaustedHistoryStaysExhaustedAfterManyLatestRefreshes() {
        var history = GroupHistoryState().accept(page(120..179, true), false)
        history = history.accept(page(60..120, true), true)
        history = history.accept(page(1..60, false), true)
        repeat(4) { history = history.accept(page((180L + it)..(239L + it), true), false) }
        assertEquals(1L, history.before)
        assertFalse(history.hasMore)
    }

    @Test fun shortLatestWindowDoesNotCloseExistingOlderHistory() {
        val history = GroupHistoryState().accept(page(120..179, true), false)
            .accept(page(177..179, false), false)
        assertEquals(120L, history.before)
        assertTrue(history.hasMore)
    }

    @Test fun inclusiveBoundaryAndAlreadyCachedMessagesStillAdvanceCursor() {
        val cached = messages(60..179)
        val history = GroupHistoryState().accept(page(120..179, true), false)
            .accept(ChatHistory(cached.take(61), true), true)
        assertEquals(60L, history.before)
        assertTrue(history.hasMore)
        assertEquals(cached, ChatStorage.merge(cached, cached.take(61)))
    }

    @Test fun repeatedFullPageIsRetryableFailureRatherThanFalseEndOrEndlessLoop() {
        val history = GroupHistoryState().accept(page(120..179, true), false)
        assertTrue(runCatching { history.accept(page(120..179, true), true) }.exceptionOrNull() is IOException)
        assertEquals(120L, history.before)
        assertTrue(history.hasMore)
        assertEquals(60L, history.accept(page(60..120, true), true).before)
    }

    @Test fun emptyOrBoundaryOnlyShortPageMarksEndWithoutMovingCursorForward() {
        val history = GroupHistoryState().accept(page(120..179, true), false)
        listOf(ChatHistory(emptyList(), false), page(120..120, false)).forEach {
            val exhausted = history.accept(it, true)
            assertFalse(exhausted.hasMore)
            assertEquals(120L, exhausted.before)
        }
    }

    @Test fun initialEmptyWindowDoesNotBlockHistoryWhenMessagesLaterArrive() {
        val empty = GroupHistoryState().accept(ChatHistory(emptyList(), false), false)
        assertTrue(empty.initialized)
        val populated = empty.accept(page(1..60, true), false)
        assertTrue(populated.hasMore)
        assertEquals(1L, populated.before)
        // New messages are still merged independently of the history boundary.
        assertEquals(60, ChatStorage.merge(emptyList(), messages(1..60)).size)
    }

    @Test fun cacheTruncationNeverPersistsAnExhaustedCursorBeyondAvailableMessages() {
        val c = Conversation("42", dev.hydrogen.tieba.data.Person("42"),
            room = dev.hydrogen.tieba.data.ChatRoom("42", "群"), messages = messages(1..600))
        val json = org.json.JSONObject().put("conversations", ChatStorage.encode(listOf(c))).toString()
        val restored = ChatStorage.decode(json).single()
        assertEquals(400, restored.messages.size)
        val history = GroupHistoryState.cached(restored.messages)
        assertEquals(201L, history.before)
        assertTrue(history.hasMore)
        assertEquals(history, history.accept(page(550..600, false), false))
    }

    @Test fun localAcknowledgementsCannotBecomeServerPaginationCursors() {
        val c = ChatMessage("ack:local", "42", "发送确认", 10, 1)
        assertEquals(GroupHistoryState(), GroupHistoryState.cached(listOf(c)))
        assertTrue(runCatching { GroupHistoryState().accept(ChatHistory(listOf(c), true), false) }.isFailure)
    }

    @Test fun twoRoomsKeepIndependentHistoryBoundaries() {
        val rooms = mutableMapOf("group:1" to GroupHistoryState().accept(page(1..4, false), false),
            "group:2" to GroupHistoryState().accept(page(100..159, true), false))
        rooms["group:2"] = rooms.getValue("group:2").accept(page(40..100, true), true)
        assertFalse(rooms.getValue("group:1").hasMore)
        assertEquals(1L, rooms.getValue("group:1").before)
        assertEquals(40L, rooms.getValue("group:2").before)
    }

    @Test fun cancelReturnOrAccountChangeRejectsEvenNonCancellableOldCompletions() {
        val fence = ChatReadFence()
        val first = fence.next()
        assertTrue(fence.accepts(first))
        fence.invalidate()
        val second = fence.next()
        var current = "第二个会话"
        if(fence.accepts(first)) current = "旧请求的结果"
        assertEquals("第二个会话", current)
        assertTrue(fence.accepts(second))
        fence.invalidate()
        assertFalse(fence.accepts(second))
    }

    @Test fun oldFinallyCannotClearNewRequestLoadingState() {
        val fence = ChatReadFence()
        val old = fence.next()
        val fresh = fence.next()
        var loading = true
        if(fence.accepts(old)) loading = false
        assertTrue(loading)
        if(fence.accepts(fresh)) loading = false
        assertFalse(loading)
    }
}
