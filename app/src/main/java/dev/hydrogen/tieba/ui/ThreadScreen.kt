package dev.hydrogen.tieba.ui

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import dev.hydrogen.tieba.data.PostItem
import dev.hydrogen.tieba.data.floorLabel
import dev.hydrogen.tieba.data.ThreadItem
import dev.hydrogen.tieba.data.IdentitySurface
import dev.hydrogen.tieba.data.VisualStyle
import dev.hydrogen.tieba.ui.theme.LocalVisualStyle
import androidx.compose.foundation.BorderStroke
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun ThreadScreen(model: TiebaViewModel, modifier: Modifier, onImage: (dev.hydrogen.tieba.data.ImageGallery) -> Unit, state: ReadingState? = model.reading) {
    val reading = state ?: return
    val scroll = key(reading.id) { rememberLazyListState() }
    val context = LocalContext.current
    var more by remember { mutableStateOf(false) }
    var jump by remember { mutableStateOf(false) }
    var pageText by remember { mutableStateOf("") }
    var consumedPositionRequest by rememberSaveable(reading.id) { mutableLongStateOf(0) }
    LaunchedEffect(reading.id, reading.positionRequest, reading.loading, reading.posts) {
        if (!reading.loading && reading.positionRequest > consumedPositionRequest) {
            val target = reading.posts.indexOfFirst { it.id == reading.targetPostId }
            if (reading.targetPostId.isNotBlank() && target < 0) return@LaunchedEffect
            val offset = 1 + (if (reading.summary != null) 1 else 0) + (if (reading.error != null && reading.posts.isNotEmpty()) 1 else 0)
            scroll.scrollToItem(if (target >= 0) target + offset else 0)
            consumedPositionRequest = reading.positionRequest
        }
    }
    Column(modifier) {
        if (model.pending?.startsWith("resolve-reply:") == true) {
            AppLinearProgressIndicator(Modifier.fillMaxWidth())
            Text("正在核对回复楼层…", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp))
        }
        Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BubbleIconButton(onClick = model::closeThread) { ThemeIcon(Icons.AutoMirrored.Outlined.ArrowBack, "返回列表") }
            Box(Modifier.weight(1f).heightIn(min = LocalTouchTarget.current).clip(RoundedCornerShape(22.dp))
                .clickable(enabled = !reading.summary?.forum.isNullOrBlank()) { reading.summary?.forum?.let(model::openForum) }, contentAlignment = Alignment.CenterStart) {
            Column(Modifier.bubbleBackground(RoundedCornerShape(22.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(reading.summary?.forum?.takeIf { it.isNotBlank() }?.let { "${it}吧" } ?: "帖子详情",
                    style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("点按查看社区", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            }
            Box {
                BubbleIconButton(onClick = { more = true }) { ThemeIcon(Icons.Outlined.MoreHoriz, "阅读与收藏选项") }
                BubbleDropdownMenu(more, { more = false }) {
                    BubbleMenuItem(text = { Text(if (reading.onlyAuthor) "查看全部楼层" else "只看楼主") }, onClick = { more = false; model.toggleOnlyAuthor() })
                    BubbleMenuItem(text = { Text("跳转到页码") }, onClick = { more = false; jump = true })
                    BubbleMenuItem(text = { Text(if (model.cloudSaved[reading.id] == true) "移出百度云收藏" else "收藏到百度账号") }, enabled = model.pending == null, onClick = { more = false; reading.summary?.let { model.cloudSave(it, model.cloudSaved[it.id] == true) } })
                }
            }
            BubbleIconButton(onClick = { model.loadReplies(true) }, enabled = !reading.loading, visualSize = 44.dp) { ThemeIcon(Icons.Outlined.Refresh, "刷新帖子", Modifier.size(28.dp)) }
            BubbleIconButton(onClick = {
                val share = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "${reading.summary?.title.orEmpty()} https://tieba.baidu.com/p/${reading.id}") }
                context.startActivity(Intent.createChooser(share, "分享帖子"))
            }) { ThemeIcon(Icons.Outlined.Share, "分享帖子") }
        }
        LaunchedEffect(scroll, reading.page, reading.error) {
            snapshotFlow { scroll.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.distinctUntilChanged().collect { position ->
                if (model.reading?.id == reading.id && model.reading?.posts?.isNotEmpty() == true && model.reading?.error == null &&
                    position >= scroll.layoutInfo.totalItemsCount - 3) model.loadReplies()
            }
        }
        RefreshableList(reading.loading, { model.loadReplies(true) }, hasContent = reading.posts.isNotEmpty()) {
        LazyColumn(state = scroll, contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 132.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)) {
            if (reading.error != null && reading.posts.isNotEmpty()) item(key = "refresh-error") { RefreshFailure(reading.error) { model.loadReplies(true) } }
            reading.summary?.let { thread -> item(key = "heading") {
                Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
                    Text(thread.title, style = MaterialTheme.typography.titleLarge)
                    Text("${compactNumber(thread.replies.toLong())} 条评论 · ${compactNumber(thread.likes.toLong())} 个赞 · ${thread.views?.let(::compactNumber) ?: "—"} 阅读",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp))
                }
            } }
            item(key = "reply-sort") {
                FlowRow(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    dev.hydrogen.tieba.data.ReplySort.entries.forEach { sort ->
                        BubbleFilterChip(selected = reading.sort == sort, onClick = { model.changeReplySort(sort) },
                            label = { Text(sort.label) })
                    }
                }
            }
            if (reading.loading && reading.posts.isEmpty()) item(key = "loading") { LoadingRow("正在打开帖子") }
            if (!reading.loading && reading.posts.isEmpty()) item(key = "empty") {
                EmptyState("暂时无法阅读", reading.error ?: "这个帖子暂时没有可显示的内容", action = "重试", onAction = { model.loadReplies(true) })
                BubbleTextButton(onClick = { model.officialThread(reading.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = LocalTouchTarget.current)) { Text("在原帖查看") }
            }
            items(reading.posts, key = { it.id }, contentType = { "post" }) { post ->
                if (post.id == reading.targetPostId) Text("消息所在位置", color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 58.dp, top = 6.dp))
                CommentBlock(post, reading.summary?.author?.id.orEmpty(), model.largeText, onImage = onImage,
                    onThreadLink = model::navigateInput, onUser = model::openUser, videoThreadId = reading.id,
                    onAuthor = { model.openUser(post.author) },
                    onDiscussion = { model.openFloor(post) }, onLike = { reading.summary?.let { model.like(it, post.takeUnless { p -> p.floor == 1 }) } },
                    onReply = { model.openComposer(post.takeUnless { it.floor == 1 }) }, busy = model.pending != null,
                    liking = model.pending == "like:${reading.id}:${if (post.floor == 1) "" else post.id}")
                if (post.floor == 1 && reading.firstLoadedPage > 1) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BubbleTextButton(onClick = { model.jumpToPage(1) }, enabled = !reading.loading && !reading.loadingMore) { Text("回到原帖开头") }
                        BubbleTextButton(onClick = model::loadEarlierReplies, enabled = !reading.loading && !reading.loadingMore) { Text("加载此前楼层") }
                    }
                }
            }
            if (reading.posts.isNotEmpty()) item(key = "footer") {
                PaginationFooter(reading.loadingMore, reading.error, reading.hasMore) { model.loadReplies(refresh = reading.page == 0) }
            }
        }
        }
    }
    if (jump) AlertDialog(onDismissRequest = { jump = false }, title = { Text("跳转到页码") }, text = {
        AppTextField(pageText, { pageText = it.filter(Char::isDigit).take(6) }, label = { Text("每页 20 层，当前第 ${reading.page.coerceAtLeast(1)} 页") }, singleLine = true)
    }, confirmButton = { BubbleTextButton(onClick = { pageText.toIntOrNull()?.let { model.jumpToPage(it) }; jump = false }, enabled = (pageText.toIntOrNull() ?: 0) > 0) { Text("跳转") } },
        dismissButton = { BubbleTextButton(onClick = { jump = false }) { Text("取消") } })
}

