package dev.hydrogen.tieba.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import android.content.ClipData
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.hydrogen.tieba.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativeInbox(model: TiebaViewModel, modifier: Modifier) {
    val chat = model.chats
    var filter by rememberSaveable { mutableStateOf("全部") }; var query by rememberSaveable { mutableStateOf("") }
    var newPrivate by rememberSaveable { mutableStateOf(false) }
    val directoryOpen = chat.directoryVisible
    DisposableEffect(directoryOpen) { onDispose { if(directoryOpen) chat.cancelDirectory() } }
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(model.account.uid,lifecycle) {
        chat.bind(model.account)
        if(model.account.loggedIn) lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try { while(true) { chat.poll(); kotlinx.coroutines.delay(15_000) } }
            finally { chat.pauseReads(null) }
        }
    }
    Column(modifier) {
        ScreenHeading("消息",actions = {
            BubbleIconButton(onClick = { newPrivate = true }) { ThemeIcon(Icons.Outlined.Edit,"新私信") }
            BubbleIconButton(onClick = { chat.showDirectory() }) { ThemeIcon(Icons.Outlined.GroupAdd,"查找吧群") }
        })
        Row(Modifier.padding(horizontal = 12.dp),horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(Icons.Outlined.FavoriteBorder to "点赞",Icons.Outlined.ChatBubbleOutline to "回复",Icons.Outlined.AlternateEmail to "@我").forEach { (icon,label) ->
                CompactBubbleButton(onClick = { model.openPanel(PanelKind.Notices,notice = when(label) { "点赞" -> NoticeKind.Like; "@我" -> NoticeKind.At; else -> NoticeKind.Reply }) },
                    modifier = Modifier.weight(1f),fill = true,visualMinHeight = 60.dp,
                    contentPadding = PaddingValues(horizontal = 10.dp,vertical = 16.dp)) {
                    ThemeIcon(icon,null,Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text(if(label == "@我") "我" else label,style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        AppTextField(query,{ query = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true, placeholder = { Text("搜索会话或消息") })
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            listOf("全部","私信","吧群","本机未看").forEach { label -> BubbleFilterChip(filter == label,{ filter = label },{ Text(label) }) }
        }
        val visible = chat.conversations.filter { c ->
            (filter != "私信" || c.room == null) && (filter != "吧群" || c.room != null) &&
                (filter != "本机未看" || c.messages.any { it.time > c.seen && it.senderId != model.account.uid }) &&
                (query.isBlank() || c.person.name.contains(query,true) || c.messages.any { it.text.contains(query,true) })
        }.sortedWith(compareByDescending<Conversation> { it.pinned }.thenByDescending { it.messages.lastOrNull()?.time ?: 0 })
        LazyColumn(Modifier.weight(1f),contentPadding = PaddingValues(bottom = 130.dp)) {
            if(!model.account.loggedIn) item { EmptyState("登录后查看消息","私信与吧群均使用百度贴吧账号。",action = "登录",onAction = model::login) }
            if(model.account.loggedIn) item {
                Column(Modifier.padding(horizontal = 16.dp,vertical = 4.dp)) {
                    Text(if(chat.privateEnabled) "私信使用百度旧版接口，首次登录时的账号凭据缺少 HTTPS 保护，可在设置 → 账号关闭。" else "旧版私信连接已关闭，可在设置 → 账号开启；本机记录仍可查看。",style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if(chat.privateSyncedAt > 0) Text("上次私信同步：${chatSyncTime(chat.privateSyncedAt)} · 仅含接口窗口与本机记录",style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("吧群仅列出本机最近打开的群；前台分批同步，不代表百度完整入群列表。",style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if(chat.error != null) BubbleTextButton(onClick = model::openPrivateMessages) { Text("打开官方私信") }
                }
            }
            if(chat.groupsLoading) item {
                Column {
                    LoadingRow("同步最近群聊 ${chat.groupsProgress}/${chat.groupsTotal}")
                    BubbleTextButton(onClick = chat::cancelGroupRefresh) { Text("停止本轮同步") }
                }
            }
            if(chat.loading && chat.conversations.isEmpty()) item { LoadingRow("正在同步私信") }
            chat.error?.let { error -> item { ChatError(error,chat::retryRead) } }
            if(!chat.loading && model.account.loggedIn && visible.isEmpty()) item { EmptyState(if(query.isNotBlank()) "没有匹配的会话" else "这里还没有会话","可使用右上角发起私信或按吧查找吧群。只显示接口返回及本机已保存的消息。") }
            items(visible,key = { it.key }) { c ->
                var menu by remember { mutableStateOf(false) }
                Column {
                    Row(Modifier.fillMaxWidth().navigationBubble().combinedClickable(onClick = { chat.select(c) },onLongClick = { menu = true })
                        .heightIn(min = LocalTouchTarget.current).padding(horizontal = 12.dp,vertical = 8.dp),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PersonAvatar(c.person,Modifier.size(42.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(c.person.name,Modifier.weight(1f),style = MaterialTheme.typography.titleSmall,maxLines = 1,overflow = TextOverflow.Ellipsis)
                                if(c.pinned) ThemeIcon(Icons.Outlined.PushPin,"已置顶",Modifier.size(13.dp))
                                c.messages.lastOrNull()?.let { Text(relativeTime(it.time),style = MaterialTheme.typography.labelSmall,color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            Text(if(c.draft.isNotEmpty()) "草稿 · ${c.draft}" else c.messages.lastOrNull()?.let(GroupChatRules::preview) ?: if(c.room != null) "吧群 · ${c.room.forum}吧" else "点击开始对话",
                                style = MaterialTheme.typography.bodySmall,maxLines = 1,overflow = TextOverflow.Ellipsis,color = if(c.draft.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if(c.messages.any { it.time > c.seen && it.senderId != model.account.uid }) ThemeIcon(Icons.Outlined.Circle,"本机有未看的消息",Modifier.size(8.dp),tint = MaterialTheme.colorScheme.primary)
                    }
                    chat.groupErrors[c.key]?.let { ChatError(it,{ chat.refreshGroups(true) }) }
                    BubbleDropdownMenu(menu,{ menu = false }) { BubbleMenuItem({ Text(if(c.pinned) "取消置顶" else "置顶会话") },{ chat.pin(c.key); menu = false }) }
                }
            }
            if(model.account.loggedIn && chat.conversations.any { it.room != null }) item { BubbleTextButton(onClick = { chat.refreshGroups(true) },enabled = !chat.groupsLoading,modifier = Modifier.fillMaxWidth()) { Text("刷新最近群聊") } }
            if(model.account.loggedIn) item { BubbleTextButton(onClick = { chat.refresh() },enabled = !chat.loading && chat.privateEnabled,modifier = Modifier.fillMaxWidth()) { Text(if(chat.loading) "同步中…" else "刷新私信") } }
        }
    }
    if(newPrivate) {
        var uid by rememberSaveable { mutableStateOf("") }
        AlertDialog(onDismissRequest = { newPrivate = false },title = { Text("发起私信") },text = { Column {
            Text("输入对方的贴吧 UID。不会自动发送任何内容。",style = MaterialTheme.typography.bodySmall)
            AppTextField(uid,{ uid = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("贴吧 UID") }) } },
            confirmButton = { BubbleTextButton(onClick = { chat.openPrivate(Person(uid.trim(),"吧友 ${uid.trim()}")); newPrivate = false },enabled = uid.trim().toLongOrNull()?.let { it > 0 } == true && model.account.loggedIn) { Text("进入会话") } },
            dismissButton = { BubbleTextButton(onClick = { newPrivate = false }) { Text("取消") } })
    }
    if(chat.directoryVisible) ChatSheet(model::dismissGroupDirectory) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("查找吧群",style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically) { AppTextField(chat.directoryQuery,chat::changeDirectoryQuery, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("输入吧名") })
                BubbleTextButton(onClick = chat::findRooms,enabled = !chat.directoryLoading && model.account.loggedIn) { Text("查找") } }
            Text("资料和消息来自百度贴吧，查看不会自动加入群。",style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.heightIn(max = 420.dp),contentPadding = PaddingValues(vertical = 12.dp)) {
                if(chat.directoryLoading) item { LoadingRow("正在读取吧群") }
                chat.directoryError?.let { item { ChatError(it,chat::findRooms) } }
                items(chat.rooms,key = { it.id }) { room -> Row(Modifier.fillMaxWidth().navigationBubble().clickable { chat.directoryVisible = false; chat.cancelDirectory(); chat.openGroup(room) }.padding(horizontal = 12.dp,vertical = 8.dp),verticalAlignment = Alignment.CenterVertically) {
                    PersonAvatar(Person(room.id,room.name,room.avatar)); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { Text(room.name,style = MaterialTheme.typography.titleSmall); Text("${room.forum}吧${room.members?.let { " · $it 位成员" }.orEmpty()}",style = MaterialTheme.typography.bodySmall) }
                } }
                if(chat.rooms.isEmpty() && !chat.directoryLoading && chat.directoryError == null) item { Text("输入吧名查看公开群列表",Modifier.padding(16.dp),style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@Composable
internal fun ChatError(text: String, retry: () -> Unit) { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp,vertical = 4.dp),verticalAlignment = Alignment.CenterVertically) {
    Text(text,Modifier.weight(1f),style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.error)
    BubbleTextButton(onClick = retry) { Text("重试") }
} }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativeConversation(model: TiebaViewModel, modifier: Modifier, onImage: (ImageGallery) -> Unit) {
    key(model.account.uid, model.chats.selected) { NativeConversationContent(model, modifier, onImage) }
}

internal fun chatSyncTime(seconds: Long): String = java.text.SimpleDateFormat("MM-dd HH:mm:ss",java.util.Locale.getDefault()).format(java.util.Date(seconds * 1000))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NativeConversationContent(model: TiebaViewModel, modifier: Modifier, onImage: (ImageGallery) -> Unit) {
    val chat = model.chats; val c = chat.current ?: return
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(c.key,lifecycle) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try { while(true) { chat.poll(); kotlinx.coroutines.delay(15_000) } }
            finally { chat.pauseReads(c.key) }
        }
    }
    val restoredViewport = remember { chat.viewport(c.key) }
    val restoredIndex = remember { c.messages.indexOfFirst { "${it.senderId}:${it.id}" == restoredViewport?.messageKey } }
    val scroll = rememberLazyListState(initialFirstVisibleItemIndex = restoredIndex.coerceAtLeast(0),initialFirstVisibleItemScrollOffset = restoredViewport?.offset ?: 0)
    val scope = rememberCoroutineScope(); val clipboard = LocalClipboard.current
    var search by rememberSaveable(c.key) { mutableStateOf(false) }; var query by rememberSaveable(c.key) { mutableStateOf("") }
    var hit by remember(c.key,query) { mutableIntStateOf(0) }; var info by rememberSaveable(c.key) { mutableStateOf(false) }
    var savedOnly by rememberSaveable(c.key) { mutableStateOf(false) }; var quoteId by rememberSaveable(c.key) { mutableStateOf<String?>(null) }
    var withdrawal by remember { mutableStateOf<ChatMessage?>(null) }
    var moderationPerson by remember { mutableStateOf<Person?>(null) }
    var unlockSend by remember { mutableStateOf(false) }
    var pendingLocate by remember { mutableStateOf<String?>(null) }
    var selected by remember(c.key) { mutableStateOf<Set<String>>(emptySet()) }; var highlight by remember(c.key) { mutableStateOf("") }
    val quote = c.messages.firstOrNull { it.id == quoteId && !it.revoked }
    val visible = c.messages.filter { !savedOnly || "${it.senderId}:${it.id}" in c.saved }
    val matches = visible.indices.filter { query.isNotBlank() && !visible[it].revoked && visible[it].text.contains(query,true) }
    val latestVisible by rememberUpdatedState(visible)
    val latestSavedOnly by rememberUpdatedState(savedOnly)
    LaunchedEffect(c.key,scroll) {
        snapshotFlow { (scroll.layoutInfo.visibleItemsInfo.firstOrNull()?.key as? String) to scroll.firstVisibleItemScrollOffset }.distinctUntilChanged().collect { (key,offset) ->
            if(!latestSavedOnly && key != null && latestVisible.any { "${it.senderId}:${it.id}" == key }) chat.rememberViewport(c.key,ChatViewport(key,offset))
        }
    }
    LaunchedEffect(c.key, scroll) {
        snapshotFlow { scroll.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }.toSet() }
            .distinctUntilChanged().collect { keys -> if (!latestSavedOnly) chat.markVisible(c.key, keys) }
    }
    fun locate(id: String) { savedOnly = false; pendingLocate = id }
    LaunchedEffect(pendingLocate,visible.size,savedOnly) {
        val id = pendingLocate ?: return@LaunchedEffect
        val index = visible.indexOfFirst { it.id == id }
        if(index >= 0) { scroll.scrollToItem(index); highlight = id; pendingLocate = null }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    fun back() { when { imeVisible -> { keyboard?.hide(); focus.clearFocus() }; info -> info = false; selected.isNotEmpty() -> selected = emptySet(); search -> { search = false; query = "" }; else -> model.closeNativeChat() } }
    // Local UI consumes back first; otherwise the root owns the predictive gesture.
    BackHandler(imeVisible || info || selected.isNotEmpty() || search) { back() }
    LaunchedEffect(highlight) { if(highlight.isNotEmpty()) { kotlinx.coroutines.delay(1100); highlight = "" } }
    var previousCount by remember(c.key) { mutableIntStateOf(0) }
    var previousTail by remember(c.key) { mutableStateOf("") }
    LaunchedEffect(visible.size,visible.lastOrNull()?.id) {
        val nearEnd = (scroll.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= previousCount - 3
        val tail = visible.lastOrNull()?.id.orEmpty()
        if(visible.isNotEmpty() && ((previousCount == 0 && restoredIndex < 0) || (previousCount > 0 && nearEnd && tail != previousTail)) && query.isBlank() && !savedOnly) scroll.scrollToItem(visible.lastIndex)
        previousCount = visible.size; previousTail = tail
    }
    val nearBottom by remember(visible) { derivedStateOf { (scroll.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= visible.lastIndex - 1 } }
    val restriction = c.room?.let(GroupChatRules::restriction)
    fun previewImage(message: ChatMessage) {
        val images = c.messages.filter { !it.revoked && it.image.isNotBlank() }
        val index = images.indexOfFirst { it.senderId == message.senderId && it.id == message.id }
        if(index >= 0) onImage(ImageGallery(images.map { ImageSource(it.image) },index))
    }
    Column(modifier.imePadding().navigationBarsPadding().themeCanvas()) {
        ScreenHeading(if(selected.isEmpty()) c.person.name else "已选 ${selected.size} 条",{ back() }) {
            if(selected.isNotEmpty()) {
                BubbleIconButton(onClick = { val text = visible.filter { "${it.senderId}:${it.id}" in selected }.joinToString("\n") { "${it.person.name}：${GroupChatRules.preview(it)}" }; scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("聊天消息",text))) }; selected = emptySet() }) { ThemeIcon(Icons.Outlined.ContentCopy,"复制选中消息") }
                BubbleIconButton(onClick = { selected = emptySet() }) { ThemeIcon(Icons.Outlined.Close,"取消多选") }
            } else { BubbleIconButton(onClick = { search = !search }) { ThemeIcon(Icons.Outlined.Search,"搜索已加载消息") }
                BubbleIconButton(onClick = { info = true }) { ThemeIcon(Icons.Outlined.MoreHoriz,if(c.room == null) "会话资料" else "群资料") } }
        }
        if(search) Row(Modifier.padding(horizontal = 12.dp),verticalAlignment = Alignment.CenterVertically) {
            AppTextField(query,{ query = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("搜索已加载消息") })
            Text(if(matches.isEmpty()) "0" else "${hit.coerceAtMost(matches.lastIndex)+1}/${matches.size}",style = MaterialTheme.typography.labelSmall)
            BubbleIconButton(onClick = { if(matches.isNotEmpty()) { hit = (hit-1+matches.size)%matches.size; locate(visible[matches[hit]].id) } },enabled = matches.isNotEmpty()) { ThemeIcon(Icons.Outlined.KeyboardArrowUp,"上一个匹配") }
            BubbleIconButton(onClick = { if(matches.isNotEmpty()) { hit = (hit+1)%matches.size; locate(visible[matches[hit]].id) } },enabled = matches.isNotEmpty()) { ThemeIcon(Icons.Outlined.KeyboardArrowDown,"下一个匹配") }
        }
        if(chat.loading && c.messages.isEmpty()) LoadingRow("正在读取消息")
        chat.error?.let { ChatError(it,chat::retryRead) }
        chat.groupDetailErrors[c.key]?.let { Text(it,Modifier.padding(horizontal = 16.dp),style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if(chat.hasMore && !savedOnly) BubbleTextButton(onClick = { chat.refresh(true) },enabled = !chat.loading,modifier = Modifier.fillMaxWidth()) { Text(if(chat.loadingOlder) "正在加载更早消息…" else "查看更早消息") }
        if(chat.historyExhausted && !savedOnly) Text("已到百度接口可返回的历史起点",Modifier.padding(horizontal = 16.dp,vertical = 2.dp),style = MaterialTheme.typography.labelSmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
        if(savedOnly) BubbleTextButton(onClick = { savedOnly = false }) { Text("本地收藏 · 显示全部") }
        LazyColumn(Modifier.weight(1f),state = scroll,contentPadding = PaddingValues(horizontal = 16.dp,vertical = 8.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if(visible.isEmpty() && !chat.loading) item { EmptyState(if(savedOnly) "还没有本地收藏" else "暂时没有消息",if(savedOnly) "长按消息可以收藏到本机。" else "发送消息或刷新查看；历史范围由百度接口返回。") }
            itemsIndexed(visible,key = { _,m -> "${m.senderId}:${m.id}" }) { index,message ->
                val key = "${message.senderId}:${message.id}"
                if(index == 0 || chatDate(visible[index-1].time) != chatDate(message.time)) Text(chatDate(message.time),Modifier.fillMaxWidth().padding(vertical = 4.dp),style = MaterialTheme.typography.labelSmall,color = MaterialTheme.colorScheme.onSurfaceVariant,textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                MessageRow(message,message.senderId == model.account.uid,query,key in c.saved,key in selected || highlight == message.id,
                    onReply = { if(!message.revoked) quoteId = message.id },onSave = { chat.save(message) },onSelect = { selected = if(key in selected) selected-key else selected+key },
                    onClick = { if(selected.isNotEmpty()) selected = if(key in selected) selected-key else selected+key },
                    onPerson = { if(message.person.id.isNotBlank()) model.openUser(message.person) },
                    onImage = { previewImage(message) },
                    canWithdraw = c.room?.let { GroupChatRules.canWithdraw(it,message,model.account.uid) } == true && !chat.actionBusy && !chat.sending && !chat.loading,
                    onWithdraw = { withdrawal = message },
                    canModerate = c.room != null && message.senderId != model.account.uid && message.senderId.toLongOrNull()?.let { it > 0 } == true,
                    onModerate = { moderationPerson = message.person },
                    onLink = { message.link.substringAfter("/p/","").takeWhile { it.isDigit() }.takeIf { it.isNotBlank() }?.let { model.openThread(it) } })
            }
        }
        if(!nearBottom && visible.isNotEmpty() && !savedOnly) BubbleTextButton(onClick = { locate(visible.last().id) },modifier = Modifier.align(Alignment.End)) { Text("回到最新消息 ↓") }
        chat.actionResult?.let { Text(it,Modifier.padding(horizontal = 16.dp,vertical = 4.dp),style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if(chat.actionBusy) LoadingRow("正在提交群操作")
        if(c.room == null && (!PrivateChatConnection.ENABLED || !chat.privateEnabled)) BubbleTextButton(onClick = model::openPrivateMessages) { Text("原生私信已关闭 · 打开官方私信") }
        chat.sendError?.let { Text(it,Modifier.padding(horizontal = 16.dp,vertical = 4.dp),style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.error) }
        restriction?.let { Text(it,Modifier.padding(horizontal = 16.dp),style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.error) }
        if(chat.uncertain && !chat.sending) BubbleTextButton(onClick = { unlockSend = true }) { Text("已核对会话，处理未确认的发送") }
        if(c.draft.length > 1600) Text("${c.draft.length}/2000",Modifier.padding(horizontal = 20.dp),style = MaterialTheme.typography.labelSmall,color = if(c.draft.length > 2000) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        GlassPanel(Modifier.fillMaxWidth().padding(horizontal = 12.dp,vertical = 6.dp)) {
            Column(Modifier.padding(horizontal = 8.dp,vertical = 2.dp)) {
                if(quote != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).heightIn(min = LocalTouchTarget.current).clickable { locate(quote.id) }.padding(start = 8.dp)) {
                        Text("文字引用 · ${quote.person.name}",style = MaterialTheme.typography.labelSmall,color = MaterialTheme.colorScheme.primary)
                        Text(GroupChatRules.preview(quote),style = MaterialTheme.typography.bodySmall,maxLines = 1,overflow = TextOverflow.Ellipsis)
                    }; BubbleIconButton(onClick = { quoteId = null }) { ThemeIcon(Icons.Outlined.Close,"取消引用") }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    AppTextField(c.draft, chat::draft, Modifier.weight(1f), maxLines = 5,
                        placeholder = { Text("写消息…") })
                    BubbleIconButton(onClick = { chat.send(quote) { if(quoteId == quote?.id) quoteId = null } },enabled = c.draft.isNotBlank() && c.draft.length <= 2000 && !chat.sending && !chat.actionBusy && !chat.uncertain && restriction == null && model.account.loggedIn && (c.room != null || (PrivateChatConnection.ENABLED && chat.privateEnabled))) {
                        if(chat.sending) AppCircularProgressIndicator(Modifier.size(18.dp),strokeWidth = 2.dp) else ThemeIcon(Icons.AutoMirrored.Outlined.Send,"发送消息",Modifier.size(20.dp))
                    }
                }
            }
        }
    }
    withdrawal?.let { message -> AlertDialog(onDismissRequest = { withdrawal = null },title = { Text("撤回这条群消息？") },text = { Text("将向百度提交撤回请求，能否撤回由服务端权限和时限决定。") },confirmButton = { BubbleTextButton(onClick = { withdrawal = null; chat.withdraw(message) }) { Text("撤回") } },dismissButton = { BubbleTextButton(onClick = { withdrawal = null }) { Text("取消") } }) }
    if(unlockSend) AlertDialog(onDismissRequest = { unlockSend = false },title = { Text("确认已核对发送结果") },text = { Text("请先刷新会话并核对另一端，确认上次消息是否已经收到。解锁只允许再次编辑和发送，不会自动重发；若已送达，请清空保留的草稿。") },confirmButton = { BubbleTextButton(onClick = { chat.acknowledgeUncertain(); unlockSend = false }) { Text("已核对，解锁") } },dismissButton = { BubbleTextButton(onClick = { unlockSend = false; chat.refresh() }) { Text("先刷新") } })
    moderationPerson?.let { person -> GroupModerationSheet(model,c,person) { moderationPerson = null } }
    if(info && c.room != null) GroupDetailsSheet(model,c,onDismiss = { info = false },onLocate = { info = false; locate(it.id) },onImage = ::previewImage)
    if(info && c.room == null) ChatSheet({ info = false }) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp),contentPadding = PaddingValues(horizontal = 20.dp,vertical = 8.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text(c.person.name,style = MaterialTheme.typography.titleLarge) }
            item { Text(if(c.syncedAt > 0) "上次消息同步：${chatSyncTime(c.syncedAt)}" else "尚未完成消息同步；当前可见内容可能来自本机缓存。",style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { FlowRow { BubbleTextButton(onClick = { chat.pin(c.key) }) { Text(if(c.pinned) "取消置顶" else "置顶会话") }
                BubbleTextButton(onClick = { savedOnly = !savedOnly; info = false }) { Text("本地收藏") }
                BubbleTextButton(onClick = model::openPrivateMessages) { Text("官方私信") }
                BubbleTextButton(onClick = { chat.refresh(); info = false },enabled = !chat.loading && chat.privateEnabled) { Text("刷新") } } }
            item { Text("已加载的共享内容",style = MaterialTheme.typography.titleSmall) }
            val media = c.messages.filter { it.image.isNotBlank() || it.link.isNotBlank() }
            if(media.isEmpty()) item { Text("暂无图片或帖子",style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(media,key = { "${it.senderId}:${it.id}" }) { m -> BubbleTextButton(onClick = { savedOnly = false; info = false; locate(m.id) }) { Text(if(m.image.isNotBlank()) "图片 · ${m.person.name}" else m.link,maxLines = 1,overflow = TextOverflow.Ellipsis) } }
            item { Text("消息来自百度贴吧；置顶、草稿、收藏与“本机未看”只在本机记录。私信当前读取百度同步窗口及本机记录，不代表完整历史。引用以文字发送。群成员完整列表、后台推送及附件发送尚未接入。",style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun MessageRow(message: ChatMessage, own: Boolean, query: String, saved: Boolean, selected: Boolean,
    onReply: () -> Unit,onSave: () -> Unit,onSelect: () -> Unit,onClick: () -> Unit,onPerson: () -> Unit,onImage: () -> Unit,canWithdraw: Boolean,onWithdraw: () -> Unit,
    canModerate: Boolean,onModerate: () -> Unit,onLink: () -> Unit) {
    val clipboard = LocalClipboard.current; var menu by remember { mutableStateOf(false) }; var drag by remember { mutableFloatStateOf(0f) }
    var animation by remember { mutableStateOf<Job?>(null) }; val scope = rememberCoroutineScope(); val motion = LocalMotionEnabled.current
    val limit = with(LocalDensity.current) { 64.dp.toPx() }; val threshold = limit * .7f
    val tracker = remember { VelocityTracker() }; var travel by remember { mutableFloatStateOf(0f) }
    fun release(reply: Boolean, velocity: Float = 0f) { if(reply && !message.revoked) onReply(); animation = scope.launch {
        if(motion) animate(drag,0f,initialVelocity = velocity.coerceIn(-limit*8,limit*8),animationSpec = spring(dampingRatio = 1f,stiffness = 650f)) { value,_ -> drag = value }
        else drag = 0f
    } }
    Row(Modifier.fillMaxWidth().graphicsLayer { translationX = drag }.pointerInput(message.id,motion) {
        detectHorizontalDragGestures(onDragStart = { animation?.cancel(); tracker.resetTracking(); travel = 0f },
            onHorizontalDrag = { change, amount -> change.consume(); travel += amount; tracker.addPosition(change.uptimeMillis,Offset(travel,0f)); drag = (drag+amount).coerceIn(-limit,0f) },
            onDragEnd = { val velocity = tracker.calculateVelocity().x; release(-drag >= threshold || (drag < -limit/4 && velocity < -limit*12),velocity) },onDragCancel = { release(false) }) },
        horizontalArrangement = if(own) Arrangement.End else Arrangement.Start,verticalAlignment = Alignment.Top) {
        if(!own) Box(Modifier.sizeIn(minWidth = LocalTouchTarget.current,minHeight = LocalTouchTarget.current).clickable(onClick = onPerson),contentAlignment = Alignment.TopStart) { PersonAvatar(message.person) }
        val pixel = dev.hydrogen.tieba.ui.theme.LocalVisualStyle.current == dev.hydrogen.tieba.data.VisualStyle.Pixel
        Column(Modifier.weight(1f,fill = false).widthIn(min = LocalTouchTarget.current,max = 420.dp).heightIn(min = LocalTouchTarget.current).then(if(own || selected || pixel) Modifier.bubbleBackground(MaterialTheme.shapes.medium, selected = own || selected).padding(horizontal = 12.dp,vertical = 8.dp) else Modifier)
            .combinedClickable(onClick = onClick,onLongClick = { menu = true }),horizontalAlignment = if(own) Alignment.End else Alignment.Start) {
            if(!own) Text(message.person.name + if(message.person.level > 0) " · Lv.${message.person.level}" else "",style = MaterialTheme.typography.labelMedium,color = MaterialTheme.colorScheme.primary)
            if(message.revoked) Text("消息已撤回",style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
            if(!message.revoked && message.text.isNotBlank()) Text(buildAnnotatedString {
                val found = if(query.isBlank()) -1 else message.text.indexOf(query,ignoreCase = true)
                if(found < 0) append(message.text) else { append(message.text.substring(0,found)); withStyle(SpanStyle(background = MaterialTheme.colorScheme.tertiaryContainer,color = MaterialTheme.colorScheme.onTertiaryContainer)) { append(message.text.substring(found,found+query.length)) }; append(message.text.substring(found+query.length)) }
            },style = MaterialTheme.typography.bodyMedium)
            if(!message.revoked && message.image.isNotBlank()) AsyncImage(imageLoader = LocalAppImageLoader.current, model = message.image, contentDescription = "消息图片",modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onImage))
            if(!message.revoked && message.link.isNotBlank()) BubbleTextButton(onClick = onLink) { Text("查看帖子",style = MaterialTheme.typography.labelMedium) }
            if(!message.revoked && message.text.isBlank() && message.image.isBlank() && message.link.isBlank()) Text("[暂不支持的消息类型 ${message.type}]",style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(4.dp)) { if(saved) ThemeIcon(Icons.Outlined.BookmarkBorder,"已收藏到本机",Modifier.size(11.dp))
                Text(chatClock(message.time) + if(own && message.id.startsWith("ack:")) " · 已提交，待历史确认" else "",style = MaterialTheme.typography.labelSmall,color = MaterialTheme.colorScheme.onSurfaceVariant) }
            BubbleDropdownMenu(menu,{ menu = false }) {
                BubbleMenuItem({ Text("回复") },{ menu = false; onReply() },enabled = !message.revoked)
                BubbleMenuItem({ Text("复制文字") },{ scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("聊天消息",message.text))) }; menu = false },enabled = !message.revoked && message.text.isNotBlank())
                BubbleMenuItem({ Text(if(saved) "取消本地收藏" else "收藏到本机") },{ onSave(); menu = false })
                BubbleMenuItem({ Text("多选") },{ onSelect(); menu = false })
                if(canWithdraw) BubbleMenuItem({ Text("撤回群消息") },{ menu = false; onWithdraw() })
                if(canModerate) BubbleMenuItem({ Text("群禁言管理（吧务）") },{ menu = false; onModerate() })
                Text(chatSyncTime(message.time),Modifier.padding(horizontal = 16.dp,vertical = 6.dp),style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

internal fun chatDate(seconds: Long): String = java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.getDefault()).format(java.util.Date(seconds * 1000))
internal fun chatClock(seconds: Long): String = java.text.SimpleDateFormat("HH:mm",java.util.Locale.getDefault()).format(java.util.Date(seconds * 1000))
