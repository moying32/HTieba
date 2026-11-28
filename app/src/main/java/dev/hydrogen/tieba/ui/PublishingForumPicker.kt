package dev.hydrogen.tieba.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hydrogen.tieba.data.ForumItem
import dev.hydrogen.tieba.data.Person
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PublishingForumPicker(model: TiebaViewModel) {
    val draft = model.publishing ?: return
    val control = model.publishingForums
    val owner = model.account.uid
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    var allFollowed by rememberSaveable(owner) { mutableStateOf(false) }
    var filter by rememberSaveable(owner) { mutableStateOf("") }
    DisposableEffect(control, owner) {
        control.open(owner)
        onDispose { control.close() }
    }
    val select: (ForumItem) -> Unit = { forum ->
        if ((!draft.sending && !draft.uncertain)) {
            model.editPublishing(forum = forum.name)
            control.dismissCandidates(); allFollowed = false; filter = ""; keyboard?.hide()
        }
    }
    AppTextField(draft.forum, { value ->
        model.editPublishing(forum = value)
        control.edit(value.take(50))
    }, enabled = (!draft.sending && !draft.uncertain), label = { Text("发布吧") }, placeholder = { Text("输入吧名，搜索并选择") },
        suffix = { if (!draft.forum.endsWith("吧")) Text("吧") }, singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = {
            control.edit(draft.forum); keyboard?.hide()
        }))
    if (control.query.isNotBlank()) {
        Text("候选吧 · 百度贴吧搜索", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val listState = rememberLazyListState()
        LaunchedEffect(control.query) { listState.scrollToItem(0) }
        if (control.results.isNotEmpty()) LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp), state = listState) {
            items(control.results, key = { it.id.ifBlank { it.name } }) { item ->
                PublishingForumRow(item, draft.forum == item.name, (!draft.sending && !draft.uncertain)) { select(item) }
            }
        }
        if (control.searching) LoadingRow("正在搜索吧")
        control.searchError?.let { error ->
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            BubbleTextButton(onClick = { if (control.page > 0) control.more() else control.retrySearch() }, enabled = !control.searching) { Text("重试搜索") }
        }
        if (!control.searching && control.searchError == null && control.results.isEmpty())
            Text("未找到候选吧，可修改关键词；也可保留完整吧名继续编辑。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (control.hasMore && control.searchError == null) BubbleTextButton(onClick = control::more, enabled = !control.searching) { Text("更多候选吧") }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("我关注的吧${if (control.followingLoaded) " · ${control.followed.size}" else ""}", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BubbleTextButton(onClick = { allFollowed = true; filter = ""; keyboard?.hide() }, enabled = (!draft.sending && !draft.uncertain) && owner.isNotBlank()) { Text("查看全部") }
    }
    if (control.followingLoading) LoadingRow("正在读取关注的吧")
    control.followingError?.let { error ->
        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        BubbleTextButton(onClick = control::refreshFollowed, enabled = !control.followingLoading) { Text("重试读取关注") }
    }
    if (owner.isBlank()) Text("登录后显示关注的吧，仍可输入吧名。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    else if (control.followingLoaded && control.followed.isEmpty()) Text("暂未关注任何吧，可在上方输入搜索。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    control.followed.take(3).forEach { item -> PublishingForumRow(item, draft.forum == item.name, (!draft.sending && !draft.uncertain)) { select(item) } }
    if (control.followed.size > 3) Text("更多关注的吧可点“查看全部”，支持搜索和纵向滚动。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

    if (allFollowed) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { allFollowed = false }, sheetState = sheet) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(.88f).imePadding().navigationBarsPadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("我关注的吧", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    BubbleTextButton(onClick = { scope.launch { sheet.hide(); allFollowed = false } }) { Text("关闭") }
                }
                AppTextField(filter, { filter = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("搜索我关注的吧") })
                Text("${control.followed.size} 个关注 · 仅用于选择发布吧", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (control.followingLoading) LoadingRow("正在读取关注的吧")
                control.followingError?.let { error ->
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                BubbleTextButton(onClick = control::refreshFollowed, enabled = !control.followingLoading) { Text("刷新关注列表") }
                val shown = remember(control.followed, filter) { control.followed.filter { filter.isBlank() || it.name.contains(filter.trim(), true) } }
                val followedState = rememberLazyListState()
                LaunchedEffect(filter) { followedState.scrollToItem(0) }
                LazyColumn(Modifier.fillMaxWidth().weight(1f), state = followedState, contentPadding = PaddingValues(bottom = 16.dp)) {
                    if (shown.isEmpty() && !control.followingLoading && control.followingError == null) item {
                        Text(if (filter.isBlank()) "暂未关注任何吧" else "关注的吧中没有匹配结果", Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                    items(shown, key = { it.id.ifBlank { it.name } }) { item -> PublishingForumRow(item, draft.forum == item.name, (!draft.sending && !draft.uncertain)) { select(item) } }
                }
            }
            val ime = WindowInsets.isImeVisible
            BackHandler(enabled = ime) { keyboard?.hide() }
        }
    }
}

@Composable
private fun PublishingForumRow(item: ForumItem, current: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).insetBubbleBackground(selected = current, enabled = enabled)
        .clickable(enabled = enabled, role = Role.Button, onClickLabel = "选择发布到${item.name}", onClick = onClick)
        .semantics { selected = current }.heightIn(min = LocalTouchTarget.current).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PersonAvatar(Person(name = item.name, avatar = item.avatar), Modifier.size(34.dp))
        Text(if (item.name.endsWith("吧")) item.name else "${item.name}吧", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (current) ThemeIcon(Icons.Outlined.Check, "当前发布吧", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
    }
}
