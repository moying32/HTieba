package dev.hydrogen.tieba.ui

import android.content.ClipData
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.hydrogen.tieba.data.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupDetailsSheet(model: TiebaViewModel, c: Conversation, onDismiss: () -> Unit,
    onLocate: (ChatMessage) -> Unit, onImage: (ChatMessage) -> Unit) {
    val room = c.room ?: return
    val chat = model.chats
    var tab by rememberSaveable(c.key) { mutableStateOf("资料") }
    var query by rememberSaveable(c.key) { mutableStateOf("") }
    var join by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val people = remember(c.messages) { GroupChatRules.participants(c.messages) }
    val content = c.messages.filter { !it.revoked }
    val tabs = listOf("资料","发言者","图片","帖子","本机收藏","管理")
    ChatSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).navigationBarsPadding().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PersonAvatar(Person(room.id,room.name,room.avatar),Modifier.size(44.dp))
                Column(Modifier.weight(1f)) {
                    Text(room.name,style = MaterialTheme.typography.titleLarge,maxLines = 2,overflow = TextOverflow.Ellipsis)
                    Text(room.members?.let { "$it 位成员" } ?: "百度未返回成员数",style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                tabs.forEach { label -> BubbleFilterChip(tab == label,{ tab = label; query = "" },{ Text(label) }) }
            }
            if(tab != "资料" && tab != "管理") ChatField(query,{ query = it },if(tab == "发言者") "搜索已加载的发言者" else "搜索已加载内容",Modifier.fillMaxWidth())
            if(tab == "管理") GroupModerationContent(model,c,Modifier.fillMaxWidth().weight(1f))
            else LazyColumn(Modifier.fillMaxWidth().weight(1f),contentPadding = PaddingValues(bottom = 20.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if(tab == "资料") {
                    item { Text(if(c.syncedAt > 0) "上次消息同步：${chatSyncTime(c.syncedAt)}" else "尚未完成同步，可能是本机缓存",style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if(!room.detailsLoaded) item { Text("群详细资料尚未读取成功，请刷新重试。",style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.error) }
                    chat.groupDetailErrors[c.key]?.let { warning -> item { ChatError(warning, { chat.refresh() }) } }
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if(room.forum.isNotBlank()) BubbleTextButton(onClick = { onDismiss(); model.openForum(room.forum) }) { Text("${room.forum.removeSuffix("吧")}吧") }
                            BubbleTextButton(onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("吧群 ID",room.id))) } }) { Text("群号 ${room.id} · 复制") }
                        }
                        if(room.forumId.isNotBlank()) Text("所属吧 ID：${room.forumId}",style = MaterialTheme.typography.labelSmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item {
                        Text("群公告",style = MaterialTheme.typography.titleSmall)
                        Text(room.announcement.ifBlank { "百度未返回群公告" },style = MaterialTheme.typography.bodyMedium)
                    }
                    item {
                        Text("发言权限",style = MaterialTheme.typography.titleSmall)
                        Text(when(room.talkBanned) { true -> "当前账号已被禁言"; false -> "当前账号未被禁言（以本次资料为准）"; null -> "百度未返回禁言状态" },style = MaterialTheme.typography.bodyMedium)
                        room.talkThreshold?.let { Text("发言等级门槛：Lv.$it；实际权限由百度确认。",style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    if(room.bots.isNotEmpty()) {
                        item { Text("群机器人",style = MaterialTheme.typography.titleSmall) }
                        items(room.bots.distinctBy { it.id.ifBlank { it.name } },key = { "bot:${it.id}:${it.name}" }) { person ->
                            GroupPersonRow(person,person.id.toLongOrNull()?.let { it > 0 } == true) { onDismiss(); model.openUser(person) }
                        }
                    }
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            BubbleTextButton(onClick = { chat.pin(c.key) }) { Text(if(c.pinned) "取消置顶" else "置顶会话") }
                            BubbleTextButton(onClick = { chat.refresh() },enabled = !chat.loading && !chat.actionBusy) { Text(if(chat.loading) "读取中…" else "刷新资料与消息") }
                            BubbleTextButton(onClick = { join = true },enabled = !chat.actionBusy && !chat.sending && !chat.loading && room.detailsLoaded) { Text("申请加入群聊") }
                        }
                        chat.actionResult?.let { Text(it,style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if(chat.actionBusy) LoadingRow("正在提交群操作")
                        chat.error?.let { ChatError(it,chat::retryRead) }
                    }
                    item { Text("发言者和共享内容仅从已加载的消息整理。完整成员名单、退出群聊、附件发送及后台通知尚未接入。置顶、收藏和未看标记保存在本机。",style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else if(tab == "发言者") {
                    item { Text("已加载消息的发言者 · ${people.size} 人，非完整群成员名单",style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    val shown = people.filter { query.isBlank() || it.name.contains(query,true) || it.id.contains(query) }
                    if(shown.isEmpty()) item { GroupIndexEmpty(query) }
                    items(shown,key = { it.id }) { person -> GroupPersonRow(person,true) { onDismiss(); model.openUser(person) } }
                } else {
                    val shown = content.filter { message ->
                        (when(tab) { "图片" -> message.image.isNotBlank(); "帖子" -> message.link.isNotBlank(); else -> "${message.senderId}:${message.id}" in c.saved }) &&
                            (query.isBlank() || message.text.contains(query,true) || message.person.name.contains(query,true) || message.link.contains(query,true))
                    }.asReversed()
                    item { Text("已加载范围 · ${shown.size} 条",style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if(shown.isEmpty()) item { GroupIndexEmpty(query) }
                    items(shown,key = { "${it.senderId}:${it.id}" }) { message ->
                        Column(Modifier.fillMaxWidth().navigationBubble().padding(horizontal = 12.dp,vertical = 8.dp)) {
                            Text("${message.person.name} · ${chatSyncTime(message.time)}",style = MaterialTheme.typography.labelSmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if(message.image.isNotBlank()) AsyncImage(imageLoader = LocalAppImageLoader.current, model = message.image, contentDescription = "查看群聊图片",modifier = Modifier.fillMaxWidth().height(128.dp).clip(RoundedCornerShape(14.dp)).clickable { onImage(message) })
                            Text(GroupChatRules.preview(message),style = MaterialTheme.typography.bodyMedium,maxLines = 3,overflow = TextOverflow.Ellipsis)
                            FlowRow {
                                BubbleTextButton(onClick = { onLocate(message) }) { Text("定位原消息") }
                                if(message.link.isNotBlank()) BubbleTextButton(onClick = { message.link.substringAfter("/p/","").takeWhile { it.isDigit() }.takeIf { it.isNotBlank() }?.let { onDismiss(); model.openThread(it) } }) { Text("打开帖子") }
                            }
                        }
                    }
                    if(chat.hasMore) item { BubbleTextButton(onClick = { chat.refresh(true) },enabled = !chat.loading) { Text(if(chat.loadingOlder) "加载中…" else "加载更早消息以扩展索引") } }
                    chat.error?.let { item { ChatError(it,chat::retryRead) } }
                }
            }
        }
    }
    if(join) AlertDialog(onDismissRequest = { join = false },title = { Text("向百度提交加入请求？") },text = { Text("目标群：${room.name}（${room.id}）。提交后将重新读取群资料；是否入群成功及能否发言，以百度返回状态为准。") },confirmButton = { BubbleTextButton(onClick = { join = false; chat.joinCurrentGroup() }) { Text("提交请求") } },dismissButton = { BubbleTextButton(onClick = { join = false }) { Text("取消") } })
}

@Composable
private fun GroupPersonRow(person: Person, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().navigationBubble().clickable(enabled = enabled,onClick = onClick).heightIn(min = LocalTouchTarget.current).padding(horizontal = 12.dp,vertical = 6.dp),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PersonAvatar(person)
        Column(Modifier.weight(1f)) {
            Text(person.name,style = MaterialTheme.typography.bodyMedium,maxLines = 1,overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(person.id.takeIf { it.isNotBlank() }?.let { "UID $it" },person.level.takeIf { it > 0 }?.let { "Lv.$it" },person.role.takeIf { it.isNotBlank() }).joinToString(" · "),style = MaterialTheme.typography.labelSmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GroupIndexEmpty(query: String) { Text(if(query.isBlank()) "已加载的消息中暂无此类内容" else "已加载范围中没有匹配结果",Modifier.padding(vertical = 16.dp),style = MaterialTheme.typography.bodyMedium,color = MaterialTheme.colorScheme.onSurfaceVariant) }
