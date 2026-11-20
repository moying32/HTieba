package dev.hydrogen.tieba.ui

import dev.hydrogen.tieba.data.*
import java.io.IOException
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class GroupModerationControllerTest {
    private val room = ChatRoom("12","群",forumId = "34")
    private fun page(name: String = "一", p: Int = 1, more: Boolean = false) = GroupBlockPage(listOf(GroupBlockedUser(name,name,"")),p,if(more) 61 else 1,more)
    private fun profile(id: String) = UserProfile(Person(id,"账号$id"),portrait = "portrait$id")

    @Test fun closingPanelRejectsEvenNonCancellableListResponse() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val response = CompletableDeferred<GroupBlockPage>()
        val c = GroupModerationController(scope,{ _,_ -> withContext(NonCancellable) { response.await() } },::profile)
        c.bind("1"); c.open(room); assertTrue(c.loading)
        c.close(); response.complete(page()); yield()
        assertNull(c.room); assertFalse(c.loading); assertTrue(c.listing.users.isEmpty()); assertNull(c.listError)
        scope.cancel()
    }
    @Test fun reopeningSameRoomCannotAcceptAnOldSnapshot() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val response = CompletableDeferred<GroupBlockPage>(); var calls = 0
        val c = GroupModerationController(scope,{ _,_ -> if(++calls == 1) withContext(NonCancellable) { response.await() } else page("新") },::profile)
        c.bind("1"); c.open(room); c.open(room)
        response.complete(page("旧")); yield()
        assertEquals("新",c.listing.users.single().name); assertFalse(c.loading)
        c.close(); scope.cancel()
    }
    @Test fun postWriteRefreshRejectsAnOverlappingPreWriteSnapshot() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val response = CompletableDeferred<GroupBlockPage>(); var calls = 0
        val c = GroupModerationController(scope,{ _,_ -> if(++calls == 1) withContext(NonCancellable) { response.await() } else GroupBlockPage(emptyList(),1,0,false) },::profile)
        c.bind("1"); c.open(room); c.refreshAfterWrite()
        response.complete(page("旧禁言账号")); yield()
        assertTrue(c.listing.users.isEmpty()); assertEquals(0L,c.listing.total); assertFalse(c.loading)
        c.close(); scope.cancel()
    }
    @Test fun accountSwitchInvalidatesPreparedTarget() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val response = CompletableDeferred<UserProfile>()
        val c = GroupModerationController(scope,{ _,_ -> page() },{ withContext(NonCancellable) { response.await() } })
        c.bind("1"); c.open(room); c.changeUid("56"); c.prepare(); assertTrue(c.targetLoading)
        c.bind("2"); response.complete(profile("56")); yield()
        assertNull(c.target); assertNull(c.room); assertEquals("",c.uid); assertFalse(c.targetLoading)
        c.close(); scope.cancel()
    }
    @Test fun editingUidNeverLeavesThePreviousAccountReady() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val response = CompletableDeferred<UserProfile>()
        val c = GroupModerationController(scope,{ _,_ -> page() },{ id -> if(id == "56") withContext(NonCancellable) { response.await() } else profile(id) })
        c.bind("1"); c.open(room); c.changeUid("56"); c.prepare()
        c.changeUid("57"); c.prepare(); response.complete(profile("56")); yield()
        assertEquals("57",c.target?.person?.id); assertFalse(c.targetLoading); assertNull(c.targetError)
        c.close(); scope.cancel()
    }
    @Test fun pagingFailureRetainsRowsAndRetriesTheFailedPage() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val pages = mutableListOf<Int>(); var failed = false
        val c = GroupModerationController(scope,{ _,p ->
            pages += p
            if(p == 2 && !failed) { failed = true; throw IOException("断网") }
            page(if(p == 1) "一" else "二",p,p == 1)
        },::profile)
        c.bind("1"); c.open(room); c.refresh(true)
        assertEquals(listOf("一"),c.listing.users.map { it.name }); assertEquals(1,c.listing.page); assertEquals("断网",c.listError)
        c.retry()
        assertEquals(listOf(1,2,2),pages); assertEquals(listOf("一","二"),c.listing.users.map { it.name }); assertFalse(c.listing.hasMore)
        c.close(); scope.cancel()
    }
    @Test fun blockedPortraitMismatchAndSelfTargetCannotBecomeReady() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined); var calls = 0
        val c = GroupModerationController(scope,{ _,_ -> page() },{ id -> calls++; profile(id) })
        c.bind("1"); c.open(room); c.chooseBlocked(GroupBlockedUser("同名","anotherPortrait",""),emptyList())
        c.changeUid("56"); c.prepare()
        assertNull(c.target); assertTrue(c.targetError!!.contains("不一致"))
        c.clearExpected(); c.changeUid("1"); c.prepare()
        assertNull(c.target); assertTrue(c.targetError!!.contains("当前登录账号")); assertEquals(1,calls)
        c.close(); scope.cancel()
    }
}
