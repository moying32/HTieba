package dev.hydrogen.tieba.ui

import dev.hydrogen.tieba.data.ChatHistory
import dev.hydrogen.tieba.data.ChatMessage
import java.io.IOException

/** Oldest-history boundary, independent of the moving latest-message window. */
internal data class GroupHistoryState(
    val initialized: Boolean = false,
    val before: Long? = null,
    val hasMore: Boolean = false,
) {
    fun accept(batch: ChatHistory, older: Boolean): GroupHistoryState {
        if (!older && initialized && before != null) return this
        val oldest = batch.messages.mapNotNull { it.id.toLongOrNull()?.takeIf { id -> id > 0 } }.minOrNull()
        if (batch.hasMore && (oldest == null || (older && before != null && oldest >= before))) {
            throw IOException("贴吧未返回可继续读取的历史游标，请重试")
        }
        return GroupHistoryState(true, listOfNotNull(before, oldest).minOrNull(), batch.hasMore)
    }

    companion object {
        // Disk cache is truncated to 400 latest messages. Never restore an old
        // exhausted marker across process death: older server history may remain.
        fun cached(messages: List<ChatMessage>): GroupHistoryState {
            val oldest = messages.mapNotNull { it.id.toLongOrNull()?.takeIf { id -> id > 0 } }.minOrNull()
            return GroupHistoryState(oldest != null, oldest, oldest != null)
        }
    }
}

/** Invalidates completions even when an underlying request ignores cancellation. */
internal class ChatReadFence {
    private var generation = 0L
    fun next(): Long = ++generation
    fun accepts(ticket: Long): Boolean = ticket == generation
    fun invalidate() { generation++ }
}

internal data class ChatViewport(val messageKey: String, val offset: Int)
