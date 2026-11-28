package dev.hydrogen.tieba.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hydrogen.tieba.data.ForumItem
import dev.hydrogen.tieba.data.Person
import dev.hydrogen.tieba.data.ThreadItem
import dev.hydrogen.tieba.data.IdentitySurface
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun ScreenHeading(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(start = if (onBack == null) 20.dp else 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) BubbleIconButton(onClick = onBack) { ThemeIcon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

@Composable
private fun AutoPage(scroll: LazyListState, feed: FeedState, next: () -> Unit) {
    LaunchedEffect(scroll, feed.page, feed.error, feed.loading, feed.loadingMore) {
        snapshotFlow { scroll.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.distinctUntilChanged().collect {
            if (feed.threads.isNotEmpty() && !feed.loading && !feed.loadingMore && feed.error == null && it >= scroll.layoutInfo.totalItemsCount - 3) next()
        }
    }
}

@Composable
fun FeedThread(model: TiebaViewModel, thread: ThreadItem, onImage: (dev.hydrogen.tieba.data.ImageGallery) -> Unit, onRemove: (() -> Unit)? = null, onOpen: (() -> Unit)? = null, filtering: Boolean = false, recommendation: Boolean = false) {
    ThreadCard(thread, model.bookmarks.any { it.id == thread.id }, onOpen = onOpen ?: { model.openThread(thread.id, thread) },
        onSave = { model.toggleBookmark(thread) }, onImage = onImage, onLike = { model.like(thread) },
        busy = model.pending != null, liking = model.pending == "like:${thread.id}:", cloudSaving = model.pending == "cloud:${thread.id}",
        onForum = { model.openForum(thread.forum) }, onAuthor = { model.openUser(thread.author) }, onCloud = { model.cloudSave(thread) }, onRemove = onRemove,
        onHide = if (filtering) ({ model.hideThread(thread) }) else null,
        onBlockAuthor = if (filtering && thread.author.id.isNotBlank()) ({ model.blockAuthor(thread.author) }) else null,
        onBlockForum = if (filtering && thread.forum.isNotBlank()) ({ model.blockForum(thread.forum) }) else null,
        excerptLines = if (recommendation) model.recommendationLines else 2,
        mediaFraction = if (recommendation) model.recommendationMediaPercent / 100f else 1f,
        compactHeader = recommendation)
}

@Composable
fun StreamScreen(model: TiebaViewModel, modifier: Modifier, scroll: LazyListState, onSearch: () -> Unit, onImage: (dev.hydrogen.tieba.data.ImageGallery) -> Unit, autoHideHeader: Boolean = true) {
    val following = model.followingSelected
    val feed = if (following) model.following else model.stream
    val load: (Boolean) -> Unit = { refresh -> if (following) model.loadFollowing(refresh) else model.loadStream(refresh) }
    val header = rememberBrowsingHeaderState(autoHideHeader)
    Column(modifier.nestedScroll(header.connection)) {
        BrowsingHeader(header) {
        ScreenHeading("氢贴吧", actions = {
            BubbleIconButton(onClick = onSearch, visualSize = 44.dp) { ThemeIcon(Icons.Outlined.Search, "搜索吧名或帖子链接", Modifier.size(28.dp)) }
            BubbleIconButton(onClick = { load(true) }, enabled = !feed.loading, visualSize = 44.dp) { ThemeIcon(Icons.Outlined.Refresh, if (following) "刷新关注动态" else "刷新推荐", Modifier.size(28.dp)) }
        })
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("推荐", "关注").forEachIndexed { index, label ->
                val active = following == (index == 1)
                Column(Modifier.padding(horizontal = 3.dp, vertical = 3.dp).widthIn(min = 74.dp).bubbleBackground(selected = active).clickable { model.selectFeed(index == 1) }.padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 13.dp))
                    Box(Modifier.size(22.dp, 3.dp).clip(CircleShape).background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent))
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
        }
        val pane=FeedPane(following,feed,scroll)
        val motion by rememberUpdatedState(LocalMotionEnabled.current)
        AnimatedContent(targetState=pane,contentKey={ it.following },transitionSpec={
            if(!motion) EnterTransition.None togetherWith ExitTransition.None
            else fadeIn(tween(HydrogenMotion.TabDuration,easing=HydrogenMotion.EaseOut)) togetherWith fadeOut(tween(HydrogenMotion.ExitDuration))
        },label="推荐关注衔接") { pane ->
            val feed=pane.feed
            val scroll=pane.scroll
            val following=pane.following
            val load: (Boolean) -> Unit = { refresh -> if(following) model.loadFollowing(refresh) else model.loadStream(refresh) }
            val visible = feed.threads.filterNot(model.contentFilters::hides)
            if (visible.isNotEmpty()) AutoPage(scroll, feed) { load(false) }
            RefreshableList(feed.loading, { load(true) }, hasContent = feed.threads.isNotEmpty()) {
            LazyColumn(state = scroll, contentPadding = PaddingValues(bottom = 132.dp)) {
                if (feed.error != null && feed.threads.isNotEmpty()) item { RefreshFailure(feed.error) { load(true) } }
                if (feed.loading && feed.threads.isEmpty()) item { LoadingRow(if (following) "正在加载关注动态" else "正在加载贴吧推荐") }
                if (!feed.loading && feed.threads.isEmpty()) item {
                    EmptyState(if (feed.error == null) { if (following) "暂时没有关注动态" else "暂时没有推荐内容" } else { if (following) "关注动态暂时无法加载" else "推荐暂时无法加载" },
                        feed.error ?: "百度贴吧接口本次未返回帖子。可以刷新，或去逛吧看看。", action = "刷新", onAction = { load(true) })
                    BubbleTextButton(onClick = { model.navigateTab(HomeTab.Browse) }, modifier = Modifier.fillMaxWidth().heightIn(min = LocalTouchTarget.current)) { Text("去逛吧") }
                }
                if (visible.isEmpty() && feed.threads.isNotEmpty()) item {
                    EmptyState("当前内容已被本机过滤", "可以加载下一页，或到设置 → 阅读与媒体 → 本机内容过滤管理。")
                }
                items(visible, key = { it.id }, contentType = { "thread" }) {
                    FeedThread(model, it, onImage, filtering = true, recommendation = !pane.following)
                }
                if (feed.threads.isNotEmpty()) item { PaginationFooter(feed.loadingMore, feed.error, feed.hasMore) { load(false) } }
            }
            }
        }
    }
}

@Composable
fun ForumScreen(model: TiebaViewModel, modifier: Modifier, scroll: LazyListState, onSearch: () -> Unit, onImage: (dev.hydrogen.tieba.data.ImageGallery) -> Unit, feed: FeedState = model.feed,
    forumName: String = model.forum, sort: dev.hydrogen.tieba.data.ForumSort = model.forumSort, autoHideHeader: Boolean = true) {
    val header = rememberBrowsingHeaderState(autoHideHeader)
    val visible = feed.threads.filterNot(model.contentFilters::hides)
    val pinned = visible.filter { it.pinned }
    var pinnedExpanded by rememberSaveable(forumName) { mutableStateOf(false) }
    Column(modifier.nestedScroll(header.connection)) {
        BrowsingHeader(header) {
        ScreenHeading("${forumName}吧", onBack = model::closeForum, actions = {
            BubbleIconButton(onClick = { model.forumInfoOpen = true }) { ThemeIcon(Icons.Outlined.Info, "查看吧资料") }
            BubbleIconButton(onClick = onSearch, visualSize = 44.dp) { ThemeIcon(Icons.Outlined.Search, "搜索贴吧", Modifier.size(28.dp)) }
            BubbleIconButton(onClick = { model.loadForum(true) }, enabled = !feed.loading, visualSize = 44.dp) { ThemeIcon(Icons.Outlined.Refresh, "刷新帖子", Modifier.size(28.dp)) }
        })
        }
        if (visible.isNotEmpty()) AutoPage(scroll, feed) { model.loadForum() }
        RefreshableList(feed.loading, { model.loadForum(true) }, hasContent = feed.threads.isNotEmpty()) {
        LazyColumn(state = scroll, contentPadding = PaddingValues(bottom = 110.dp)) {
            item {
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .5f)).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${forumName}吧", style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.heightIn(min = LocalTouchTarget.current).bubbleBackground().padding(horizontal = 8.dp).clickable { model.forumInfoOpen = true }.wrapContentHeight())
                        Text(if (feed.members > 0) "${compactNumber(feed.members)} 人关注 · ${sort.label}" else "来自百度贴吧的讨论",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 7.dp))
                    }
                    FilledTonalButton(onClick = model::followForum, enabled = model.pending == null && feed.forumId.isNotBlank() && !feed.loading,
                        modifier = Modifier.heightIn(min = LocalTouchTarget.current)) { Text(if (model.pending?.startsWith("follow:") == true) "处理中" else if (feed.followed) "已关注" else "关注") }
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    dev.hydrogen.tieba.data.ForumSort.entries.forEach { sort ->
                        BubbleFilterChip(selected = model.forumSort == sort, onClick = { model.changeForumSort(sort) }, label = { Text(sort.label) }, modifier = Modifier.heightIn(min = LocalTouchTarget.current))
                    }
                }
            }
            if (feed.error != null && feed.threads.isNotEmpty()) item { RefreshFailure(feed.error) { model.loadForum(true) } }
            if (feed.loading && feed.threads.isEmpty()) item { LoadingRow() }
            if (!feed.loading && feed.threads.isEmpty()) item { EmptyState("暂时没有内容", feed.error ?: "稍后再来看看", action = "重新加载", onAction = { model.loadForum(true) }) }
            if (pinned.isNotEmpty()) item(key = "pinned-toggle") {
                BubbleTextButton(onClick = { pinnedExpanded = !pinnedExpanded }, modifier = Modifier.fillMaxWidth()) {
                    Text("置顶 ${pinned.size} 篇 · ${if (pinnedExpanded) "收起" else "展开"}")
                }
            }
            if (pinnedExpanded) items(pinned, key = { it.id }, contentType = { "thread" }) { FeedThread(model, it, onImage, filtering = true) }
            if (visible.isEmpty() && feed.threads.isNotEmpty()) item {
                EmptyState("当前内容已被本机过滤", "可以加载下一页，或到阅读设置中取消屏蔽。")
            }
            items(visible.filterNot { it.pinned }, key = { it.id }, contentType = { "thread" }) { FeedThread(model, it, onImage, filtering = true) }
            if (feed.threads.isNotEmpty()) item { PaginationFooter(feed.loadingMore, feed.error, feed.hasMore) { model.loadForum() } }
        }
        }
    }
}

