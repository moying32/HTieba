package dev.hydrogen.tieba.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.hydrogen.tieba.data.LocalStore
import dev.hydrogen.tieba.data.PostItem
import dev.hydrogen.tieba.data.ThemeMode
import dev.hydrogen.tieba.data.VisualStyle
import dev.hydrogen.tieba.data.ThreadItem
import dev.hydrogen.tieba.data.TiebaApi
import dev.hydrogen.tieba.data.AccountState
import dev.hydrogen.tieba.data.BaiduSession
import dev.hydrogen.tieba.data.LoginExpiredException
import dev.hydrogen.tieba.data.DiscoveryPage
import dev.hydrogen.tieba.data.TiebaRejectedException
import dev.hydrogen.tieba.data.CommentPreparationException
import dev.hydrogen.tieba.data.*
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class FeedState(
    val threads: List<ThreadItem> = emptyList(), val page: Int = 0, val hasMore: Boolean = true,
    val loading: Boolean = false, val loadingMore: Boolean = false, val error: String? = null, val members: Long = 0,
    val forumId: String = "", val followed: Boolean = false,
    val info: ForumInfo? = null,
    val cursor: String = "",
)
data class ReadingState(
    val id: String, val summary: ThreadItem? = null, val posts: List<PostItem> = emptyList(), val page: Int = 0,
    val hasMore: Boolean = true, val loading: Boolean = true, val loadingMore: Boolean = false, val error: String? = null,
    val onlyAuthor: Boolean = false,
    val positionRequest: Long = 0, val targetPostId: String = "",
    val firstLoadedPage: Int = 1,
    val sort: ReplySort = ReplySort.Ascending,
)
enum class HomeTab(val title: String) { Feed("刷帖"), Browse("逛吧"), Compose("发帖"), Messages("消息"), Profile("我的") }

data class GroupChatsState(val forum: String = "", val rooms: List<ChatRoom> = emptyList(), val room: ChatRoom? = null,
    val messages: List<ChatMessage> = emptyList(), val loading: Boolean = false, val loadingMore: Boolean = false,
    val hasMore: Boolean = false, val error: String? = null, val searched: Boolean = false)
data class FloorState(val parent: PostItem, val posts: List<PostItem> = emptyList(), val page: Int = 0,
    val loading: Boolean = true, val error: String? = null, val hasMore: Boolean = true, val targetPostId: String = "")
data class ComposerState(
    val thread: ThreadItem, val parent: PostItem? = null, val text: String = "", val sending: Boolean = false,
    val error: String? = null, val uncertain: Boolean = false, val owner: String = "guest", val target: PostItem? = null,
) {
    val draftKey get() = "$owner:${thread.id}:${parent?.id.orEmpty()}${target?.let { ":${it.id}" }.orEmpty()}"
}

enum class PanelKind(val label: String) { Threads("我的帖子"), Comments("我的评论"), Cloud("云收藏"),
    People("关注"), Fans("粉丝"), User("用户主页"), Forums("管理我的吧"), EditProfile("编辑资料"), Search("搜索"), Notices("消息"), Publish("发布帖子") }
data class PanelState(val key: Long, val kind: PanelKind, val uid: String = "", val loading: Boolean = true,
    val error: String? = null, val page: Int = 0, val hasMore: Boolean = false, val items: List<UserContent> = emptyList(),
    val people: List<UserProfile> = emptyList(), val profile: UserProfile? = null, val forums: List<ForumItem> = emptyList(),
    val query: String = "", val searchKind: SearchKind = SearchKind.Thread, val results: SearchPage = SearchPage(),
    val noticeKind: NoticeKind = NoticeKind.Reply, val notices: List<Notice> = emptyList(), val cursor: String = "0",
    val refreshing: Boolean = false)
private data class SurfaceReturn(val forScreen: String, val tab: HomeTab, val panel: PanelState?, val reading: ReadingState?,
    val forum: String, val forumOpen: Boolean, val feed: FeedState, val collection: String?, val sort: ForumSort, val nativeChat: String?)

