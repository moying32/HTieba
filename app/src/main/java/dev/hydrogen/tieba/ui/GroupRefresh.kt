package dev.hydrogen.tieba.ui

import dev.hydrogen.tieba.data.ChatHistory
import dev.hydrogen.tieba.data.ChatRoom
import kotlinx.coroutines.CancellationException

internal data class GroupRefresh(val room: ChatRoom, val history: ChatHistory, val detailError: String?)

internal suspend fun loadGroupRefresh(room: ChatRoom, older: Boolean,
    fetchRoom: suspend () -> ChatRoom, fetchMessages: suspend () -> ChatHistory): GroupRefresh {
    var detailError: String? = null
    val detail = if (older) room else try { fetchRoom() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) {
        detailError = "群资料更新失败，当前显示上次资料：${error.message ?: "请重试"}"
        room
    }
    // A failed metadata request must not prevent independent history reads.
    return GroupRefresh(detail, fetchMessages(), detailError)
}
