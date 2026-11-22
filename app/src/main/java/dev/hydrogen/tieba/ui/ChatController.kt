package dev.hydrogen.tieba.ui

import androidx.compose.runtime.*
import dev.hydrogen.tieba.data.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

internal data class Conversation(val id: String, val person: Person, val room: ChatRoom? = null,
    val messages: List<ChatMessage> = emptyList(), val pinned: Boolean = false, val saved: Set<String> = emptySet(),
    val seen: Long = 0, val draft: String = "", val syncedAt: Long = 0) {
    val key get() = (if (room == null) "private:" else "group:") + id
}

internal object ChatStorage {
    fun encode(items: List<Conversation>) = JSONArray().apply { items.take(100).forEach { c ->
        put(JSONObject().put("id",c.id).put("name",c.person.name).put("avatar",c.person.avatar)
            .put("pinned",c.pinned).put("seen",c.seen).put("syncedAt",c.syncedAt).put("saved",JSONArray(c.saved.toList()))
            .put("room",c.room?.let { JSONObject().put("id",it.id).put("name",it.name).put("avatar",it.avatar).put("forum",it.forum)
                .put("members",it.members).put("announcement",it.announcement).put("forumId",it.forumId)
                .put("talkThreshold",it.talkThreshold).put("talkBanned",it.talkBanned).put("detailsLoaded",it.detailsLoaded)
                .put("bots",JSONArray().apply { it.bots.forEach { bot -> put(JSONObject().put("id",bot.id).put("name",bot.name).put("avatar",bot.avatar).put("role",bot.role)) } }) })
            .put("messages",JSONArray().apply { c.messages.takeLast(400).forEach { m ->
                put(JSONObject().put("id",m.id).put("sender",m.senderId).put("text",m.text).put("time",m.time).put("type",m.type)
                    .put("name",m.person.name).put("avatar",m.person.avatar).put("level",m.person.level).put("role",m.person.role)
                    .put("image",m.image).put("link",m.link).put("msgKey",m.msgKey).put("revoked",m.revoked).put("recalledIds",JSONArray(m.recalledIds)))
            } }))
    } }
    fun decode(json: String): List<Conversation> = runCatching {
        val array = JSONObject(json).optJSONArray("conversations") ?: return emptyList()
        (0 until array.length()).map { i ->
            val c = array.getJSONObject(i); val room = c.optJSONObject("room")?.let { r ->
                ChatRoom(r.getString("id"),r.getString("name"),r.optString("avatar"),r.optString("forum"),
                    if (r.has("members") && !r.isNull("members")) r.optLong("members") else null,r.optString("announcement"),
                    forumId = r.optString("forumId"),talkThreshold = if(r.has("talkThreshold") && !r.isNull("talkThreshold")) r.optInt("talkThreshold") else null,
                    talkBanned = if(r.has("talkBanned") && !r.isNull("talkBanned")) r.optBoolean("talkBanned") else null,
                    bots = CommunityParser.objects(r.optJSONArray("bots")).map { Person(it.optString("id"),it.optString("name"),it.optString("avatar"),role = it.optString("role")) },detailsLoaded = r.optBoolean("detailsLoaded")) }
            val list = c.optJSONArray("messages") ?: JSONArray()
            val messages = (0 until list.length()).map { index -> val m = list.getJSONObject(index)
                ChatMessage(m.getString("id"),m.getString("sender"),m.optString("text"),m.optLong("time"),m.optInt("type"),
                    Person(m.getString("sender"),m.optString("name","吧友"),m.optString("avatar"),level = m.optInt("level"),role = m.optString("role")),m.optString("image"),m.optString("link"),m.optString("msgKey"),m.optBoolean("revoked"),m.optJSONArray("recalledIds")?.let { ids -> (0 until ids.length()).map { ids.getString(it) } }.orEmpty()) }
            val saved = c.optJSONArray("saved") ?: JSONArray()
            Conversation(c.getString("id"),Person(c.getString("id"),c.getString("name"),c.optString("avatar")),room,messages,
                c.optBoolean("pinned"),(0 until saved.length()).map { saved.getString(it) }.toSet(),c.optLong("seen"),syncedAt = c.optLong("syncedAt"))
        }.filter { it.id.toLongOrNull()?.let { id -> id > 0 } == true }.distinctBy { it.key }
    }.getOrDefault(emptyList())
    fun merge(old: List<ChatMessage>, incoming: List<ChatMessage>): List<ChatMessage> {
        val remaining = old.toMutableList()
        // Reconcile only locally acknowledged group sends, one to one. Never
        // collapse two real server messages just because their texts match.
        incoming.distinctBy { "${it.senderId}:${it.id}" }.filter { !it.id.startsWith("ack:") && old.none { prior -> prior.id == it.id && prior.senderId == it.senderId } }.forEach { server ->
            val index = remaining.indexOfFirst { local -> local.id.startsWith("ack:") && local.senderId == server.senderId &&
                (if(local.msgKey.isNotBlank() || server.msgKey.isNotBlank()) local.msgKey.isNotBlank() && local.msgKey == server.msgKey
                 else local.text == server.text && kotlin.math.abs(local.time-server.time) < 120) }
            if(index >= 0) remaining.removeAt(index)
        }
        val recalled = (old + incoming).flatMap { it.recalledIds }.toSet()
        val merged = incoming.map { server -> if(old.any { it.revoked && it.senderId == server.senderId && it.id == server.id }) server.copy(revoked = true) else server }
        return (remaining + merged).associateBy { "${it.senderId}:${it.id}" }
        .values.map { if(it.revoked || it.id in recalled) it.copy(text = "",image = "",link = "",revoked = true) else it }.sortedWith(compareBy<ChatMessage> { it.time }.thenBy { it.id.toLongOrNull() ?: 0L })
    }
}