class TiebaViewModel(application: Application) : AndroidViewModel(application) {
    private val store = LocalStore(application)
    private val updateRepository = UpdateRepository(application)
    internal val updates = UpdateController(viewModelScope, updateRepository,
        requireNotNull(AppVersion.parse(dev.hydrogen.tieba.BuildConfig.VERSION_NAME)),
        { updateRepository.skippedVersion }, { updateRepository.skippedVersion = it })
    var contentFilters by mutableStateOf(store.contentFilters)
        private set
    var showDecorativeBadges by mutableStateOf(store.showDecorativeBadges)
        private set
    var feedImageQuality by mutableStateOf(store.feedImageQuality)
        private set
    fun changeDecorativeBadges(value: Boolean) { store.showDecorativeBadges = value; showDecorativeBadges = value }
    fun changeFeedImageQuality(value: ImageQuality) { store.feedImageQuality = value; feedImageQuality = value }
    private fun saveFilters(value: ContentFilters) { store.contentFilters = value; contentFilters = value }
    fun hideThread(thread: ThreadItem) {
        saveFilters(contentFilters.copy(threads = (setOf(thread.id) + contentFilters.threads).take(1000).toSet()))
        messages.tryEmit("已在本机隐藏这篇帖子，可在阅读设置中恢复")
    }
    fun blockAuthor(person: Person) {
        if (person.id.isBlank()) return
        saveFilters(contentFilters.copy(users = contentFilters.users + (person.id to person.name)))
        messages.tryEmit("已在本机屏蔽该作者的列表帖子")
    }
    fun blockForum(name: String) {
        if (name.isBlank()) return
        saveFilters(contentFilters.copy(forums = contentFilters.forums + name.trim()))
        messages.tryEmit("已在本机屏蔽${name}吧的列表帖子")
    }
    fun unblockAuthor(id: String) = saveFilters(contentFilters.copy(users = contentFilters.users - id))
    fun unblockForum(name: String) = saveFilters(contentFilters.copy(forums = contentFilters.forums - name))
    fun restoreHiddenThreads() = saveFilters(contentFilters.copy(threads = emptySet()))
    private val api = TiebaApi(store.installationId)
    internal val chats = ChatController(store, api, viewModelScope)
    internal val publishingForums = PublishingForumController(viewModelScope,
        { query, page -> api.search(query, SearchKind.Forum, page) }, { uid -> api.followedForums(uid) })
    override fun onCleared() { updates.close(); publishingForums.close(); chats.close(); super.onCleared() }
    private val themeStore = ThemePackStore(File(application.filesDir,"themes"))
    var appFont by mutableStateOf(store.appFont)
        private set
    var customThemes by mutableStateOf(themeStore.all())
        private set
    var customTheme by mutableStateOf(customThemes.firstOrNull { it.id == store.customThemeId })
        private set
    var themeImportPreview by mutableStateOf<ThemePack?>(null)
        private set
    var themeBusy by mutableStateOf(false)
        private set
    var themeError by mutableStateOf<String?>(null)
        private set
    private var feedJob: Job? = null
    private var readingJob: Job? = null
    private var identityJob: Job? = null
    private val identities = mutableMapOf<Pair<String,String>,Person>()
    private var floorJob: Job? = null
    private var composerFloorReturn: FloorState? = null
    private var accountJob: Job? = null
    private var interactionJob: Job? = null
    private var panelJob: Job? = null
    private var chatJob: Job? = null
    var groupChats by mutableStateOf(GroupChatsState())
        private set
    private var returns = emptyList<SurfaceReturn>()
    var panel by mutableStateOf<PanelState?>(null)
        private set
    var publishing by mutableStateOf<PublishingDraft?>(null)
        private set
    var forumSort by mutableStateOf(ForumSort.Reply)
        private set
    var forumInfoOpen by mutableStateOf(false)
    var searchHistory by mutableStateOf(store.searchHistory())
        private set
    var hotTopics by mutableStateOf<List<HotTopic>>(emptyList())
        private set
    var hotLoading by mutableStateOf(false)
        private set
    var hotError by mutableStateOf<String?>(null)
        private set
    private var hotJob: Job? = null
    var imageQuality by mutableStateOf(store.imageQuality)
        private set
    var video by mutableStateOf<VideoSource?>(null)
        private set
    var videoLoading by mutableStateOf(false)
        private set
    var videoError by mutableStateOf<String?>(null)
        private set
    private var videoJob: Job? = null
    fun changeImageQuality(value: ImageQuality) { store.imageQuality=value; imageQuality=value }
    suspend fun resolveImageGallery(gallery: ImageGallery): ImageGallery =
        gallery.withDetailImages(api.detail(gallery.threadId, 1))
    fun previewVideo(source: VideoSource) {
        videoJob?.cancel(); video=source; videoError=null
        videoLoading=source.url.isBlank()
        if(!videoLoading) return
        videoJob=viewModelScope.launch {
            try {
                require(source.threadId.isNotBlank()) { "贴吧未返回视频播放地址" }
                val detail=api.detail(source.threadId,1)
                val url=detail.thread.video.ifBlank { detail.posts.flatMap { it.content }.firstOrNull { it.video.isNotBlank() }?.video.orEmpty() }
                require(url.isNotBlank()) { "贴吧未返回可直接播放的视频地址，请在原帖查看" }
                video=source.copy(url=url)
            } catch(e: CancellationException) { throw e
            } catch(e: Exception) { videoError=friendly(e)
            } finally { videoLoading=false }
        }
    }
    fun closeVideo() { videoJob?.cancel(); video=null; videoLoading=false; videoError=null }
    fun loadHotTopics() {
        if(hotLoading) return
        hotLoading=true; hotError=null
        hotJob=viewModelScope.launch {
            try { hotTopics=api.hotTopics()
            } catch(e: CancellationException) { throw e
            } catch(e: Exception) { hotError=friendly(e)
            } finally { hotLoading=false }
        }
    }
    var textScale by mutableStateOf(store.textScale)
        private set
    var interfaceScale by mutableStateOf(store.interfaceScale)
        private set
    var profileCoverRevision by mutableIntStateOf(0)
        private set
    var profileCoverBusy by mutableStateOf(false)
        private set
    val localProfileCover: String get() {
        profileCoverRevision
        return store.profileCover(account.uid.ifBlank { "guest" })
    }
    fun replaceProfileCover(uri: Uri) {
        if (profileCoverBusy) return
        val owner = account.uid.ifBlank { "guest" }
        profileCoverBusy = true
        viewModelScope.launch {
            var imported: File? = null
            var saved = false
            try {
                val file = ProfileCoverStore.import(getApplication(), uri) { imported = it }
                val previous = store.profileCover(owner)
                store.setProfileCover(owner, file.absolutePath)
                saved = true
                profileCoverRevision++
                if (previous.isNotBlank()) withContext(Dispatchers.IO) { File(previous).delete() }
                messages.tryEmit("已更换本机主页背景")
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { messages.tryEmit(e.message ?: "背景更换失败，请重试") }
            finally {
                if (!saved) imported?.delete()
                profileCoverBusy = false
            }
        }
    }
    fun resetProfileCover() {
        val owner = account.uid.ifBlank { "guest" }
        val previous = store.profileCover(owner)
        store.setProfileCover(owner, "")
        profileCoverRevision++
        viewModelScope.launch(Dispatchers.IO) { if (previous.isNotBlank()) File(previous).delete() }
        messages.tryEmit("已恢复贴吧主页背景")
    }
    var tab by mutableStateOf(HomeTab.Feed)
    var settingsOpen by mutableStateOf(false)
    var collection by mutableStateOf<String?>(null)
    var forumOpen by mutableStateOf(false)
    var stream by mutableStateOf(FeedState())
        private set
    var discovery by mutableStateOf(DiscoveryPage(emptyList(), emptyList()))
        private set
    var discovering by mutableStateOf(false)
        private set
    var discoveryError by mutableStateOf<String?>(null)
        private set
    var forumSignState by mutableStateOf(ForumSignState())
        private set
    private var streamJob: Job? = null
    private var followingJob: Job? = null
    var followingSelected by mutableStateOf(false)
        private set
    var following by mutableStateOf(FeedState())
        private set
    private var discoveryJob: Job? = null
    var history by mutableStateOf(store.history())
        private set
    var forum by mutableStateOf(store.lastForum)
        private set
    var recentForums by mutableStateOf(store.recentForums())
        private set
    var feed by mutableStateOf(FeedState())
        private set
    var reading by mutableStateOf<ReadingState?>(null)
        private set
    var bookmarks by mutableStateOf(store.bookmarks())
        private set
    var theme by mutableStateOf(store.theme)
        private set
    var visualStyle by mutableStateOf(store.visualStyle)
        private set
    var largeText by mutableStateOf(store.largeText)
        private set
    var richEffects by mutableStateOf(store.richEffects)
        private set
    var accentColor by mutableStateOf(customTheme?.let(store::customAccent) ?: store.accentColor)
        private set
    var reduceMotion by mutableStateOf(store.reduceMotion)
        private set
    var autoHideNavigation by mutableStateOf(store.autoHideNavigation)
        private set
    var account by mutableStateOf(AccountState())
        private set
    var pending by mutableStateOf<String?>(null)
        private set
    var interactionError by mutableStateOf<String?>(null)
        private set
    fun dismissInteractionError() { interactionError = null }
    var composer by mutableStateOf<ComposerState?>(null)
        private set
    var webUrl by mutableStateOf<String?>(null)
    var floor by mutableStateOf<FloorState?>(null)
        private set
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    init { if (customTheme == null) store.customThemeId = null; loadStream(true); loadDiscovery() }

    fun navigateTab(value: HomeTab) {
        if (value == HomeTab.Compose) { openPublishing(); return }
        panelJob?.cancel()
        settingsOpen = false; collection = null; forumOpen = false; panel = null; returns = emptyList()
        tab = value
        if (value == HomeTab.Browse && discovery.followed.isEmpty() && !discovering) loadDiscovery()
    }

    fun loadStream(refresh: Boolean = false) {
        if (!refresh && (stream.loading || stream.loadingMore || !stream.hasMore)) return
        streamJob?.cancel()
        val page = if (refresh) 1 else stream.page + 1
        stream = stream.copy(loading = refresh, loadingMore = !refresh, error = null)
        streamJob = viewModelScope.launch {
            try {
                val result = api.personalized(page)
                val fresh = result.threads.any { item -> stream.threads.none { it.id == item.id } }
                stream = FeedState(threads = (if (refresh) result.threads else stream.threads + result.threads).distinctBy { it.id },
                    page = page, hasMore = result.hasMore && (refresh || fresh))
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { stream = stream.copy(loading = false, loadingMore = false, error = friendly(e)) }
        }
    }

    fun selectFeed(follows: Boolean) {
        followingSelected = follows
        if (follows && following.page == 0 && !following.loading) loadFollowing(true)
    }
    fun loadFollowing(refresh: Boolean = false) {
        if (!requireAccount()) return
        if (!refresh && (following.loading || following.loadingMore || !following.hasMore)) return
        followingJob?.cancel()
        val current = following
        val page = if (refresh) 1 else current.page + 1
        following = current.copy(loading = refresh, loadingMore = !refresh, error = null)
        followingJob = viewModelScope.launch {
            try {
                val result = api.following(page, if (refresh) "" else current.cursor)
                val combined = (if (refresh) result.threads else current.threads + result.threads).distinctBy { it.id }
                following = FeedState(threads = combined, page = page, hasMore = result.hasMore && (refresh || combined.size > current.threads.size), cursor = result.cursor)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { expireIfNeeded(e); following = current.copy(loading = false, loadingMore = false, error = friendly(e)) }
        }
    }

    fun loadDiscovery() {
        if (discoveryJob?.isActive == true) return
        discovering = true; discoveryError = null
        discoveryJob = viewModelScope.launch {
            try { discovery = api.discovery()
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { discoveryError = friendly(e)
            } finally { discovering = false }
        }
    }

    fun navigateInput(input: String) {
        val value = input.trim()
        if (value.isEmpty()) return
        val uri = runCatching { Uri.parse(value) }.getOrNull()
        val forumLink = if (uri?.host == "tieba.baidu.com" && uri.path == "/f") uri.getQueryParameter("kw") else null
        if (!forumLink.isNullOrBlank()) { webUrl = null; openForum(forumLink); return }
        val id = Regex("(?:https?://)?tieba\\.baidu\\.com/p/(\\d+)").find(value)?.groupValues?.get(1)
            ?: value.takeIf { it.length in 6..20 && it.all(Char::isDigit) }
        if (id != null) { webUrl = null; openThread(id); return }
        if (value.contains("://")) { messages.tryEmit("请输入吧名或贴吧帖子链接"); return }
        openForum(value.removeSuffix("吧").ifBlank { value })
    }

    fun openForum(name: String) {
        rememberSurface("forum")
        closeFloor()
        readingJob?.cancel()
        reading = null
        panel = null; forumInfoOpen = false; forumSort = ForumSort.Reply
        forumOpen = true
        settingsOpen = false; collection = null
        forum = name
        feed = FeedState()
        loadForum(refresh = true)
    }

    fun loadForum(refresh: Boolean = false) {
        if (!refresh && (feed.loading || feed.loadingMore || !feed.hasMore)) return
        feedJob?.cancel()
        val target = forum
        val page = if (refresh) 1 else feed.page + 1
        feed = feed.copy(loading = refresh, loadingMore = !refresh, error = null)
        feedJob = viewModelScope.launch {
            try {
                val result = api.forum(target, page, forumSort)
                if (target != forum) return@launch
                feed = FeedState(
                    threads = (if (refresh) result.threads else feed.threads + result.threads).distinctBy { it.id },
                    page = page, hasMore = result.hasMore && (refresh || result.threads.any { incoming -> feed.threads.none { it.id == incoming.id } }),
                    members = if (refresh) result.members else feed.members,
                    forumId = if (refresh) result.forumId else feed.forumId,
                    followed = if (refresh) result.followed else feed.followed,
                    info = if (refresh) result.info else feed.info,
                )
                store.rememberForum(target)
                recentForums = store.recentForums()
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                feed = feed.copy(loading = false, loadingMore = false, error = friendly(error))
            }
        }
    }

    fun openThread(id: String, summary: ThreadItem? = null) {
        closeFloor()
        readingJob?.cancel()
        reading = ReadingState(id, summary)
        summary?.let { store.rememberThread(it); history = store.history() }
        loadReplies(refresh = true)
    }

    fun closeThread() { closeFloor(); readingJob?.cancel(); identityJob?.cancel(); reading = null }

    fun openPrivateMessages() {
        if (requireAccount()) webUrl = "https://tieba.baidu.com/im/pcmsg/"
    }

    fun openNativePrivate(person: Person) {
        if (!requireAccount()) return
        if(person.id.toLongOrNull()?.let { it > 0 } != true) { messages.tryEmit("贴吧未返回有效的私信 UID"); return }
        chats.bind(account)
        if (reading != null || panel != null || forumOpen) rememberSurface("chat")
        readingJob?.cancel(); panelJob?.cancel(); closeFloor()
        reading = null; panel = null; forumOpen = false; collection = null; settingsOpen = false; tab = HomeTab.Messages
        chats.openPrivate(person)
    }
    fun closeNativeChat() { chats.back(); restoreSurface("chat") }

    fun openNativeGroups(forumName: String) {
        if (!requireAccount()) return
        chats.bind(account)
        if (reading != null || panel != null || forumOpen) rememberSurface("chat")
        readingJob?.cancel(); panelJob?.cancel(); closeFloor()
        reading = null; panel = null; forumOpen = false; forumInfoOpen = false
        collection = null; settingsOpen = false; tab = HomeTab.Messages
        chats.back(); chats.showDirectory(forumName)
    }
    fun dismissGroupDirectory() {
        chats.directoryVisible = false; chats.cancelDirectory()
        if(chats.selected == null) restoreSurface("chat")
    }

    fun findChatRooms(name: String) {
        if (!requireAccount() || name.isBlank()) return
        chatJob?.cancel()
        val forumName = name.trim()
        groupChats = GroupChatsState(forum = forumName, loading = true, searched = true)
        chatJob = viewModelScope.launch {
            try { groupChats = groupChats.copy(rooms = api.chatRooms(forumName), loading = false)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { groupChats = groupChats.copy(loading = false, error = friendly(e)) }
        }
    }

    fun openChatRoom(room: ChatRoom) {
        if (!requireAccount()) return
        chatJob?.cancel()
        groupChats = groupChats.copy(room = room, messages = emptyList(), loading = true, hasMore = false, error = null)
        chatJob = viewModelScope.launch {
            try {
                val detail = api.chatRoom(room)
                groupChats = groupChats.copy(room = detail)
                val history = api.chatMessages(room.id, ClientIdentity.cuid(store.installationId))
                groupChats = groupChats.copy(messages = history.messages, hasMore = history.hasMore, loading = false)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { groupChats = groupChats.copy(loading = false, error = friendly(e)) }
        }
    }

    fun loadChatHistory() {
        val current = groupChats
        val room = current.room ?: return
        if (current.loading || current.loadingMore || !current.hasMore) return
        val before = current.messages.mapNotNull { it.id.toLongOrNull() }.minOrNull()?.toString() ?: return
        groupChats = current.copy(loadingMore = true, error = null)
        chatJob = viewModelScope.launch {
            try {
                val history = api.chatMessages(room.id, ClientIdentity.cuid(store.installationId), before)
                val fresh = history.messages.filter { incoming -> current.messages.none { it.id == incoming.id } }
                groupChats = current.copy(messages = (fresh + current.messages).sortedBy { it.time },
                    hasMore = history.hasMore && fresh.isNotEmpty(), loadingMore = false)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { groupChats = current.copy(loadingMore = false, error = friendly(e)) }
        }
    }

    fun closeChatRoom() { chatJob?.cancel(); groupChats = groupChats.copy(room = null, messages = emptyList(), loading = false, loadingMore = false, error = null) }

    private fun rememberSurface(destination: String) {
        returns = returns + SurfaceReturn(destination, tab, panel, reading, forum, forumOpen, feed, collection, forumSort,chats.selected)
    }

    private fun restoreSurface(destination: String): Boolean {
        val previous = returns.lastOrNull()?.takeIf { it.forScreen == destination } ?: return false
        returns = returns.dropLast(1)
        tab = previous.tab; panel = previous.panel; reading = previous.reading
        forum = previous.forum; forumOpen = previous.forumOpen; feed = previous.feed; collection = previous.collection
        forumSort = previous.sort
        chats.restoreSelection(previous.nativeChat)
        if (reading?.loading == true || reading?.loadingMore == true) loadReplies(true)
        if (panel?.loading == true) loadPanel(true)
        return true
    }

    fun closeForum() { feedJob?.cancel(); forumInfoOpen = false; if (!restoreSurface("forum")) forumOpen = false }
    fun changeForumSort(value: ForumSort) { if (forumSort != value) { forumSort = value; loadForum(true) } }
    fun toggleOnlyAuthor() { reading = reading?.copy(onlyAuthor = reading?.onlyAuthor != true); loadReplies(true) }

    fun changeReplySort(value: ReplySort) {
        val current = reading ?: return
        if (current.sort == value) return
        closeFloor()
        readingJob?.cancel()
        reading = current.copy(sort = value, posts = emptyList(), page = 0, hasMore = true,
            loading = false, loadingMore = false, error = null, firstLoadedPage = 1,
            targetPostId = "", positionRequest = current.positionRequest + 1)
        loadReplies(true)
    }

    fun jumpToPage(page: Int, postId: String = "", isFloor: Boolean = false) {
        val current = reading ?: return
        if (page < 1) return
        readingJob?.cancel(); reading = current.copy(loading = true, error = null, loadingMore = false)
        readingJob = viewModelScope.launch {
            try {
                val locator = if (postId.isNotBlank()) try { api.detail(current.id, 1, false, postId)
                } catch (e: CancellationException) { throw e
                } catch (_: Exception) { null } else null
                val targetPage = locator?.let { ReplyLocation.page(it, postId) } ?: page
                val sort = if (postId.isBlank()) current.sort else ReplySort.Ascending
                val first = api.detail(current.id, 1, if (postId.isBlank()) current.onlyAuthor else false, sort = sort)
                val full = if (targetPage == 1) first else try {
                    api.detail(current.id, targetPage, if (postId.isBlank()) current.onlyAuthor else false, sort = sort)
                } catch (e: CancellationException) { throw e
                } catch (e: Exception) {
                    messages.tryEmit("目标页暂时无法读取，已打开原帖开头：${friendly(e)}")
                    first
                }
                val result = ReplyLocation.withRoot(first, full)
                val parent = ReplyLocation.parent(result.posts, postId)
                    ?: locator?.posts?.firstOrNull { isFloor && it.floor > 1 }
                val anchor = parent?.id?.takeIf { pid -> result.posts.any { it.id == pid } }.orEmpty()
                if (reading?.id == current.id) reading = current.copy(summary = result.thread, posts = result.posts,
                    page = result.page, hasMore = result.hasMore, loading = false, loadingMore = false,
                    onlyAuthor = if (postId.isBlank()) current.onlyAuthor else false, firstLoadedPage = result.page,
                    positionRequest = current.positionRequest + 1, targetPostId = anchor, sort = sort)
                if (postId.isNotBlank() && anchor.isBlank()) messages.tryEmit("已打开原帖，暂时无法定位到目标回复")
                if (reading?.id == current.id && parent != null && parent.id != postId) openFloorAt(parent, postId)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { if (reading?.id == current.id) reading = current.copy(error = friendly(e), loading = false) }
        }
    }

    fun loadEarlierReplies() {
        val current = reading ?: return
        if (current.firstLoadedPage <= 1 || current.loading || current.loadingMore) return
        reading = current.copy(loadingMore = true, error = null)
        readingJob = viewModelScope.launch {
            try {
                val result = api.detail(current.id, current.firstLoadedPage - 1, current.onlyAuthor, sort = current.sort)
                if (reading?.id == current.id) reading = current.copy(
                    posts = (current.posts.filter { it.floor == 1 } + result.posts + current.posts).distinctBy { it.id },
                    firstLoadedPage = result.page, loadingMore = false)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { if (reading?.id == current.id) reading = current.copy(loadingMore = false, error = friendly(e)) }
        }
    }

    fun openPanel(kind: PanelKind, uid: String = account.uid, notice: NoticeKind = NoticeKind.Reply) {
        if (kind !in listOf(PanelKind.Search, PanelKind.User, PanelKind.People, PanelKind.Fans) && !requireAccount()) return
        if (panel != null || reading != null) rememberSurface("panel")
        panelJob?.cancel(); readingJob?.cancel(); closeFloor(); reading = null
        panel = PanelState(System.nanoTime(), kind, uid, noticeKind = notice, profile = account.profile.takeIf { uid == account.uid }, loading = kind != PanelKind.Search)
        if (kind != PanelKind.Search) loadPanel(true) else if(hotTopics.isEmpty()) loadHotTopics()
    }

    fun openUser(person: Person) {
        if (person.id.isBlank()) { messages.tryEmit("贴吧未返回该用户的主页标识"); return }
        openPanel(PanelKind.User, person.id)
    }

    fun closePanel() { if (publishing?.sending == true) return; panelJob?.cancel(); if (!restoreSurface("panel")) panel = null }

    fun submitSearch(query: String, kind: SearchKind = panel?.searchKind ?: SearchKind.Thread) {
        val q = query.trim().take(100)
        if (q.isBlank()) return
        if (q.contains("tieba.baidu.com/p/") || (q.length in 6..20 && q.all(Char::isDigit))) { navigateInput(q); return }
        store.rememberSearch(q); searchHistory = store.searchHistory()
        panel = panel?.copy(query = q, searchKind = kind, results = SearchPage(), page = 0, hasMore = true)
        loadPanel(true)
    }
    fun clearSearchHistory() { store.clearSearchHistory(); searchHistory = emptyList() }
    fun resetSearch() { panelJob?.cancel(); panel=panel?.copy(query="",results=SearchPage(),loading=false,refreshing=false,error=null,page=0,hasMore=false) }

    fun loadPanel(refresh: Boolean = false) {
        val current = panel ?: return
        if (!refresh && (current.loading || !current.hasMore)) return
        panelJob?.cancel()
        val page = if (refresh) 1 else current.page + 1
        panel = current.copy(loading = true, refreshing = refresh, error = null)
        panelJob = viewModelScope.launch {
            try {
                var result = current.copy(loading = false, refreshing = false, error = null, page = page)
                when (current.kind) {
                    PanelKind.Publish -> Unit
                    PanelKind.Threads, PanelKind.Comments, PanelKind.Cloud, PanelKind.User -> {
                        val profile = if (current.kind == PanelKind.User && refresh) api.profile(current.uid) else current.profile
                        if (panel?.key == current.key && profile != null) panel = panel?.copy(profile = profile)
                        val data = if (current.kind == PanelKind.Cloud) api.cloudCollection(current.uid, page)
                            else api.userContents(current.uid, current.kind != PanelKind.Comments, page)
                        val owner = profile?.person ?: account.identity.takeIf { current.uid == account.uid && account.loggedIn }
                        val incoming = data.items.map { item ->
                            if (owner != null && current.kind != PanelKind.Cloud) item.copy(thread=item.thread.copy(author=owner)) else item
                        }
                        val combined = (if (refresh) incoming else current.items + incoming).distinctBy { it.postId.ifBlank { it.thread.id } }
                        result = result.copy(profile = profile, items = combined, hasMore = data.hasMore && (refresh || combined.size > current.items.size))
                    }
                    PanelKind.People, PanelKind.Fans -> {
                        val data = api.people(current.uid, current.kind == PanelKind.Fans, page)
                        val combined = (if (refresh) data.users else current.people + data.users).distinctBy { it.person.id }
                        result = result.copy(people = combined, hasMore = data.hasMore && (refresh || combined.size > current.people.size))
                    }
                    PanelKind.Forums -> result = result.copy(forums = api.followedForums(current.uid))
                    PanelKind.EditProfile -> result = result.copy(profile = api.profile(current.uid))
                    PanelKind.Search -> {
                        if (current.query.isNotBlank()) {
                            val data = api.search(current.query, current.searchKind, page)
                            val old = if (refresh) SearchPage() else current.results
                            result = result.copy(results = data.copy(threads = (old.threads + data.threads).distinctBy { it.id },
                                forums = (old.forums + data.forums).distinctBy { it.id }, users = (old.users + data.users).distinctBy { it.person.id }), hasMore = data.hasMore)
                        }
                    }
                    PanelKind.Notices -> {
                        val data = api.notices(current.noticeKind, page, if (refresh) "0" else current.cursor)
                        val combined = (if (refresh) data.items else current.notices + data.items).distinctBy { "${it.id}:${it.postId}:${it.time}" }
                        result = result.copy(notices = combined, hasMore = data.hasMore && (refresh || combined.size > current.notices.size), cursor = data.cursor)
                    }
                }
                if (panel?.key == current.key) panel = result
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                expireIfNeeded(e)
                if (panel?.key == current.key) panel = panel?.copy(loading = false, refreshing = false, error = friendly(e))
            }
        }
    }

    private fun mutate(key: String, operation: suspend () -> Unit, success: () -> Unit) {
        if (pending != null || !requireAccount()) return
        pending = key
        interactionJob = viewModelScope.launch {
            try { operation(); success()
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { expireIfNeeded(e); interactionError = friendly(e)
            } finally { pending = null }
        }
    }
    fun followUser(profile: UserProfile) = mutate("user:${profile.person.id}", { api.followUser(profile, profile.followed) }) {
        panel = panel?.copy(profile = profile.copy(followed = !profile.followed)); messages.tryEmit(if (profile.followed) "已取消关注" else "已关注")
    }
    fun cloudSave(thread: ThreadItem, remove: Boolean = false) = mutate("cloud:${thread.id}", { api.saveCloud(thread.id, remove) }) {
        messages.tryEmit(if (remove) "已移出云收藏" else "已保存到百度云收藏")
        if (panel?.kind == PanelKind.Cloud) loadPanel(true)
    }
    fun deleteContent(content: UserContent, isThread: Boolean) {
        if (panel?.uid != account.uid || panel?.kind !in listOf(PanelKind.Threads, PanelKind.Comments)) return
        mutate("delete:${content.postId}", {
            val item = if (content.thread.forumId.isBlank()) content.copy(thread = content.thread.copy(forumId = api.forum(content.thread.forum, 1).forumId)) else content
            api.deleteOwn(item, isThread)
        }) { messages.tryEmit("服务器已接受删除请求"); loadPanel(true) }
    }
    fun saveProfile(name: String, intro: String, gender: Int) = mutate("profile", {
        api.updateProfile(name, intro, gender)
    }) {
        messages.tryEmit("资料已保存"); refreshAccount(); loadPanel(true)
    }
    private fun signDay() = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString()
    val currentForumSignState get() = forumSignState.takeIf {
        it.day == signDay() && (it.owner == account.uid || (!account.loggedIn && it.error != null))
    } ?: ForumSignState()
    fun isForumSigned(name: String): Boolean = forumSignState.takeIf { it.owner == account.uid && it.day == signDay() }
        ?.entries?.any { it.forum.name == name && it.status in setOf(ForumSignStatus.Signed, ForumSignStatus.AlreadySigned) } == true

    private fun reflectSignedForum(name: String) {
        if (forum == name) feed = feed.copy(info = feed.info?.copy(signed = true))
        returns = returns.map { previous ->
            if (previous.forum == name) previous.copy(feed = previous.feed.copy(info = previous.feed.info?.copy(signed = true))) else previous
        }
    }

    fun signForum(name: String) {
        val owner = account.uid
        val session = BaiduSession.cookie("BDUSS")
        var result = ForumSignResult.Signed
        mutate("sign:$name", {
            val token = api.tbs(owner)
            if (BaiduSession.cookie("BDUSS") != session) throw LoginExpiredException()
            result = api.signForum(name, token)
        }) {
            val previous = forumSignState.takeIf { it.owner == account.uid && it.day == signDay() } ?: ForumSignState(owner = account.uid, day = signDay())
            val status = if (result == ForumSignResult.Signed) ForumSignStatus.Signed else ForumSignStatus.AlreadySigned
            val entries = if (previous.entries.any { it.forum.name == name }) previous.entries.map { if (it.forum.name == name) it.copy(status = status, message = "") else it }
                else previous.entries + ForumSignEntry(ForumItem("", name), status)
            forumSignState = previous.copy(entries = entries)
            reflectSignedForum(name)
            messages.tryEmit(if (result == ForumSignResult.Signed) "${name}吧签到成功" else "${name}吧今日已签到")
        }
    }

    fun stopForumSignIn() {
        if (forumSignState.running) forumSignState = forumSignState.copy(stopRequested = true)
    }

    fun startForumSignIn(retryFailed: Boolean = false, resume: Boolean = false) {
        if (pending != null || !requireAccount()) return
        val owner = account.uid
        val day = signDay()
        val session = BaiduSession.cookie("BDUSS")
        val previous = forumSignState.takeIf { it.owner == owner && it.day == day } ?: ForumSignState(owner = owner, day = day)
        if (retryFailed && previous.failed == 0) return
        if (resume && previous.waiting == 0) return
        pending = "sign-all"
        forumSignState = previous.copy(running = true, loading = true, stopRequested = false, stopped = false, error = null)
        interactionJob = viewModelScope.launch {
            try {
                val forums = api.followedForums(owner)
                if (forumSignState.stopRequested) { forumSignState = forumSignState.copy(stopped = true); return@launch }
                val mode = if (retryFailed) ForumSignMode.Failed else if (resume) ForumSignMode.Remaining else ForumSignMode.All
                val selected = prepareForumSigns(forums, previous.entries, mode)
                val selectedByName = selected.associateBy { it.forum.name }
                val old = previous.entries.associateBy { it.forum.name }
                val entries = forums.map { selectedByName[it.name] ?: old[it.name]?.copy(forum = it) ?: ForumSignEntry(it) }
                forumSignState = forumSignState.copy(loading = false, entries = entries)
                if (selected.none { it.status == ForumSignStatus.Waiting }) {
                    messages.tryEmit(if (forums.isEmpty()) "没有可签到的关注吧" else "本次没有待签到的吧")
                    return@launch
                }
                val token = api.tbs(owner)
                val result = runForumSignBatch(selected,
                    shouldStop = { forumSignState.stopRequested || account.uid != owner || signDay() != day },
                    sign = { item ->
                        if (BaiduSession.cookie("BDUSS") != session) throw LoginExpiredException()
                        api.signForum(item.name, token)
                    }, describe = ::friendly,
                    onProgress = { updates ->
                        val byName = updates.associateBy { it.forum.name }
                        forumSignState = forumSignState.copy(entries = entries.map { byName[it.forum.name] ?: it })
                        updates.filter { it.status in setOf(ForumSignStatus.Signed, ForumSignStatus.AlreadySigned) }.forEach { reflectSignedForum(it.forum.name) }
                    })
                result.error?.let(::expireIfNeeded)
                forumSignState = forumSignState.copy(stopped = result.stopped, error = result.error?.let(::friendly))
                messages.tryEmit(result.error?.let(::friendly) ?: if (result.stopped) "签到已停止，已完成的记录保留" else "签到结束：成功${forumSignState.signed}，已签到${forumSignState.alreadySigned}，失败${forumSignState.failed}")
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                expireIfNeeded(e)
                forumSignState = forumSignState.copy(error = friendly(e))
                messages.tryEmit(friendly(e))
            } finally {
                forumSignState = forumSignState.copy(running = false, loading = false,
                    entries = forumSignState.entries.map { if (it.status == ForumSignStatus.Signing) it.copy(status = ForumSignStatus.Failed, message = "结果未确认，请查看该吧后重试") else it })
                pending = null
            }
        }
    }
    fun removeHistory(id: String) { store.removeHistory(id); history = store.history() }
    fun clearHistory() { store.clearHistory(); history = emptyList() }
    fun editPublished(thread: ThreadItem) {
        officialThread(thread.id)
        messages.tryEmit("已发布内容的编辑权限由贴吧决定，请在原帖管理菜单查看")
    }

    fun openPublishing() {
        val owner = account.uid.ifBlank { "guest" }
        publishing = PublishingDraft.decode(owner, store.draft("publish:$owner"), forum)
        if (panel != null || reading != null) rememberSurface("panel")
        reading = null; panel = PanelState(System.nanoTime(), PanelKind.Publish, owner, loading = false)
    }
    fun editPublishing(forum: String? = null, title: String? = null, text: String? = null, images: List<DraftImage>? = null) {
        val draft = publishing ?: return
        if (draft.sending) return
        publishing = draft.copy(forum = (forum ?: draft.forum).take(50), title = (title ?: draft.title).take(90), text = (text ?: draft.text).take(10000), images = (images ?: draft.images).distinctBy { it.uri }.take(9), error = null)
        savePublishingDraft()
    }
    private fun savePublishingDraft() { publishing?.let { store.saveDraft("publish:${it.owner}", it.encode()) } }
    fun acknowledgePublishResult() { publishing = publishing?.copy(uncertain = false, error = null); savePublishingDraft() }
    fun publishThread() {
        val draft = publishing ?: return
        if (draft.sending || draft.uncertain || pending != null || !requireAccount()) return
        if (draft.owner != account.uid) { publishing = draft.copy(error = "账号已变化，请保存关闭后重新打开编辑器"); return }
        if (draft.forum.isBlank() || draft.title.isBlank() || (draft.text.isBlank() && draft.images.isEmpty())) return
        pending = "publish"; publishing = draft.copy(sending = true, progress = "确认发布吧…", error = null)
        interactionJob = viewModelScope.launch {
            var submitted = false
            try {
                val forum = api.forum(draft.forum.trim().removeSuffix("吧"), 1)
                if (forum.forumId.isBlank()) throw IOException("未获取到吧编号，尚未发布")
                val images = draft.images.toMutableList()
                images.indices.forEach { index ->
                    if (images[index].code.isBlank()) {
                        publishing = publishing?.copy(progress = "上传图片 ${index + 1}/${images.size}")
                        images[index] = images[index].copy(code = api.uploadImage(getApplication(), images[index].uri))
                        publishing = publishing?.copy(images = images.toList()); savePublishingDraft()
                    }
                }
                publishing = publishing?.copy(progress = "正在提交帖子…", uncertain = true); savePublishingDraft()
                submitted = true
                val id = api.publish(forum.name, forum.forumId, draft.title.trim(), draft.text.trim() + images.joinToString("", prefix = if (images.isEmpty()) "" else "\n") { it.code })
                store.saveDraft("publish:${draft.owner}", ""); publishing = null
                closePanel(); openThread(id)
                messages.tryEmit("帖子已提交，内容可能需要审核")
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                expireIfNeeded(e)
                val uncertain = submitted && e !is TiebaRejectedException && e !is CommentPreparationException && e !is IllegalArgumentException
                publishing = publishing?.copy(sending = false, progress = "", uncertain = uncertain,
                    error = friendly(e) + if (uncertain) "。草稿已保留，请先查看我的帖子核对结果" else "。草稿已保留")
                savePublishingDraft()
            } finally { pending = null }
        }
    }

    fun openFloor(post: PostItem) {
        closeFloor()
        floor = FloorState(post)
        loadFloor()
    }

    private fun openFloorAt(post: PostItem, targetId: String) {
        closeFloor()
        floor = FloorState(post, targetPostId = targetId)
        floorJob = viewModelScope.launch {
            try {
                var page = 1
                var more = true
                var collected = emptyList<PostItem>()
                while (more && collected.none { it.id == targetId } && page <= 30) {
                    val result = api.floor(reading?.id ?: return@launch, post.id, page)
                    val fresh = result.posts.filter { incoming -> collected.none { it.id == incoming.id } }
                    collected = collected + fresh
                    more = result.hasMore && fresh.isNotEmpty()
                    floor = FloorState(post, collected, page, loading = more && collected.none { it.id == targetId }, hasMore = more, targetPostId = targetId)
                    page++
                }
                floor = floor?.copy(loading = false)
                if (collected.none { it.id == targetId }) messages.tryEmit("已定位所属楼层，目标楼中楼暂未找到，可继续加载")
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { floor = floor?.copy(loading = false, error = friendly(e)) }
        }
    }

    fun closeFloor() { floorJob?.cancel(); floor = null }

    fun loadFloor() {
        val threadId = reading?.id ?: return
        val current = floor ?: return
        if (floorJob?.isActive == true || !current.hasMore) return
        floor = current.copy(loading = true, error = null)
        floorJob = viewModelScope.launch {
            try {
                val result = api.floor(threadId, current.parent.id, current.page + 1)
                if (floor?.parent?.id != current.parent.id) return@launch
                floor = current.copy(posts = (current.posts + result.posts).distinctBy { it.id }, page = result.page,
                    hasMore = result.hasMore && result.posts.any { incoming -> current.posts.none { it.id == incoming.id } }, loading = false)
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) { floor = current.copy(loading = false, error = friendly(error)) }
        }
    }

    fun loadReplies(refresh: Boolean = false) {
        val current = reading ?: return
        if (!refresh && (current.loading || current.loadingMore || !current.hasMore)) return
        readingJob?.cancel()
        val page = if (refresh) 1 else current.page + 1
        reading = current.copy(loading = refresh, loadingMore = !refresh, error = null)
        readingJob = viewModelScope.launch {
            try {
                val result = api.detail(current.id, page, current.onlyAuthor, sort = current.sort)
                if (reading?.id != current.id) return@launch
                val summary = if (page == 1) result.thread else reading?.summary ?: result.thread
                fun updateSummary(item: ThreadItem) = if (item.id == summary.id) summary.copy(images = summary.images.ifEmpty { item.images }, excerpt = summary.excerpt.ifBlank { item.excerpt }) else item
                stream = stream.copy(threads = stream.threads.map(::updateSummary))
                feed = feed.copy(threads = feed.threads.map(::updateSummary))
                if (page == 1) { store.rememberThread(summary); history = store.history() }
                reading = ReadingState(current.id, summary,
                    (if (refresh) result.posts else current.posts + result.posts).distinctBy { it.id }, result.page,
                    result.hasMore && (refresh || result.posts.any { incoming -> current.posts.none { it.id == incoming.id } }), loading = false, onlyAuthor = current.onlyAuthor,
                    positionRequest=current.positionRequest,targetPostId=current.targetPostId,
                    firstLoadedPage=if (refresh) 1 else current.firstLoadedPage, sort = current.sort)
                enrichAuthor(summary)
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                if (reading?.id == current.id) reading = reading?.copy(loading = false, loadingMore = false, error = friendly(error))
            }
        }
    }

    private fun enrichAuthor(thread: ThreadItem) {
        if (thread.author.id.isBlank() || thread.author.levelName.isNotBlank() || thread.forumId.isBlank()) return
        identityJob?.cancel()
        identityJob = viewModelScope.launch {
            try {
                val key = thread.forumId to thread.author.id
                val person = identities[key] ?: api.forumIdentity(thread.author, thread.forumId).also { identities[key] = it }
                reading?.takeIf { it.id == thread.id }?.let { current ->
                    reading = current.copy(summary = current.summary?.copy(author = person), posts = current.posts.map { if (it.author.id == person.id) it.copy(author = person) else it })
                }
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { /* Optional identity metadata never blocks reading. */ }
        }
    }

    fun toggleBookmark(thread: ThreadItem) {
        val exists = bookmarks.any { it.id == thread.id }
        if (!exists && bookmarks.size >= 1000) { messages.tryEmit("本地收藏已满，请先移除一些帖子"); return }
        bookmarks = if (exists) bookmarks.filterNot { it.id == thread.id } else listOf(thread) + bookmarks
        store.saveBookmarks(bookmarks)
        messages.tryEmit(if (exists) "已移出本地收藏" else "已保存到本地收藏")
    }

    fun setThemeMode(value: ThemeMode) { theme = value; store.theme = value }
    fun changeVisualStyle(value: VisualStyle) {
        store.customThemeId = null; customTheme = null
        store.visualStyle = value
        visualStyle = value
        accentColor = store.accentColor
    }
    fun changeFont(value: AppFont) { store.appFont = value; appFont = value }
    fun chooseCustomTheme(pack: ThemePack) {
        customTheme = pack; store.customThemeId = pack.id; accentColor = store.customAccent(pack)
    }
    fun dismissThemePreview() { themeImportPreview = null }
    fun dismissThemeError() { themeError = null }
    fun importTheme(uri: Uri) {
        if (themeBusy) return
        themeBusy = true; themeError = null
        viewModelScope.launch {
            try {
                themeImportPreview = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use(ThemePackCodec::read)
                        ?: error("无法读取主题文件，请从系统文件选择器重新选择")
                }
            } catch(e: CancellationException) { throw e
            } catch(e: Exception) { themeError = e.message ?: "无法读取主题包"
            } finally { themeBusy = false }
        }
    }
    fun saveImportedTheme() {
        val pack = themeImportPreview ?: return
        if (themeBusy) return
        themeBusy = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { themeStore.save(pack) }
                customThemes = withContext(Dispatchers.IO) { themeStore.all() }
                chooseCustomTheme(pack); themeImportPreview = null; messages.tryEmit("已导入并应用 ${pack.name}")
            } catch(e: CancellationException) { throw e
            } catch(e: Exception) { themeError = e.message ?: "无法保存主题"
            } finally { themeBusy = false }
        }
    }
    fun deleteTheme(pack: ThemePack) {
        if (themeBusy) return
        themeBusy = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { themeStore.delete(pack.id) }
                if (customTheme?.id == pack.id) changeVisualStyle(visualStyle)
                store.forgetThemeAccent(pack.id)
                customThemes = withContext(Dispatchers.IO) { themeStore.all() }
            } catch(e: CancellationException) { throw e
            } catch(e: Exception) { themeError = e.message ?: "无法删除主题"
            } finally { themeBusy = false }
        }
    }
    fun exportThemeExample(uri: Uri) {
        if (themeBusy) return
        themeBusy = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(ThemePackCodec.encode(ThemePackCodec.example)) }
                        ?: error("无法写入所选位置")
                }
                messages.tryEmit("示例主题已导出")
            } catch(e: CancellationException) { throw e
            } catch(e: Exception) { themeError = e.message ?: "导出失败"
            } finally { themeBusy = false }
        }
    }
    fun setLargeReading(value: Boolean) { largeText = value; store.largeText = value }
    fun changeTextScale(value: Float) { store.textScale = value; textScale = store.textScale }
    fun changeInterfaceScale(value: Float) { store.interfaceScale = value; interfaceScale = store.interfaceScale }
    fun changeRichEffects(value: Boolean) { richEffects = value; store.richEffects = value }
    fun changeAccent(value: String) {
        val hex = value.trim().removePrefix("#").uppercase()
        if (!Regex("[0-9A-F]{6}").matches(hex)) return
        accentColor = hex; store.accentColor = hex
    }
    fun changeReduceMotion(value: Boolean) { reduceMotion = value; store.reduceMotion = value }
    fun changeAutoHideNavigation(value: Boolean) { autoHideNavigation = value; store.autoHideNavigation = value }

    fun refreshAccount() {
        if (accountJob?.isActive == true || pending != null) return
        val previous = account
        account = account.copy(loading = true, error = null)
        accountJob = viewModelScope.launch {
            try {
                val result = api.account()
                account = result
                if (previous.uid != result.uid || previous.loggedIn != result.loggedIn) {
                    chatJob?.cancel(); groupChats = GroupChatsState(); chats.bind(AccountState())
                    forumSignState = ForumSignState()
                    followingJob?.cancel(); following = FeedState()
                    panelJob?.cancel(); panel = null; returns = emptyList()
                    streamJob?.cancel(); stream = FeedState(); loadStream(true)
                    discoveryJob?.cancel(); discovery = DiscoveryPage(emptyList(), emptyList()); loadDiscovery()
                    if (forumOpen) loadForum(true)
                    if (reading != null) loadReplies(true)
                }
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                account = AccountState(error = friendly(error))
            }
        }
    }

    fun logout() {
        if (pending != null) { messages.tryEmit("请等待当前操作结束"); return }
        accountJob?.cancel()
        closeComposer()
        BaiduSession.clear {
            chatJob?.cancel(); groupChats = GroupChatsState(); chats.bind(AccountState())
            account = AccountState()
            forumSignState = ForumSignState()
            followingJob?.cancel(); following = FeedState()
            panelJob?.cancel(); panel = null; returns = emptyList()
            streamJob?.cancel(); stream = FeedState(); loadStream(true)
            discoveryJob?.cancel(); discovery = DiscoveryPage(emptyList(), emptyList()); loadDiscovery()
            loadForum(true)
            closeFloor()
            if (reading != null) loadReplies(true)
            messages.tryEmit("已退出当前设备的百度账号")
        }
    }

    private fun requireAccount(): Boolean {
        if (account.loading) { messages.tryEmit("正在确认账号状态，请稍候"); return false }
        if (!account.loggedIn) { login(); return false }
        return true
    }

    fun like(thread: ThreadItem, post: PostItem? = null) {
        if (pending != null || !requireAccount()) return
        val key = "like:${thread.id}:${post?.id.orEmpty()}"
        pending = key
        interactionJob = viewModelScope.launch {
            try {
                val reference = if (post == null && !thread.likesKnown) api.detail(thread.id, 1).thread else thread
                val liked = post?.liked ?: reference.liked
                val fid = reference.forumId.ifBlank { api.detail(thread.id, 1).thread.forumId }
                api.likePost(thread.id, post?.id.orEmpty(), liked, isComment = post?.floor == 0, forumId = fid)
                val delta = if (liked) -1 else 1
                if (post == null) {
                    fun update(item: ThreadItem) = if (item.id == thread.id) item.copy(liked = !liked, likes = (reference.likes + delta).coerceAtLeast(0), likesKnown = true) else item
                    feed = feed.copy(threads = feed.threads.map(::update))
                    stream = stream.copy(threads = stream.threads.map(::update))
                    following = following.copy(threads = following.threads.map(::update))
                    reading?.let { current -> reading = current.copy(summary = current.summary?.let(::update)) }
                    reading?.let { current -> reading = current.copy(posts = current.posts.map { if (it.floor == 1) it.copy(liked = !liked, likes = (reference.likes + delta).coerceAtLeast(0)) else it }) }
                    panel = panel?.copy(items = panel!!.items.map { it.copy(thread = update(it.thread)) }, results = panel!!.results.copy(threads = panel!!.results.threads.map(::update)))
                } else {
                    reading?.let { current -> reading = current.copy(posts = current.posts.map { if (it.id == post.id) it.copy(liked = !liked, likes = (it.likes + delta).coerceAtLeast(0)) else it }) }
                    floor?.let { current -> floor = current.copy(
                        parent = if (current.parent.id == post.id) current.parent.copy(liked = !liked, likes = (current.parent.likes + delta).coerceAtLeast(0)) else current.parent,
                        posts = current.posts.map { if (it.id == post.id) it.copy(liked = !liked, likes = (it.likes + delta).coerceAtLeast(0)) else it }) }
                }
                // The selected button and server-confirmed count already
                // acknowledge success; don't interrupt reading with a snackbar.
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) { expireIfNeeded(error); interactionError = friendly(error)
            } finally { pending = null }
        }
    }

    fun followForum() {
        if (pending != null || feed.loading || !requireAccount()) return
        val target = forum
        val fid = feed.forumId
        val undo = feed.followed
        if (fid.isBlank()) { messages.tryEmit("请等待吧信息加载完成"); return }
        pending = "follow:$fid"
        interactionJob = viewModelScope.launch {
            try {
                api.followForum(fid, target, undo)
                if (forum == target) feed = feed.copy(followed = !undo)
                messages.tryEmit(if (undo) "已取消关注${target}吧" else "已关注${target}吧")
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) { expireIfNeeded(error); messages.tryEmit(friendly(error))
            } finally { pending = null }
        }
    }

    fun openComposer(parent: PostItem? = null, target: PostItem? = null) {
        val thread = reading?.summary ?: return
        if (parent != null && parent.floor <= 0) {
            if (pending != null) return
            val owner = account.uid
            pending = "resolve-reply:${parent.id}"
            interactionJob = viewModelScope.launch {
                try {
                    val resolved = api.resolveReplyParent(thread.id, parent)
                    if (reading?.id != thread.id || account.uid != owner) return@launch
                    reading = reading?.let { state -> state.copy(posts = state.posts.map { if (it.id == resolved.id) it.copy(floor = resolved.floor) else it }) }
                    floor = floor?.let { state -> if (state.parent.id == resolved.id) state.copy(parent = resolved) else state }
                    openComposer(resolved, target)
                } catch (error: CancellationException) { throw error
                } catch (error: Exception) { messages.tryEmit(friendly(error))
                } finally { pending = null }
            }
            return
        }
        val state = ComposerState(thread, parent, owner = account.uid.ifBlank { "guest" }, target = target)
        composerFloorReturn = floor
        closeFloor()
        val uncertain = store.draftUncertain(state.draftKey)
        composer = state.copy(text = store.draft(state.draftKey), uncertain = uncertain,
            error = if (uncertain) "上次发送结果未确认，请先查看原帖，避免重复发送" else null)
    }

    fun editComment(value: String) {
        val current = composer ?: return
        if (current.sending || value.length > 10000) return
        composer = current.copy(text = value, error = null)
        store.saveDraft(current.draftKey, value)
    }

    fun closeComposer() {
        val current = composer ?: return
        if (current.sending) return
        store.saveDraft(current.draftKey, current.text)
        composer = null
        composerFloorReturn?.takeIf { reading?.id == current.thread.id }?.let { previous ->
            floor = previous.copy(loading = false)
            // Opening the editor cancels the floor request. Resume it instead of
            // restoring an orphaned loading flag with no job behind it.
            if (previous.loading) loadFloor()
        }
        composerFloorReturn = null
    }

    fun acknowledgeCommentResult() {
        val current = composer ?: return
        store.setDraftUncertain(current.draftKey, false)
        composer = current.copy(uncertain = false, error = null)
    }

    fun sendComment() {
        val current = composer ?: return
        if (pending != null || current.sending || current.uncertain || current.text.isBlank() || !requireAccount()) return
        if (current.owner != account.uid && current.owner != "guest") {
            composer = current.copy(error = "账号已切换，请关闭后重新打开回复框")
            return
        }
        pending = "comment:${current.thread.id}"
        composer = current.copy(sending = true, error = null)
        store.setDraftUncertain(current.draftKey, true)
        interactionJob = viewModelScope.launch {
            var requestStarted = false
            try {
                val fid = current.thread.forumId.ifBlank { api.forum(current.thread.forum, 1).forumId }
                requestStarted = true
                api.comment(fid, current.thread.forum, current.thread.id, current.parent, current.text, current.target)
                store.saveDraft(current.draftKey, "")
                store.setDraftUncertain(current.draftKey, false)
                composer = null
                composerFloorReturn = null
                messages.tryEmit("评论已提交，请刷新查看；内容可能需要审核")
                if (reading?.id == current.thread.id) loadReplies(true)
                closeFloor()
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                expireIfNeeded(error)
                val uncertain = requestStarted && error !is TiebaRejectedException && error !is CommentPreparationException &&
                    error !is LoginExpiredException && error !is IllegalArgumentException
                store.setDraftUncertain(current.draftKey, uncertain)
                composer = current.copy(error = friendly(error) + if (uncertain) "。草稿已保留，请先查看原帖确认结果" else "。草稿已保留", uncertain = uncertain)
            } finally { pending = null }
        }
    }
    fun officialThread(id: String) { webUrl = "https://tieba.baidu.com/p/$id" }
    fun login() { webUrl = "https://wappass.baidu.com/passport/?login&tpl=tb&u=https%3A%2F%2Ftieba.baidu.com%2F" }

    private fun expireIfNeeded(error: Exception) {
        if (error is LoginExpiredException || error.cause is LoginExpiredException) account = AccountState(error = error.message)
    }

    private fun friendly(error: Exception): String = when (error) {
        is UnknownHostException -> "网络暂不可用，请检查连接后重试"
        is SocketTimeoutException -> "加载超时了，请再试一次"
        is IOException -> error.message?.takeIf { it.any { character -> character.code > 127 } } ?: "连接没有完成，请稍后重试"
        is IllegalArgumentException -> error.message?.take(160) ?: "请检查输入后重试"
        else -> "这次没能加载成功，请重试"
    }
}
