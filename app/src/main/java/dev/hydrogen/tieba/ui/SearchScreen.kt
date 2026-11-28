package dev.hydrogen.tieba.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hydrogen.tieba.data.*

@Composable
fun SearchScreen(model: TiebaViewModel, state: PanelState, modifier: Modifier, onImage: (ImageGallery) -> Unit) {
    var input by rememberSaveable(state.key) { mutableStateOf(state.query) }
    var kind by rememberSaveable(state.key) { mutableStateOf(state.searchKind) }
    var expandedHistory by rememberSaveable { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(state.query) { input = state.query }
    LaunchedEffect(state.searchKind) { kind = state.searchKind }
    val keyboard = LocalSoftwareKeyboardController.current
    val search: (String) -> Unit = { query -> keyboard?.hide(); model.submitSearch(query, kind) }
    val idle = state.query.isBlank()
    val hasContent = if (idle) model.hotTopics.isNotEmpty() else
        state.results.threads.isNotEmpty() || state.results.forums.isNotEmpty() || state.results.users.isNotEmpty()
    Column(modifier.imePadding()) {
        ScreenHeading("搜索", model::closePanel)
        RefreshableList(if (idle) model.hotLoading else state.refreshing,
            { if (idle) model.loadHotTopics() else model.loadPanel(true) }, hasContent = hasContent) {
            LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp)) {
                item(key = "search-entry") {
                    GlassPanel(Modifier.padding(horizontal = 14.dp)) {
                        BoxWithConstraints(Modifier.padding(8.dp)) {
                            val wide = maxWidth >= 340.dp
                            @Composable fun Field(modifier: Modifier) {
                                AppTextField(input, { input = it.take(100) }, modifier = modifier,
                                    placeholder = { Text("搜索帖子、吧、用户", style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    singleLine = true, leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(20.dp)) },
                                    trailingIcon = { if (input.isNotEmpty()) BubbleIconButton(onClick = {
                                        input = ""; model.resetSearch()
                                    }) { Icon(Icons.Outlined.Close, "清除搜索", Modifier.size(18.dp)) } },
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { search(input) }))
                            }
                            @Composable fun SearchAction(modifier: Modifier = Modifier) {
                                Button(onClick = { search(input) }, enabled = input.isNotBlank(),
                                    modifier = modifier.heightIn(min = LocalTouchTarget.current)) { Text("搜索") }
                            }
                            if (wide) Row(verticalAlignment = Alignment.CenterVertically) {
                                Field(Modifier.weight(1f)); SearchAction(Modifier.padding(end = 4.dp))
                            } else Column {
                                Field(Modifier.fillMaxWidth())
                                SearchAction(Modifier.align(Alignment.End).padding(end = 4.dp, bottom = 4.dp))
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SearchKind.entries.forEach { scope ->
                            BubbleFilterChip(kind == scope, onClick = {
                                kind = scope
                                if (!idle && input.isNotBlank()) search(input)
                            }, label = { Text(scope.label) })
                        }
                    }
                }
                if (idle) {
                    if (model.searchHistory.isNotEmpty()) item(key = "history") {
                        LayeredInputCard(Modifier.padding(horizontal = 14.dp).padding(bottom = 16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("最近搜索", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                BubbleIconButton(onClick = { confirmClear = true }) {
                                    Icon(Icons.Outlined.DeleteOutline, "清空搜索历史", Modifier.size(18.dp))
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                model.searchHistory.take(if (expandedHistory) model.searchHistory.size else 4).forEach { query ->
                                    Box(Modifier.widthIn(min = LocalTouchTarget.current, max = 152.dp)
                                        .heightIn(min = LocalTouchTarget.current).clickable { input = query; search(query) },
                                        contentAlignment = Alignment.Center) {
                                        Box(Modifier.bubbleBackground().padding(horizontal = 12.dp, vertical = 5.dp)) {
                                            Text(query, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                            if (model.searchHistory.size > 4) BubbleTextButton(onClick = { expandedHistory = !expandedHistory }) {
                                Text(if (expandedHistory) "收起搜索历史" else "查看更早搜索历史（${model.searchHistory.size - 4}）",
                                    style = MaterialTheme.typography.labelMedium)
                                Icon(if (expandedHistory) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(16.dp))
                            }
                        }
                    }
                    item(key = "hot-heading") {
                        LayeredListPart(first = true) {
                            Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.LocalFireDepartment, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                    Text("贴吧热榜", style = MaterialTheme.typography.titleMedium)
                                    Text("来自百度贴吧", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                BubbleIconButton(onClick = model::loadHotTopics, enabled = !model.hotLoading) {
                                    Icon(Icons.Outlined.Refresh, "刷新热榜", Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                    if (model.hotLoading && model.hotTopics.isEmpty()) item(key = "hot-loading") {
                        LayeredListPart { LoadingRow("正在读取贴吧热榜") }
                    }
                    if (model.hotError != null) item(key = "hot-error") {
                        LayeredListPart {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Text(model.hotError.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                BubbleTextButton(onClick = model::loadHotTopics) { Text("重试热榜") }
                            }
                        }
                    }
                    items(model.hotTopics, key = { "hot:${it.id.ifBlank { it.title }}" }) { topic ->
                        LayeredListPart {
                            Column {
                                Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp).fillMaxWidth()
                                    .bubbleBackground(RoundedCornerShape(22.dp)).clickable { input = topic.title; search(topic.title) }
                                    .heightIn(min = LocalTouchTarget.current).padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.Top) {
                                    Text(topic.rank.toString(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                        color = if (topic.rank <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.widthIn(min = 28.dp).padding(end = 6.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(topic.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(topic.discussions?.let { "${compactNumber(it)} 热度" } ?: "查看讨论",
                                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 3.dp))
                                    }
                                    Icon(Icons.Outlined.NorthWest, null, Modifier.padding(start = 6.dp).size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
                            }
                        }
                    }
                    if (!model.hotLoading && model.hotError == null && model.hotTopics.isEmpty()) item(key = "hot-empty") {
                        LayeredListPart { EmptyState("热榜暂时没有内容", "百度贴吧本次未返回热榜条目", action = "刷新", onAction = model::loadHotTopics) }
                    }
                    item(key = "hot-bottom") { LayeredListPart(last = true) { Spacer(Modifier.height(12.dp)) } }
                } else {
                    item(key = "results-heading") {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("“${state.query}”的${state.searchKind.label}结果", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            BubbleTextButton(onClick = { keyboard?.hide(); model.openForum(input.trim().removeSuffix("吧")) }, enabled = input.isNotBlank()) { Text("直达吧") }
                        }
                    }
                    items(state.results.threads, key = { it.id }) { FeedThread(model, it, onImage) }
                    items(state.results.forums, key = { "${it.id}:${it.name}" }) { ForumRow(it) { model.openForum(it.name) } }
                    items(state.results.users, key = { it.person.id }) { PersonRow(it) { model.openUser(it.person) } }
                    if (state.loading && (!state.refreshing || !hasContent)) item { LoadingRow("正在搜索贴吧") }
                    if (state.error != null) item { EmptyState("搜索暂时不可用", state.error, action = "重试", onAction = { model.loadPanel(true) }) }
                    if (!state.loading && state.error == null && !hasContent) item {
                        EmptyState("没有找到相关内容", "试试更短的关键词，或切换帖子、吧和用户分类。", icon = Icons.Outlined.Search)
                    }
                    if (state.hasMore && !state.loading && state.error == null) {
                        item { PaginationFooter(false, null, true) { model.loadPanel() } }
                    }
                }
            }
        }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("清空最近搜索？") }, text = { Text("仅移除此设备的搜索历史。") },
        confirmButton = { BubbleTextButton(onClick = { model.clearSearchHistory(); confirmClear = false }) { Text("清空") } },
        dismissButton = { BubbleTextButton(onClick = { confirmClear = false }) { Text("取消") } })
}