@Composable
fun ReadingActions(thread: ThreadItem, saved: Boolean, onSave: () -> Unit, onReply: () -> Unit, onLike: () -> Unit, busy: Boolean, liking: Boolean) {
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
    GlassPanel(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconToggleButton(saved, { onSave() }, Modifier.size(48.dp)) {
                val rich = LocalMotionEnabled.current
                val scale by animateFloatAsState(if (saved && rich) 1.12f else 1f, spring(dampingRatio = .58f, stiffness = 500f), label = "收藏反馈")
                ThemeIcon(if (saved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, if (saved) "取消本地收藏" else "收藏到本机",
                    modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale })
            }
            FilledTonalButton(onClick = onLike, enabled = !busy, modifier = Modifier.heightIn(min = LocalTouchTarget.current), contentPadding = PaddingValues(horizontal = 12.dp)) {
                if (liking) AppCircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else ThemeIcon(Icons.Outlined.ThumbUp, if (thread.liked) "取消点赞" else "点赞", Modifier.size(18.dp))
                Text(if (thread.liked) "已赞" else "赞", modifier = Modifier.padding(start = 5.dp))
            }
            Button(onClick = onReply, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = LocalTouchTarget.current), shape = if (LocalVisualStyle.current == VisualStyle.Pixel) MaterialTheme.shapes.small else RoundedCornerShape(22.dp)) {
                ThemeIcon(Icons.Outlined.Edit, null, Modifier.size(18.dp))
                Text("写评论", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloorSheet(model: TiebaViewModel, onImage: (dev.hydrogen.tieba.data.ImageGallery) -> Unit) {
    val floor = model.floor ?: return
    val floorScroll = key(floor.parent.id) { rememberLazyListState() }
    var located by remember(floor.parent.id, floor.targetPostId) { mutableStateOf(false) }
    LaunchedEffect(floor.posts, floor.targetPostId) {
        val index = floor.posts.indexOfFirst { it.id == floor.targetPostId }
        if (!located && floor.targetPostId.isNotBlank() && index >= 0) {
            floorScroll.scrollToItem(index + 1)
            located = true
        }
    }
    val authorId = model.reading?.summary?.author?.id.orEmpty()
    val tid = model.reading?.id.orEmpty()
    val openUser: (dev.hydrogen.tieba.data.Person) -> Unit = { model.closeFloor(); model.openUser(it) }
    // A fractional height inside the sheet depends on the sheet's own moving
    // constraints. Give it a stable viewport instead, so end-of-list flings
    // cannot repeatedly resize the content and recalculate sheet anchors.
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val sheetHeight = maxHeight * .92f
    ModalBottomSheet(modifier = Modifier.height(sheetHeight), onDismissRequest = model::closeFloor, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(floor.parent.floorLabel?.let { "${it}的讨论" } ?: "这条回复的讨论", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                BubbleIconButton(onClick = model::closeFloor) { ThemeIcon(Icons.Outlined.Close, "关闭楼中楼") }
            }
            LazyColumn(Modifier.weight(1f), state = floorScroll, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                item(key = "parent") {
                    CommentBlock(floor.parent.copy(previewReplies = emptyList(), subReplies = 0), authorId, model.largeText,
                        onImage, model::navigateInput, {}, onUser = openUser, videoThreadId = tid,
                        onAuthor = { openUser(floor.parent.author) }, onReply = { model.openComposer(floor.parent) },
                        onLike = { model.reading?.summary?.let { model.like(it, floor.parent.takeUnless { p -> p.floor == 1 }) } }, busy = model.pending != null,
                        liking = model.pending == "like:$tid:${if (floor.parent.floor == 1) "" else floor.parent.id}")
                }
                if (floor.posts.isEmpty() && !floor.loading && floor.error == null) item {
                    EmptyState("还没有可显示的回复", "可以回复这一楼，参与讨论")
                }
                items(floor.posts, key = { it.id }) { post ->
                    if (post.id == floor.targetPostId) Text("定位的回复", color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 58.dp, top = 6.dp))
                    CommentBlock(post, authorId, model.largeText, onImage, model::navigateInput, {}, nested = true,
                        onLike = { model.reading?.summary?.let { model.like(it, post) } }, onReply = { model.openComposer(floor.parent, post) },
                        busy = model.pending != null, onUser = openUser, videoThreadId = tid,
                        liking = model.pending == "like:$tid:${post.id}", onAuthor = { openUser(post.author) })
                }
                item(key = "more") { PaginationFooter(floor.loading, floor.error, floor.hasMore, model::loadFloor) }
            }
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                GlassPanel(Modifier.fillMaxWidth()) {
                    TextButton(onClick = { model.openComposer(floor.parent) }, enabled = model.pending == null,
                        modifier = Modifier.fillMaxWidth().heightIn(min = LocalTouchTarget.current),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                        ThemeIcon(Icons.Outlined.Edit, null, Modifier.size(18.dp))
                        Text("回复这一楼", Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}
}