internal class ChatController(private val store: LocalStore, private val api: TiebaApi, private val scope: CoroutineScope) {
    val moderation = GroupModerationController(scope,api::groupBlocks,api::profile)
    private val privateChat = PrivateChatConnection()
    private var owner = ""
    private var account = AccountState()
    private var readJob: Job? = null
    private var sendJob: Job? = null
    var conversations by mutableStateOf<List<Conversation>>(emptyList()); private set
    var selected by mutableStateOf<String?>(null); private set
    val current get() = conversations.firstOrNull { it.key == selected }
    var loading by mutableStateOf(false); private set
    var privateSyncedAt by mutableLongStateOf(0); private set
    var privateEnabled by mutableStateOf(store.legacyPrivateChat); private set
    var sending by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var sendError by mutableStateOf<String?>(null); private set
    var rooms by mutableStateOf<List<ChatRoom>>(emptyList()); private set
    var directoryLoading by mutableStateOf(false); private set
    var directoryError by mutableStateOf<String?>(null); private set
    private val histories = mutableStateMapOf<String, GroupHistoryState>()
    private val viewports = mutableMapOf<String, ChatViewport>()
    val hasMore get() = current?.let { histories[it.key]?.hasMore } == true
    val historyExhausted get() = current?.let { histories[it.key]?.let { h -> h.initialized && !h.hasMore } } == true
    var loadingOlder by mutableStateOf(false); private set
    private var retryOlder = false
    var directoryQuery by mutableStateOf("")
    private var directoryJob: Job? = null
    private var groupJob: Job? = null
    private val groupReads = ChatReadFence()
    private var groupOffset = 0
    var groupsLoading by mutableStateOf(false); private set
    var groupsProgress by mutableIntStateOf(0); private set
    var groupsTotal by mutableIntStateOf(0); private set
    val groupErrors = mutableStateMapOf<String,String>()
    val groupDetailErrors = mutableStateMapOf<String,String>()
    var directoryVisible by mutableStateOf(false)
    var actionBusy by mutableStateOf(false); private set
    private var actionJob: Job? = null
    private val actions = ChatReadFence()
    private val actionResults = mutableStateMapOf<String,String>()
    val actionResult get() = selected?.let { actionResults[it] }
    private var uncertaintyRevision by mutableIntStateOf(0)
    val uncertain get(): Boolean { uncertaintyRevision; return current?.let { store.draftUncertain(draftKey(it.key)) } == true }
    private var epoch = 0L
    private val reads = ChatReadFence()
    private val sends = ChatReadFence()
    private var sendingPrivate = false
    private var directoryVersion = 0L
    fun bind(value: AccountState) {
        account = value
        val next = value.uid.takeIf { value.loggedIn }.orEmpty()
        if (next == owner) return
        close(); epoch++; owner = next; moderation.bind(next); selected = null; rooms = emptyList(); directoryQuery = ""; directoryError = null
        conversations = if (owner.isBlank()) emptyList() else ChatStorage.decode(store.chatState(owner)).map {
            it.copy(draft = store.draft(draftKey(it.key)), seen = maxOf(it.seen, store.chatSeen(owner, it.key)))
        }
        histories.clear(); viewports.clear(); groupErrors.clear(); groupDetailErrors.clear(); groupOffset = 0; directoryVisible = false; actionResults.clear()
        conversations.filter { it.room != null }.forEach { histories[it.key] = GroupHistoryState.cached(it.messages) }
        error = null; sendError = null; privateSyncedAt = 0
    }
    private fun draftKey(key: String) = "chat:$owner:$key"
    private fun persist() { if(owner.isNotBlank()) store.saveChatState(owner,JSONObject().put("conversations",ChatStorage.encode(conversations)).toString()) }
    private fun update(key: String, change: (Conversation) -> Conversation) {
        conversations = conversations.map { if(it.key == key) change(it) else it }; persist()
    }
    fun draft(text: String) { val c = current ?: return
        conversations = conversations.map { if(it.key == c.key) it.copy(draft = text) else it }
        store.saveDraft(draftKey(c.key), text)
    }
    fun pin(key: String) = update(key) { it.copy(pinned = !it.pinned) }
    fun viewport(key: String) = viewports[key]
    fun rememberViewport(key: String, viewport: ChatViewport) { if(selected == key) viewports[key] = viewport }
    fun markVisible(key: String, messageKeys: Set<String>) {
        if (selected != key) return
        val c = current ?: return
        val seen = visibleChatSeen(c.messages, messageKeys, c.seen)
        if (seen > c.seen) {
            conversations = conversations.map { if (it.key == key) it.copy(seen = seen) else it }
            // Scrolling must not re-encode every conversation and message.
            store.saveChatSeen(owner, key, seen)
        }
    }
    fun changePrivateEnabled(enabled: Boolean) {
        if(privateEnabled == enabled) return
        privateEnabled = enabled; store.legacyPrivateChat = enabled
        // Stop both in-flight reads and sends before closing the legacy socket.
        // Unconfirmed sends retain the existing uncertain-draft marker.
        if(current?.room == null) {
            reads.invalidate(); readJob?.cancel(); loading = false; loadingOlder = false
            error = null; sendError = current?.takeIf { store.draftUncertain(draftKey(it.key)) }?.let { "上次发送结果未确认，请先刷新核对会话，避免重复发送。草稿已保留。" }
        }
        if(sendingPrivate) { sends.invalidate(); sendJob?.cancel(); sending = false; sendingPrivate = false }
        privateChat.close(); privateSyncedAt = 0
    }
    fun save(message: ChatMessage) { val c = current ?: return; val key = "${message.senderId}:${message.id}"; update(c.key) { it.copy(saved = if(key in it.saved) it.saved-key else it.saved+key) } }
    fun select(c: Conversation) {
        moderation.close()
        stopGroupRefresh()
        reads.invalidate(); readJob?.cancel(); loading = false; loadingOlder = false
        selected = c.key; error = null; sendError = if(store.draftUncertain(draftKey(c.key))) "上次发送结果未确认，请先刷新核对会话，避免重复发送。草稿已保留。" else null
        if(c.room != null && c.key !in histories) histories[c.key] = GroupHistoryState.cached(c.messages)
        refresh()
    }
    fun back() { moderation.close(); pauseReads(selected); selected = null; error = null }
    fun restoreSelection(key: String?) {
        if(selected == key) return
        val conversation = conversations.firstOrNull { it.key == key }
        if(conversation != null) select(conversation) else back()
    }
    fun pauseReads(key: String?) { if(selected == key) { reads.invalidate(); readJob?.cancel(); loading = false; loadingOlder = false; if(key == null) stopGroupRefresh(); if(!sending) privateChat.close() } }
    fun openPrivate(person: Person) {
        if(owner.isBlank() || person.id.toLongOrNull()?.let { it > 0 } != true) { error = "请登录后输入有效的贴吧 UID"; return }
        val c = conversations.firstOrNull { it.key == "private:${person.id}" } ?: Conversation(person.id,person).also { conversations = conversations + it; persist() }
        select(c)
    }
    fun openGroup(room: ChatRoom) {
        if(owner.isBlank() || room.id.toLongOrNull()?.let { it > 0 } != true) { error = "请登录并选择有效的吧群"; return }
        val c = conversations.firstOrNull { it.key == "group:${room.id}" } ?: Conversation(room.id,Person(room.id,room.name,room.avatar),room).also { conversations = conversations + it; persist() }
        select(c)
    }
    fun findRooms() {
        if(directoryQuery.isBlank() || owner.isBlank()) { directoryError = "请登录并输入吧名"; return }
        directoryJob?.cancel(); directoryLoading = true; directoryError = null
        val version = ++directoryVersion; val accountEpoch = epoch; val query = directoryQuery.trim().removeSuffix("吧")
        directoryJob = scope.launch {
            try { val result = api.chatRooms(query); if(version == directoryVersion && epoch == accountEpoch) rooms = result }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { if(version == directoryVersion && epoch == accountEpoch) directoryError = e.message ?: "吧群读取失败" }
            finally { if(version == directoryVersion && epoch == accountEpoch) directoryLoading = false }
        }
    }
    fun cancelDirectory() { directoryVersion++; directoryJob?.cancel(); directoryLoading = false }
    fun changeDirectoryQuery(value: String) {
        if(directoryQuery == value) return
        cancelDirectory(); directoryQuery = value; rooms = emptyList(); directoryError = null
    }
    fun showDirectory(forum: String = "") {
        if(forum.isNotBlank() && directoryQuery != forum) { cancelDirectory(); rooms = emptyList(); directoryError = null; directoryQuery = forum }
        directoryVisible = true
        if(forum.isNotBlank()) findRooms()
    }
    private fun stopGroupRefresh() { groupReads.invalidate(); groupJob?.cancel(); groupsLoading = false }
    fun cancelGroupRefresh() = stopGroupRefresh()
    fun refreshGroups(all: Boolean = false) {
        if(owner.isBlank() || selected != null || groupsLoading) return
        val groups = conversations.filter { it.room != null && (all || it.key !in groupErrors) }
        if(groups.isEmpty()) return
        val targets = if(all) groups else List(minOf(4,groups.size)) { groups[(groupOffset+it)%groups.size] }
        if(!all) groupOffset = (groupOffset+targets.size)%groups.size
        val uid = owner; val accountEpoch = epoch; val ticket = groupReads.next()
        groupsLoading = true; groupsProgress = 0; groupsTotal = targets.size
        groupJob = scope.launch {
            try { for(c in targets) {
                try {
                    val result = loadGroupRefresh(c.room!!, false, { api.chatRoom(c.room) },
                        { api.chatMessages(c.id,ClientIdentity.cuid(store.installationId)) })
                    val room = result.room; val page = result.history
                    if(uid != owner || epoch != accountEpoch || !groupReads.accepts(ticket) || selected != null) return@launch
                    val history = histories[c.key] ?: GroupHistoryState.cached(c.messages)
                    val nextHistory = history.accept(page,false)
                    update(c.key) { it.copy(room = room,person = it.person.copy(name = room.name,avatar = room.avatar),
                        messages = ChatStorage.merge(it.messages,page.messages),syncedAt = System.currentTimeMillis()/1000) }
                    histories[c.key] = nextHistory
                    groupErrors.remove(c.key)
                    if(result.detailError == null) groupDetailErrors.remove(c.key) else groupDetailErrors[c.key] = result.detailError
                } catch(e: CancellationException) { throw e }
                catch(e: Exception) { if(uid == owner && epoch == accountEpoch && groupReads.accepts(ticket)) groupErrors[c.key] = e.message ?: "群同步失败" }
                finally { if(uid == owner && epoch == accountEpoch && groupReads.accepts(ticket)) groupsProgress++ }
            } } finally { if(uid == owner && epoch == accountEpoch && groupReads.accepts(ticket)) groupsLoading = false }
        }
    }
    fun refresh(older: Boolean = false) {
        if(owner.isBlank()) { error = "请先登录百度账号"; return }
        if(current?.room == null && (!PrivateChatConnection.ENABLED || !privateEnabled)) { error = null; return }
        if(loading) return
        val c = current
        val history = c?.let { histories[it.key] } ?: GroupHistoryState()
        if(older && (c?.room == null || !history.hasMore || history.before == null)) return
        readJob?.cancel(); loading = true; loadingOlder = older; retryOlder = older; error = null
        val uid = owner; val version = reads.next(); val accountEpoch = epoch
        readJob = scope.launch {
            try {
                if(c?.room != null) {
                    val before = if(older) history.before.toString() else ""
                    val result = loadGroupRefresh(c.room,older,{ api.chatRoom(c.room) },
                        { api.chatMessages(c.id,ClientIdentity.cuid(store.installationId),before) })
                    val detail = result.room; val batch = result.history
                    if(uid != owner || epoch != accountEpoch || !reads.accepts(version)) return@launch
                    val nextHistory = history.accept(batch, older)
                    update(c.key) { it.copy(room = detail,person = it.person.copy(name = detail.name,avatar = detail.avatar),messages = ChatStorage.merge(it.messages,batch.messages),
                        syncedAt = if(older) it.syncedAt else System.currentTimeMillis()/1000) }
                    histories[c.key] = nextHistory
                    groupErrors.remove(c.key)
                    if(!older) { if(result.detailError == null) groupDetailErrors.remove(c.key) else groupDetailErrors[c.key] = result.detailError }
                } else {
                    privateChat.login(uid,BaiduSession.cookie("BDUSS"))
                    val incoming = privateChat.messages(ClientIdentity.cuid(store.installationId))
                    if(uid != owner || epoch != accountEpoch || !reads.accepts(version)) return@launch
                    val synced = System.currentTimeMillis()/1000
                    incoming.groupBy { it.senderId }.forEach { (id, list) ->
                        val key = "private:$id"; val existing = conversations.firstOrNull { it.key == key }
                        if(existing != null) update(key) { it.copy(person = list.last().person,messages = ChatStorage.merge(it.messages,list),
                            syncedAt = synced) }
                        else conversations = conversations + Conversation(id,list.last().person,messages = list,syncedAt = synced)
                    }
                    if(c != null) update(c.key) { it.copy(syncedAt = synced) }
                    persist(); privateSyncedAt = synced
                }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { if(uid == owner && epoch == accountEpoch && reads.accepts(version)) error = e.message ?: "消息读取失败，请重试" }
            finally { if(uid == owner && epoch == accountEpoch && reads.accepts(version)) { loading = false; loadingOlder = false } }
        }
    }
    fun retryRead() = refresh(retryOlder)
    fun poll() { if(error == null) refresh(); if(selected == null) refreshGroups() }
    fun acknowledgeUncertain() { val c = current ?: return; if(sending) return; store.setDraftUncertain(draftKey(c.key),false); uncertaintyRevision++; sendError = null }
    fun joinCurrentGroup() {
        val c = current ?: return; val room = c.room ?: return
        val person = account.identity.copy(id = owner,name = account.name,avatar = account.avatar)
        groupAction(c) { api.joinGroup(room,person,ClientIdentity.cuid(store.installationId)); "百度已确认加入请求；群身份和发言权限以重新读取的资料为准。" }
    }
    fun withdraw(message: ChatMessage) {
        val c = current ?: return; val room = c.room ?: return
        if(!GroupChatRules.canWithdraw(room,message,owner)) return
        val uid = owner
        groupAction(c,onSuccess = {
            update(c.key) { it.copy(messages = it.messages.map { m -> if(m.id == message.id && m.senderId == message.senderId) m.copy(text = "",image = "",link = "",revoked = true) else m }) }
        }) {
            api.withdrawGroupMessage(room,message,uid)
            "百度已接受撤回请求"
        }
    }
    fun moderate(target: GroupModerationTarget, duration: GroupBlockDuration, unblock: Boolean) {
        val c = current ?: return; val room = c.room ?: return
        if(moderation.room?.id != room.id || moderation.target != target || target.person.id == owner) return
        val uid = owner
        groupAction(c,onSuccess = {
            if(moderation.room?.id == room.id) moderation.refreshAfterWrite()
        }) {
            if(unblock) api.unblockGroupUser(room,target,uid) else api.blockGroupUser(room,target,duration,uid)
            if(unblock) "百度已确认解除禁言，请刷新名单核对。" else "百度已确认禁言 ${duration.label}，请刷新名单核对。"
        }
    }
    private fun groupAction(c: Conversation, onSuccess: () -> Unit = {}, operation: suspend () -> String) {
        if(actionBusy || owner.isBlank() || sending || loading) return
        val uid = owner; val accountEpoch = epoch; val ticket = actions.next()
        actionBusy = true; actionResults.remove(c.key)
        actionJob = scope.launch {
            try {
                val result = operation()
                if(uid == owner && epoch == accountEpoch && actions.accepts(ticket)) { onSuccess(); actionResults[c.key] = result }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { if(uid == owner && epoch == accountEpoch && actions.accepts(ticket)) actionResults[c.key] = (e.message ?: "群操作未完成") + "。若连接中断，请先刷新核对结果，勿连续重复操作。" }
            finally { if(uid == owner && epoch == accountEpoch && actions.accepts(ticket)) { actionBusy = false; if(selected == c.key) refresh() } }
        }
    }
    fun send(quote: ChatMessage?, onSuccess: () -> Unit = {}) {
        val c = current ?: return
        if(sending || actionBusy || uncertain || c.draft.isBlank() || owner.isBlank() || (c.room == null && (!privateEnabled || !PrivateChatConnection.ENABLED))) return
        c.room?.let { GroupChatRules.restriction(it)?.let { reason -> sendError = reason; return } }
        val snapshot = c.draft; val uid = owner; val accountEpoch = epoch
        val person = account.identity.copy(id = uid,name = account.name,avatar = account.avatar)
        val currentQuote = quote?.let { target -> c.messages.firstOrNull { it.id == target.id && it.senderId == target.senderId } }
        val payload = GroupChatRules.payload(snapshot,currentQuote)
        if(payload.length > 2000) { sendError = "消息最多 2000 字，请缩短后发送"; return }
        sending = true; sendingPrivate = c.room == null; sendError = null
        val sendVersion = sends.next()
        sendJob = scope.launch {
            var dispatched = false
            fun markDispatched() { store.setDraftUncertain(draftKey(c.key),true); uncertaintyRevision++; dispatched = true }
            try {
                var msgKey = ""
                var sentPerson = person
                val id = if(c.room == null) {
                    privateChat.login(uid,BaiduSession.cookie("BDUSS"))
                    if(uid != owner || epoch != accountEpoch || !sends.accepts(sendVersion)) return@launch
                    markDispatched(); privateChat.send(c.id,payload,System.currentTimeMillis())
                } else {
                    val detail = api.chatRoom(c.room)
                    if(uid != owner || epoch != accountEpoch || !sends.accepts(sendVersion)) return@launch
                    update(c.key) { it.copy(room = detail) }
                    GroupChatRules.restriction(detail)?.let { throw IllegalStateException(it) }
                    sentPerson = api.forumIdentity(person.copy(level = 0,levelName = "",role = ""),detail.forumId)
                    if(uid != owner || epoch != accountEpoch || !sends.accepts(sendVersion)) return@launch
                    markDispatched()
                    val receipt = api.sendGroupText(detail,sentPerson,ClientIdentity.cuid(store.installationId),payload)
                    msgKey = receipt.msgKey; receipt.id
                }
                if(uid != owner || epoch != accountEpoch || !sends.accepts(sendVersion)) return@launch
                update(c.key) { it.copy(messages = ChatStorage.merge(it.messages,listOf(ChatMessage(id,uid,payload,System.currentTimeMillis()/1000,1,
                    sentPerson,msgKey = msgKey))),draft = if(it.draft == snapshot) "" else it.draft,seen = System.currentTimeMillis()/1000) }
                store.saveDraft(draftKey(c.key),conversations.first { it.key == c.key }.draft)
                store.setDraftUncertain(draftKey(c.key),false)
                uncertaintyRevision++
                if(selected == c.key) onSuccess()
                if(c.room != null && selected == c.key) refresh()
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { if(uid == owner && epoch == accountEpoch && sends.accepts(sendVersion) && selected == c.key) sendError = (e.message ?: "发送未完成") + if(dispatched) "。草稿已保留；请先刷新核对，避免重复发送。" else "。消息尚未提交，草稿已保留，可重试。" }
            finally { if(uid == owner && epoch == accountEpoch && sends.accepts(sendVersion)) { sending = false; sendingPrivate = false } }
        }
    }
    fun close() { moderation.close(); reads.invalidate(); sends.invalidate(); actions.invalidate(); stopGroupRefresh(); directoryVersion++; readJob?.cancel(); sendJob?.cancel(); directoryJob?.cancel(); actionJob?.cancel(); privateChat.close(); loading = false; loadingOlder = false; sending = false; sendingPrivate = false; actionBusy = false; directoryLoading = false }
}
