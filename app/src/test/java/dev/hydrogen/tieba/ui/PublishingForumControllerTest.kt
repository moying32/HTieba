package dev.hydrogen.tieba.ui

import dev.hydrogen.tieba.data.*
import java.io.IOException
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PublishingForumControllerTest {
    private fun batch(name: String, p: Int = 1, more: Boolean = false) = SearchPage(forums = listOf(ForumItem(name, name)), page = p, hasMore = more)

    @Test fun changedQueryRejectsAnOlderUncancellableResponse() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val response = CompletableDeferred<SearchPage>()
        val c = PublishingForumController(scope, { q, _ -> if (q == "旧") withContext(NonCancellable) { response.await() } else batch(q) }, { emptyList() }, 0)
        c.open("1"); c.edit("旧"); c.edit("新")
        response.complete(batch("旧")); yield()
        assertEquals("新", c.query); assertEquals("新", c.results.single().name); assertFalse(c.searching)
        c.close(); scope.cancel()
    }

    @Test fun choosingAForumInvalidatesAnOutstandingSearch() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val response = CompletableDeferred<SearchPage>()
        val c = PublishingForumController(scope, { _, _ -> withContext(NonCancellable) { response.await() } }, { emptyList() }, 0)
        c.open("1"); c.edit("待选"); c.dismissCandidates()
        response.complete(batch("不应重现")); yield()
        assertEquals("", c.query); assertTrue(c.results.isEmpty()); assertFalse(c.searching)
        c.close(); scope.cancel()
    }

    @Test fun accountChangeCannotExposeThePreviousFollowedList() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val response = CompletableDeferred<List<ForumItem>>()
        val c = PublishingForumController(scope, { q, p -> batch(q, p) }, { uid ->
            if (uid == "1") withContext(NonCancellable) { response.await() } else listOf(ForumItem("2", "新账号关注"))
        }, 0)
        c.open("1"); c.open("2"); response.complete(listOf(ForumItem("1", "旧账号关注"))); yield()
        assertEquals("新账号关注", c.followed.single().name); assertFalse(c.followingLoading)
        c.close(); scope.cancel()
    }

    @Test fun closeInvalidatesBothReadChannels() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val search = CompletableDeferred<SearchPage>(); val followed = CompletableDeferred<List<ForumItem>>()
        val c = PublishingForumController(scope, { _, _ -> withContext(NonCancellable) { search.await() } }, { withContext(NonCancellable) { followed.await() } }, 0)
        c.open("1"); c.edit("吧"); c.close()
        search.complete(batch("旧")); followed.complete(listOf(ForumItem("旧", "旧"))); yield()
        assertTrue(c.followed.isEmpty()); assertTrue(c.results.isEmpty()); assertFalse(c.followingLoaded); assertFalse(c.searching)
        scope.cancel()
    }

    @Test fun followedListRetainsAllEntriesBeyondEightAndDeduplicates() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val forums = (1..127).map { ForumItem("$it", "关注$it") }
        val c = PublishingForumController(scope, { q, p -> batch(q, p) }, { forums + forums.take(7) }, 0)
        c.open("1"); assertEquals(127, c.followed.size); assertEquals("关注127", c.followed.last().name); assertTrue(c.followingLoaded)
        c.close(); scope.cancel()
    }

    @Test fun failedFollowedRefreshKeepsThePreviousListAndCanRetry() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined); var calls = 0
        val c = PublishingForumController(scope, { q, p -> batch(q, p) }, {
            if (++calls == 2) throw IOException("网络失败")
            listOf(ForumItem("1", if (calls == 1) "之前" else "之后"))
        }, 0)
        c.open("1"); c.refreshFollowed()
        assertEquals("之前", c.followed.single().name); assertEquals("网络失败", c.followingError)
        c.refreshFollowed(); assertEquals("之后", c.followed.single().name); assertNull(c.followingError)
        c.close(); scope.cancel()
    }

    @Test fun searchPaginationKeepsResultsAndRetriesTheFailedPage() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val requested = mutableListOf<Int>(); var failed = false
        val c = PublishingForumController(scope, { _, p ->
            requested += p
            if (p == 2 && !failed) { failed = true; throw IOException("读取失败") }
            batch("候选$p", p, p == 1)
        }, { emptyList() }, 0)
        c.open("1"); c.edit("候选"); c.more()
        assertEquals(1, c.page); assertEquals("候选1", c.results.single().name); assertNotNull(c.searchError)
        c.more(); assertEquals(listOf(1, 2, 2), requested); assertEquals(2, c.results.size); assertFalse(c.hasMore)
        c.close(); scope.cancel()
    }

    @Test fun nonAdvancingSearchPageDoesNotLoopOrLosePreviousRows() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val c = PublishingForumController(scope, { _, p -> batch("重复", p, true) }, { emptyList() }, 0)
        c.open("1"); c.edit("重复"); c.more()
        assertEquals(1, c.page); assertEquals(1, c.results.size); assertNotNull(c.searchError)
        c.close(); scope.cancel()
    }

    @Test fun guestCanSearchWithoutLoadingAnyAccountFollowedList() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined); var reads = 0
        val c = PublishingForumController(scope, { q, p -> batch(q, p) }, { reads++; emptyList() }, 0)
        c.open(""); c.edit(" android ")
        assertEquals(0, reads); assertEquals("android", c.query); assertEquals("android", c.results.single().name)
        c.edit("  "); assertTrue(c.results.isEmpty()); assertFalse(c.searching)
        c.close(); scope.cancel()
    }
}