@Composable
fun ForumRow(forum: ForumItem, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().navigationBubble().clickable(onClick = onOpen).padding(horizontal = 20.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        PersonAvatar(Person(name = forum.name, avatar = forum.avatar), Modifier.size(49.dp))
        Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
            Text("${forum.name}吧", style = MaterialTheme.typography.titleMedium)
            if (forum.description.isNotBlank()) Text(forum.description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        ThemeIcon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun DiscoverScreen(model: TiebaViewModel, modifier: Modifier, scroll: LazyListState, onSearch: () -> Unit, autoHideHeader: Boolean = true) {
    val header = rememberBrowsingHeaderState(autoHideHeader)
    Column(modifier.nestedScroll(header.connection)) {
        BrowsingHeader(header) {
        ScreenHeading("逛吧", actions = { BubbleIconButton(onClick = model::loadDiscovery, enabled = !model.discovering, visualSize = 44.dp) { ThemeIcon(Icons.Outlined.Refresh, "刷新关注和推荐吧", Modifier.size(28.dp)) } })
        }
        RefreshableList(model.discovering, model::loadDiscovery, hasContent = model.discovery.followed.isNotEmpty() || model.discovery.recommended.isNotEmpty()) {
        LazyColumn(state = scroll, contentPadding = PaddingValues(bottom = 132.dp)) {
            item {
                Surface(onClick = onSearch, shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Row(Modifier.padding(15.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        ThemeIcon(Icons.Outlined.Search, null, Modifier.size(21.dp)); Text("搜索感兴趣的吧 / 打开帖子", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                ForumSignInPanel(model)
                SectionLabel("我关注的吧")
            }
            if (model.discovering && model.discovery.followed.isEmpty() && model.discovery.recommended.isEmpty()) item { LoadingRow("正在同步关注的吧") }
            items(model.discovery.followed, key = { "follow:${it.id}:${it.name}" }) { ForumRow(it) { model.openForum(it.name) } }
            if (!model.discovering && model.discovery.followed.isEmpty()) item {
                Text(if (model.account.loggedIn) "贴吧接口暂未返回关注的吧" else "登录百度账号后同步关注的吧",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
                if (!model.account.loggedIn) BubbleTextButton(onClick = model::login, modifier = Modifier.padding(horizontal = 10.dp).heightIn(min = LocalTouchTarget.current)) { Text("登录百度账号") }
            }
            item {
                SectionLabel("最近浏览")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    model.recentForums.forEach { name -> BubbleAssistChip(onClick = { model.openForum(name) }, label = { Text(name) }, modifier = Modifier.heightIn(min = LocalTouchTarget.current)) }
                }
                SectionLabel("推荐吧")
            }
            items(model.discovery.recommended, key = { "recommend:${it.id}:${it.name}" }) { ForumRow(it) { model.openForum(it.name) } }
            if (!model.discovering && model.discovery.recommended.isEmpty()) item {
                Text("贴吧接口本次没有返回推荐社区", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
            }
            model.discoveryError?.let { error -> item { EmptyState("同步没有完成", error, action = "重试", onAction = model::loadDiscovery) } }
        }
        }
    }
}

@Composable
fun SectionLabel(label: String) {
    Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp))
}

@Composable
fun MenuRow(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().navigationBubble().clickable(onClick = onClick).heightIn(min = 62.dp).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        ThemeIcon(icon, null, Modifier.size(23.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ThemeIcon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ProfileScreen(model: TiebaViewModel, modifier: Modifier) {
    var coverMenu by remember { mutableStateOf(false) }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(model::replaceProfileCover)
    }
    val localCover = model.localProfileCover
    ProfileCanvas(localCover.ifBlank { model.account.profile?.backgroundImage.orEmpty() }, modifier) {
    Column(Modifier.fillMaxSize()) {
        // A single settings entry, in the list below. The header has no duplicate.
        ScreenHeading("我的", actions = {
            Box {
                BubbleIconButton(onClick = { coverMenu = true }, enabled = !model.profileCoverBusy) {
                    if (model.profileCoverBusy) AppCircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else ThemeIcon(Icons.Outlined.Wallpaper, "更换主页背景")
                }
                BubbleDropdownMenu(coverMenu, { coverMenu = false }) {
                    BubbleMenuItem(text = { Text("选择本机背景图片") }, onClick = {
                        coverMenu = false; coverPicker.launch(photoPickerRequest())
                    })
                    BubbleMenuItem(text = { Text("恢复贴吧主页背景") }, enabled = localCover.isNotBlank(), onClick = {
                        coverMenu = false; model.resetProfileCover()
                    })
                }
            }
        })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 132.dp)) {
            Column(Modifier.padding(horizontal = 22.dp)) {
                val a = model.account
                ProfileIdentityRow(a, { model.openPanel(PanelKind.People) }, { model.openPanel(PanelKind.Fans) })
                Text(if (a.loading) "正在确认账号" else a.name.ifBlank { "连接熟悉的百度账号" }, style = MaterialTheme.typography.headlineSmall)
                PersonBadges(a.identity, IdentitySurface.Profile)
                Text(if (a.loggedIn) "百度贴吧账号 · 已连接" else "登录后同步你的贴吧日常", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 7.dp))
                a.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
                a.profileError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (a.loggedIn) {
                    if (a.profile?.introduction?.isNotBlank() == true) Text(a.profile.introduction, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                }
                if (!a.loggedIn) Button(onClick = model::login, enabled = !a.loading, modifier = Modifier.padding(top = 15.dp).heightIn(min = LocalTouchTarget.current)) { Text("百度账号登录") }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
                ProfileShortcut(Icons.Outlined.BookmarkBorder, "收藏", Modifier.weight(1f)) { if (model.account.loggedIn) model.openPanel(PanelKind.Cloud) else model.collection = "收藏" }
                ProfileShortcut(Icons.Outlined.ThumbUp, "点赞", Modifier.weight(1f)) { model.collection = "点赞" }
                ProfileShortcut(Icons.Outlined.History, "浏览历史", Modifier.weight(1f)) { model.collection = "浏览历史" }
                ProfileShortcut(Icons.Outlined.ChatBubbleOutline, "我的评论", Modifier.weight(1f)) { model.openPanel(PanelKind.Comments) }
            }
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
                ProfileShortcut(Icons.Outlined.Article, "我的帖子", Modifier.weight(1f)) { model.openPanel(PanelKind.Threads) }
                ProfileShortcut(Icons.Outlined.Edit, "编辑资料", Modifier.weight(1f)) { model.openPanel(PanelKind.EditProfile) }
                ProfileShortcut(Icons.Outlined.Forum, "管理我的吧", Modifier.weight(1f)) { model.openPanel(PanelKind.Forums) }
            }
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
            MenuRow(Icons.Outlined.Settings, "设置", "主题色、阅读与账号") { model.settingsOpen = true }
            Text("轻一点，离讨论近一点。", color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(24.dp))
        }
    }
    }
}

@Composable
private fun ProfileShortcut(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.padding(horizontal = 3.dp).bubbleBackground(RoundedCornerShape(26.dp)).clickable(onClick = onClick).heightIn(min = 64.dp).padding(horizontal = 2.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ThemeIcon(icon, null, Modifier.size(21.dp), tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp), maxLines = 2)
    }
}

@Composable
fun CollectionScreen(model: TiebaViewModel, type: String, modifier: Modifier, onImage: (dev.hydrogen.tieba.data.ImageGallery) -> Unit) {
    val isHistory = type == "浏览历史"
    val threads = if (isHistory) model.history else model.bookmarks
    var managing by remember(type) { mutableStateOf(false) }
    var selected by remember(type) { mutableStateOf(emptySet<String>()) }
    var confirmAll by remember(type) { mutableStateOf(false) }
    var confirmSelection by remember(type) { mutableStateOf<Set<String>?>(null) }
    val allIds = remember(threads) { threads.mapTo(mutableSetOf()) { it.id } }
    val currentSelection = selected intersect allIds
    val inSelection = isHistory && managing && threads.isNotEmpty()
    val exitSelection = { managing = false; selected = emptySet() }

    BackHandler(enabled = inSelection && model.pageRoute == "collection:$type", onBack = exitSelection)
    Column(modifier) {
        ScreenHeading(if (type == "收藏") "本地收藏" else type, onBack = {
            if (inSelection) exitSelection() else model.collection = null
        }, actions = {
            if (isHistory && threads.isNotEmpty()) {
                BubbleTextButton(onClick = { if (inSelection) exitSelection() else managing = true }) {
                    Text(if (inSelection) "完成" else "管理")
                }
            }
        })
        if (type == "收藏" && model.account.loggedIn) BubbleTextButton(onClick = { model.openPanel(PanelKind.Cloud) }) { Text("查看百度云收藏") }
        if (isHistory && threads.isNotEmpty()) {
            if (inSelection) {
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("已选 ${currentSelection.size} 项", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp))
                    BubbleTextButton(onClick = { selected = if (currentSelection == allIds) emptySet() else allIds }) {
                        Text(if (currentSelection == allIds) "取消全选" else "全选")
                    }
                    BubbleTextButton(enabled = currentSelection.isNotEmpty(), onClick = { confirmSelection = currentSelection }) {
                        Text("删除所选", color = if (currentSelection.isEmpty()) Color.Unspecified else MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                BubbleTextButton(onClick = { confirmAll = true }) { Text("清空本机浏览历史") }
            }
        }
        if (type == "点赞") {
            EmptyState("点赞记录尚在接入", "点赞操作仍使用百度贴吧接口。完整的账号点赞记录将在后续版本接入，不使用本地记录冒充。")
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
            if (threads.isEmpty()) item { EmptyState(if (type == "收藏") "把喜欢的讨论留下来" else "这里还没有浏览记录",
                if (type == "收藏") "点击帖子下方的书签，保存到这台设备。云收藏在百度账号中单独保存。" else "看过的帖子会留在这里。") }
            items(threads, key = { it.id }) { thread ->
                if (inSelection) {
                    HistorySelectionRow(thread, thread.id in currentSelection) { checked ->
                        selected = if (checked) currentSelection + thread.id else currentSelection - thread.id
                    }
                } else {
                    FeedThread(model, thread, onImage, if (isHistory) ({ model.removeHistory(thread.id) }) else null)
                }
            }
        }
    }
    if (confirmAll) {
        AlertDialog(onDismissRequest = { confirmAll = false }, title = { Text("清空本机浏览历史？") },
            text = { Text("清空后无法恢复，不影响百度账号上的记录。") },
            confirmButton = { BubbleTextButton(onClick = { model.clearHistory(); confirmAll = false }) { Text("清空") } },
            dismissButton = { BubbleTextButton(onClick = { confirmAll = false }) { Text("取消") } })
    }
    confirmSelection?.let { ids ->
        AlertDialog(onDismissRequest = { confirmSelection = null }, title = { Text("删除选中的 ${ids.size} 条浏览记录？") },
            text = { Text("这些记录会从本机浏览历史移除。") },
            confirmButton = { BubbleTextButton(onClick = { model.removeHistory(ids); confirmSelection = null; exitSelection() }) { Text("删除") } },
            dismissButton = { BubbleTextButton(onClick = { confirmSelection = null }) { Text("取消") } })
    }
}

@Composable
private fun HistorySelectionRow(thread: ThreadItem, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = LocalTouchTarget.current)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChecked)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Column(Modifier.weight(1f)) {
            Text(thread.title.ifBlank { thread.excerpt.ifBlank { "帖子 ${thread.id}" } },
                style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (thread.forum.isNotBlank()) Text("${thread.forum.removeSuffix("吧")}吧", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun MessagesScreen(model: TiebaViewModel, modifier: Modifier) {
    NativeInbox(model, modifier)
}

private data class FeedPane(val following: Boolean,val feed: FeedState,val scroll: LazyListState)
