package dev.hydrogen.tieba.ui

import androidx.compose.runtime.*
import dev.hydrogen.tieba.data.*
import java.io.IOException
import kotlinx.coroutines.*

internal data class GroupBlockListing(val users: List<GroupBlockedUser> = emptyList(), val page: Int = 0,
    val total: Long? = null, val hasMore: Boolean = false) {
    fun accept(batch: GroupBlockPage, append: Boolean): GroupBlockListing {
        if(batch.page != if(append) page + 1 else 1) throw IOException("禁言名单页码已变化，请重新刷新")
        // A moving server list can overlap. Refuse a non-advancing page rather than loop forever.
        if(append && batch.hasMore && batch.users.all { user -> users.any { it.portrait == user.portrait } })
            throw IOException("禁言名单发生变化，请从第一页刷新")
        return GroupBlockListing((if(append) users + batch.users else batch.users).distinctBy { it.portrait },batch.page,batch.total,batch.hasMore)
    }
}

/** Read-only preparation; actual writes share ChatController's account fence and write lock. */
internal class GroupModerationController(private val scope: CoroutineScope,
    private val fetchBlocks: suspend (String,Int) -> GroupBlockPage,
    private val fetchProfile: suspend (String) -> UserProfile) {
    private var owner = ""
    var room by mutableStateOf<ChatRoom?>(null); private set
    var listing by mutableStateOf(GroupBlockListing()); private set
    var loading by mutableStateOf(false); private set
    var listError by mutableStateOf<String?>(null); private set
    var syncedAt by mutableLongStateOf(0); private set
    var uid by mutableStateOf(""); private set
    var expectedPortrait by mutableStateOf(""); private set
    var expectedName by mutableStateOf(""); private set
    var target by mutableStateOf<GroupModerationTarget?>(null); private set
    var targetLoading by mutableStateOf(false); private set
    var targetError by mutableStateOf<String?>(null); private set
    private var listJob: Job? = null
    private var targetJob: Job? = null
    private val listFence = ChatReadFence()
    private val targetFence = ChatReadFence()
    private var retryAppend = false

    fun bind(value: String) { if(owner != value) { close(); owner = value } }
    fun open(value: ChatRoom, person: Person? = null) {
        close(); room = value
        refresh()
        if(person != null) choosePerson(person)
    }
    fun close() {
        listFence.invalidate(); targetFence.invalidate(); listJob?.cancel(); targetJob?.cancel()
        room = null; listing = GroupBlockListing(); loading = false; listError = null; syncedAt = 0
        uid = ""; expectedPortrait = ""; expectedName = ""; target = null; targetLoading = false; targetError = null
    }
    fun refresh(append: Boolean = false) {
        val group = room ?: return
        if(owner.isBlank() || loading || (append && !listing.hasMore)) return
        val page = if(append) listing.page + 1 else 1
        val account = owner; val ticket = listFence.next()
        loading = true; listError = null; retryAppend = append
        listJob = scope.launch {
            try {
                val result = fetchBlocks(group.id,page)
                if(owner == account && listFence.accepts(ticket)) {
                    listing = listing.accept(result,append); syncedAt = System.currentTimeMillis()/1000
                }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { if(owner == account && listFence.accepts(ticket)) listError = e.message ?: "禁言名单读取失败" }
            finally { if(owner == account && listFence.accepts(ticket)) loading = false }
        }
    }
    fun retry() = refresh(retryAppend)
    fun refreshAfterWrite() {
        // A pre-write list response must never masquerade as the post-write readback.
        listFence.invalidate(); listJob?.cancel(); loading = false
        refresh()
    }
    fun changeUid(value: String) {
        targetFence.invalidate(); targetJob?.cancel(); targetLoading = false; uid = value
        target = null; targetError = null
    }
    fun clearExpected() { expectedPortrait = ""; expectedName = ""; changeUid("") }
    fun choosePerson(person: Person) {
        clearExpected(); changeUid(person.id); prepare()
    }
    fun chooseBlocked(user: GroupBlockedUser, candidates: List<Person>) {
        clearExpected(); expectedPortrait = user.portrait; expectedName = user.name
        // Candidates only suggest a UID. Preparation still verifies it against fresh profile metadata.
        val match = candidates.firstOrNull { it.avatar == user.avatar || it.avatar.substringBefore('?') == user.avatar }
        changeUid(match?.id.orEmpty())
        if(uid.isNotBlank()) prepare()
    }
    fun prepare() {
        if(room == null || owner.isBlank()) return
        targetFence.invalidate(); targetJob?.cancel(); target = null; targetError = null; targetLoading = false
        val requested = uid.trim(); val expected = expectedPortrait
        if(requested.toLongOrNull()?.let { it > 0 } != true) { targetError = "请输入该账号的贴吧 UID"; return }
        if(requested == owner) { targetError = "请勿对当前登录账号执行群禁言管理"; return }
        val account = owner; val ticket = targetFence.next()
        targetLoading = true
        targetJob = scope.launch {
            try {
                val profile = fetchProfile(requested)
                val result = GroupModerationProtocol.target(profile,requested,expected)
                if(owner == account && targetFence.accepts(ticket)) target = result
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { if(owner == account && targetFence.accepts(ticket)) targetError = e.message ?: "账号资料读取失败" }
            finally { if(owner == account && targetFence.accepts(ticket)) targetLoading = false }
        }
    }
}
