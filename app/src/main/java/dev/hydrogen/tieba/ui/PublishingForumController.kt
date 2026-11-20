package dev.hydrogen.tieba.ui

import androidx.compose.runtime.*
import dev.hydrogen.tieba.data.ForumItem
import dev.hydrogen.tieba.data.SearchPage
import java.io.IOException
import kotlinx.coroutines.*

/** Publication-only reads, independent of the main search page and its history. */
internal class PublishingForumController(
    private val scope: CoroutineScope,
    private val fetchSearch: suspend (String, Int) -> SearchPage,
    private val fetchFollowed: suspend (String) -> List<ForumItem>,
    private val debounceMillis: Long = 300,
) {
    private var active = false
    private var owner = ""
    private var searchJob: Job? = null
    private var followedJob: Job? = null
    private val searchFence = ChatReadFence()
    private val followedFence = ChatReadFence()
    var query by mutableStateOf(""); private set
    var results by mutableStateOf<List<ForumItem>>(emptyList()); private set
    var page by mutableIntStateOf(0); private set
    var hasMore by mutableStateOf(false); private set
    var searching by mutableStateOf(false); private set
    var searchError by mutableStateOf<String?>(null); private set
    var followed by mutableStateOf<List<ForumItem>>(emptyList()); private set
    var followingLoading by mutableStateOf(false); private set
    var followingLoaded by mutableStateOf(false); private set
    var followingError by mutableStateOf<String?>(null); private set

    fun open(uid: String) {
        close(); active = true; owner = uid
        if (uid.isNotBlank()) refreshFollowed()
    }

    fun close() {
        active = false; owner = ""
        dismissCandidates()
        followedFence.invalidate(); followedJob?.cancel()
        followed = emptyList(); followingLoading = false; followingLoaded = false; followingError = null
    }

    fun dismissCandidates() {
        searchFence.invalidate(); searchJob?.cancel()
        query = ""; results = emptyList(); page = 0; hasMore = false; searching = false; searchError = null
    }

    fun edit(value: String) {
        dismissCandidates()
        query = value.trim()
        if (active && query.isNotBlank()) loadSearch(debounce = true)
    }

    fun retrySearch() = loadSearch()
    fun more() { if (hasMore) loadSearch(append = true) }

    private fun loadSearch(append: Boolean = false, debounce: Boolean = false) {
        if (!active || query.isBlank() || searching || (append && !hasMore)) return
        val requested = query; val requestedPage = if (append) page + 1 else 1
        val ticket = searchFence.next()
        searching = true; searchError = null
        searchJob = scope.launch {
            try {
                if (debounce) delay(debounceMillis)
                val batch = fetchSearch(requested, requestedPage)
                if (active && searchFence.accepts(ticket)) {
                    if (batch.page != requestedPage) throw IOException("候选吧页码已变化，请重新搜索")
                    val combined = (if (append) results + batch.forums else batch.forums)
                        .filter { it.name.isNotBlank() }.distinctBy { it.id.ifBlank { it.name } }
                    if (append && batch.hasMore && combined.size == results.size) throw IOException("候选列表没有新增结果，请重新搜索")
                    results = combined; page = requestedPage; hasMore = batch.hasMore
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (active && searchFence.accepts(ticket)) searchError = e.message ?: "候选吧读取失败" }
            finally { if (searchFence.accepts(ticket)) searching = false }
        }
    }

    fun refreshFollowed() {
        if (!active || owner.isBlank() || followingLoading) return
        val uid = owner; val ticket = followedFence.next()
        followingLoading = true; followingError = null
        followedJob = scope.launch {
            try {
                val batch = fetchFollowed(uid)
                if (active && followedFence.accepts(ticket)) {
                    followed = batch.filter { it.name.isNotBlank() }.distinctBy { it.id.ifBlank { it.name } }
                    followingLoaded = true
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (active && followedFence.accepts(ticket)) followingError = (e.message ?: "关注吧读取失败")
                    .replace("，尚未开始签到", "").replace("尚未开始签到，", "")
            } finally { if (followedFence.accepts(ticket)) followingLoading = false }
        }
    }
}
