package dev.hydrogen.tieba.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hydrogen.tieba.data.*

@Composable
fun ProfileNumbers(profile: UserProfile?, onFollows: () -> Unit, onFans: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BubbleTextButton(onClick = onFollows, modifier = Modifier.heightIn(min = LocalTouchTarget.current)) { Text("${profile?.follows?.let(::compactNumber) ?: "—"} 关注") }
        BubbleTextButton(onClick = onFans, modifier = Modifier.heightIn(min = LocalTouchTarget.current)) { Text("${profile?.fans?.let(::compactNumber) ?: "—"} 粉丝") }
    }
}

@Composable
fun PersonRow(profile: UserProfile, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().navigationBubble().clickable(onClick = onOpen).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        PersonAvatar(profile.person, Modifier.size(48.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(profile.person.name, style = MaterialTheme.typography.titleSmall)
            PersonBadges(profile.person, IdentitySurface.Profile)
            if (profile.introduction.isNotBlank()) Text(profile.introduction, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        ThemeIcon(Icons.Outlined.ChevronRight, "查看用户主页")
    }
}

@Composable
fun CommunityScreen(model: TiebaViewModel, modifier: Modifier, onImage: (dev.hydrogen.tieba.data.ImageGallery) -> Unit, state: PanelState? = model.panel) {
    val state = state ?: return
    if(state.kind==PanelKind.Search) { SearchScreen(model,state,modifier,onImage); return }
    var deleting by remember(state.key) { mutableStateOf<UserContent?>(null) }
    val showThreads = state.kind != PanelKind.Comments
    val own = state.uid == model.account.uid && model.account.loggedIn
    Column(modifier) {
        ScreenHeading(if (state.kind == PanelKind.Notices) state.noticeKind.label else state.kind.label, model::closePanel, actions = {
            if (state.kind !in listOf(PanelKind.Search, PanelKind.Publish)) BubbleIconButton(onClick = { model.loadPanel(true) }, enabled = !state.loading) { ThemeIcon(Icons.Outlined.Refresh, "刷新") }
        })
        if (state.kind == PanelKind.Publish) { PublishingScreen(model); return@Column }
        if (state.kind == PanelKind.EditProfile) {
            ProfileEditor(model, state)
            return@Column
        }
        val hasContent = state.profile != null || state.items.isNotEmpty() || state.people.isNotEmpty() || state.forums.isNotEmpty() || state.notices.isNotEmpty()
        RefreshableList(state.refreshing, { model.loadPanel(true) }, hasContent = hasContent) {
        LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
            if (state.kind == PanelKind.Forums) item(key = "sign-all") { ForumSignInPanel(model) }
            state.profile?.takeIf { state.kind == PanelKind.User }?.let { profile -> item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    PersonAvatar(profile.person, Modifier.size(62.dp))
                    Text(profile.person.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
                    PersonBadges(profile.person, IdentitySurface.Profile)
                    if (profile.introduction.isNotBlank()) Text(profile.introduction, modifier = Modifier.padding(top = 8.dp))
                    val metadata = listOf("贴吧 ID ${profile.person.id}", profile.age.takeIf { it.isNotBlank() }?.let { "吧龄 $it" }, profile.location.takeIf { it.isNotBlank() }).filterNotNull()
                    Text(metadata.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ProfileNumbers(profile, { model.openPanel(PanelKind.People, state.uid) }, { model.openPanel(PanelKind.Fans, state.uid) })
                    if (!own) BubbleOutlinedButton(onClick = { model.followUser(profile) }, enabled = model.pending == null, modifier = Modifier.heightIn(min = LocalTouchTarget.current)) {
                        Text(if (model.pending == "user:${profile.person.id}") "处理中" else if (profile.followed) "已关注 · 取消关注" else "关注用户")
                    }
                    if (!own) BubbleTextButton(onClick = { model.openNativePrivate(profile.person) }) { Text("私信") }
                }
            } }
            if (state.kind == PanelKind.Cloud) item {
                if (model.cloudWrite?.uncertain == true) BubbleTextButton(onClick = model::showCloudWrite) { Text("有一项云收藏结果待核对") }
                Text("百度账号云收藏，与本机收藏分别保存。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
                BubbleTextButton(onClick = { model.closePanel(); model.collection = "收藏" }) { Text("查看本机收藏") }
            }
            items(state.items, key = { it.contentKey(showThreads) }) { content ->
                if (content.deleted) Text("该内容已被删除", modifier = Modifier.padding(20.dp))
                else {
                    FeedThread(model, content.thread, onImage, onOpen = {
                        model.openThread(content.thread.id, content.thread)
                        if (state.kind == PanelKind.Comments && content.postId.isNotBlank()) {
                            model.jumpToPage(1, content.postId, verifyNested = true)
                        }
                    })
                    if (own && state.kind in listOf(PanelKind.Threads, PanelKind.Comments)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (state.kind == PanelKind.Threads) BubbleTextButton(onClick = { model.editPublished(content.thread) }, enabled = model.pending == null) { Text("编辑 / 管理") }
                        if (content.hasDeleteTarget(showThreads)) BubbleTextButton(onClick = { deleting = content }, enabled = model.pending == null) {
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (state.kind == PanelKind.Cloud) BubbleTextButton(onClick = { model.cloudSave(content.thread, true) }, enabled = model.pending == null) { Text("移出云收藏") }
                }
            }
            items(state.people, key = { it.person.id }) { profile -> PersonRow(profile) { model.openUser(profile.person) } }
            items(state.forums, key = { "${it.id}:${it.name}" }) { forum ->
                Column {
                    ForumRow(forum) { model.openForum(forum.name) }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                        BubbleTextButton(onClick = { model.openForum(forum.name); model.forumInfoOpen = true }) { Text("资料 / 管理关注") }
                        BubbleTextButton(onClick = { model.signForum(forum.name) }, enabled = model.pending == null && !model.isForumSigned(forum.name)) {
                            Text(if (model.pending == "sign:${forum.name}") "签到中" else if (model.isForumSigned(forum.name)) "今日已签到" else "签到")
                        }
                    }
                }
            }
            items(state.results.threads, key = { it.id }) { FeedThread(model, it, onImage) }
            items(state.results.forums, key = { "${it.id}:${it.name}" }) { ForumRow(it) { model.openForum(it.name) } }
            items(state.results.users, key = { it.person.id }) { PersonRow(it) { model.openUser(it.person) } }
            items(state.notices, key = { "${it.id}:${it.postId}:${it.time}" }) { notice ->
                Column(Modifier.fillMaxWidth().navigationBubble().clickable(enabled = notice.threadId.isNotBlank()) {
                    model.openThread(notice.threadId)
                    if (notice.postId.isNotBlank()) model.jumpToPage(1, notice.postId, notice.isFloor)
                }.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PersonAvatar(notice.person)
                        Text(notice.person.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(start = 10.dp))
                        Text(relativeTime(notice.time), style = MaterialTheme.typography.labelSmall)
                    }
                    Text(dev.hydrogen.tieba.data.NoticeParser.action(state.noticeKind), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val body = dev.hydrogen.tieba.data.NoticeParser.body(notice, state.noticeKind, model.account.name)
                    if (body.isNotBlank()) Text(body, style = MaterialTheme.typography.bodyLarge)
                    if (notice.quote.isNotBlank()) Text("引用：${notice.quote}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    if (notice.title.isNotBlank()) Text(notice.title, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (state.loading) {
                if (!state.refreshing && state.page > 0) item { PaginationFooter(true, null, true) {} }
                else if (!state.refreshing || !hasContent) item { LoadingRow() }
            }
            else if (state.error != null) item {
                if (state.appendFailed) PaginationFooter(false, state.error, true) { model.loadPanel() }
                else EmptyState("暂时无法加载", state.error, action = "重试", onAction = { model.loadPanel(true) })
            }
            else if (!state.hasMore && state.items.isEmpty() && state.people.isEmpty() && state.forums.isEmpty() && state.notices.isEmpty() &&
                state.results.threads.isEmpty() && state.results.forums.isEmpty() && state.results.users.isEmpty() && (state.kind != PanelKind.Search || state.query.isNotBlank())) item {
                EmptyState("暂时没有内容", "百度贴吧本次未返回可显示的记录。")
            }
            if (!state.loading && state.error == null && state.hasMore) item { PaginationFooter(false, null, true) { model.loadPanel() } }
        }
        }
    }
    deleting?.let { item -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(if (state.kind == PanelKind.Threads) "删除这篇帖子？" else "删除这条评论？") },
        text = { Text("将向百度贴吧提交删除请求，删除后无法在本应用恢复。") },
        confirmButton = { BubbleTextButton(onClick = { deleting = null; model.deleteContent(item, state.kind == PanelKind.Threads) }) { Text("确认删除") } },
        dismissButton = { BubbleTextButton(onClick = { deleting = null }) { Text("取消") } }) }
}

@Composable
private fun ProfileEditor(model: TiebaViewModel, state: PanelState) {
    val profile = state.profile
    if (profile == null) {
        if (state.loading) LoadingRow() else EmptyState("资料未加载", state.error ?: "请重试", action = "重试", onAction = { model.loadPanel(true) })
        return
    }
    var name by rememberSaveable(profile.person.name) { mutableStateOf(profile.person.name) }
    var intro by rememberSaveable(profile.introduction) { mutableStateOf(profile.introduction) }
    var gender by rememberSaveable(profile.gender) { mutableIntStateOf(profile.gender) }
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PersonAvatar(profile.person, Modifier.size(66.dp))
        Text("贴吧 ID：${profile.person.id}${if (profile.age.isBlank()) "" else " · 吧龄 ${profile.age}"}", style = MaterialTheme.typography.bodySmall)
        AppTextField(name, { name = it.take(40) }, label = { Text("昵称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        AppTextField(intro, { intro = it.take(500) }, label = { Text("个人简介") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        Text("性别", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("未设置", "男", "女").forEachIndexed { i, label ->
            BubbleFilterChip(selected = gender == i, onClick = { gender = i }, label = { Text(label) }, modifier = Modifier.heightIn(min = LocalTouchTarget.current))
        } }
        Button(onClick = { confirm = true }, enabled = model.pending == null && name.isNotBlank() && (name != profile.person.name || intro != profile.introduction || gender != profile.gender), modifier = Modifier.fillMaxWidth().heightIn(min = LocalTouchTarget.current)) {
            Text(if (model.pending == "profile") "保存中" else "保存到百度账号")
        }
        Text("昵称的修改次数、长度和审核规则以百度贴吧为准。头像与更多账号资料目前可在官方资料页修改。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BubbleOutlinedButton(onClick = { model.webUrl = "https://tieba.baidu.com/mo/q/profile" }, modifier = Modifier.heightIn(min = LocalTouchTarget.current)) { Text("官方资料页") }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("保存账号资料？") }, text = { Text("昵称：$name\n简介：$intro\n将修改真实百度账号资料。") },
        confirmButton = { BubbleTextButton(onClick = { confirm = false; model.saveProfile(name.trim(), intro, gender) }) { Text("保存") } }, dismissButton = { BubbleTextButton(onClick = { confirm = false }) { Text("取消") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumInfoSheet(model: TiebaViewModel) {
    ModalBottomSheet(onDismissRequest = { model.forumInfoOpen = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        val info = model.feed.info
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${model.forum}吧", style = MaterialTheme.typography.headlineSmall)
            BubbleTextButton(onClick = { model.openNativeGroups(model.forum) }) { Text("查看吧群") }
            if (info == null) {
                if (model.feed.loading) LoadingRow() else Text(model.feed.error ?: "贴吧未返回吧资料")
            } else {
                PersonAvatar(Person(name = info.name, avatar = info.avatar), Modifier.size(66.dp))
                Text(info.description.ifBlank { "贴吧未返回简介" })
                Text("关注 ${info.members?.let(::compactNumber) ?: "—"} · 帖子 ${info.threads?.let(::compactNumber) ?: "—"} · 发言 ${info.posts?.let(::compactNumber) ?: "—"}")
                if (info.level > 0) Text("我的吧内等级 Lv.${info.level} ${info.levelName}${info.experience?.let { " · 经验 $it" }.orEmpty()}")
                if (info.managers.isNotEmpty()) { Text("吧务团队", style = MaterialTheme.typography.titleMedium); info.managers.forEach { person -> PersonRow(UserProfile(person)) { model.forumInfoOpen = false; model.openUser(person) } } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BubbleOutlinedButton(onClick = model::followForum, enabled = model.pending == null && model.feed.forumId.isNotBlank()) { Text(if (model.feed.followed) "取消关注该吧" else "关注该吧") }
                    val signed = info.signed || model.isForumSigned(model.forum)
                    Button(onClick = { model.signForum(model.forum) }, enabled = model.pending == null && !signed) { Text(if (signed) "今日已签到" else "签到") }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
