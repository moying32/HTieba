package dev.hydrogen.tieba.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hydrogen.tieba.data.Person

@Composable
internal fun ChatEntry(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().navigationBubble().clickable(onClick = onClick)
        .heightIn(min = 76.dp).padding(14.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ThemeIcon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
internal fun GroupDirectory(model: TiebaViewModel, modifier: Modifier, onBack: () -> Unit) {
    val state = model.groupChats
    var query by rememberSaveable { mutableStateOf(state.forum) }
    Column(modifier) {
        ScreenHeading("吧群", onBack)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            AppTextField(query, { query = it }, singleLine = true, label = { Text("吧名") }, modifier = Modifier.weight(1f),
                placeholder = { Text("输入想查看的吧") })
            BubbleTextButton(onClick = { model.findChatRooms(query) }, enabled = query.isNotBlank() && !state.loading) { Text("查找") }
        }
        Text("群列表与资料来自百度贴吧；查看不会自动加入群聊。", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 130.dp)) {
            if (state.loading) item { LoadingRow("正在读取吧群") }
            state.error?.let { error -> item { EmptyState("群列表暂不可用", error, action = "重试", onAction = { model.findChatRooms(query) }) } }
            if (state.searched && !state.loading && state.error == null && state.rooms.isEmpty()) item {
                EmptyState("这个吧暂未返回群聊", "部分吧没有公开群，或当前账号无权访问。")
            }
            items(state.rooms, key = { it.id }) { room ->
                Row(Modifier.fillMaxWidth().navigationBubble().clickable { model.openChatRoom(room) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PersonAvatar(Person(name = room.name, avatar = room.avatar))
                    Column(Modifier.weight(1f)) {
                        Text(room.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${room.forum}吧${room.members?.let { " · $it 位成员" }.orEmpty()}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
internal fun GroupConversation(model: TiebaViewModel, modifier: Modifier) {
    val state = model.groupChats
    val room = state.room ?: return
    Column(modifier) {
        ScreenHeading(room.name, model::closeChatRoom) {
            BubbleIconButton(onClick = { model.openChatRoom(room) }, enabled = !state.loading && !state.loadingMore) { ThemeIcon(Icons.Outlined.Refresh, "刷新群消息") }
        }
        Text("${room.forum}吧${room.members?.let { " · $it 位成员" }.orEmpty()}", modifier = Modifier.padding(horizontal = 20.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 130.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (room.announcement.isNotBlank()) item { Text("群公告：${room.announcement}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp)) }
            item { Text("历史消息 · 当前仅支持读取，发送与实时收取尚未接入", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
            if (state.loading) item { LoadingRow("正在读取群消息") }
            if (state.hasMore) item {
                BubbleTextButton(onClick = model::loadChatHistory, enabled = !state.loadingMore, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.loadingMore) "正在加载" else "查看更早消息")
                }
            }
            state.error?.let { error -> item { EmptyState("群消息暂不可用", error, action = "重试", onAction = {
                if (state.messages.isEmpty()) model.openChatRoom(room) else model.loadChatHistory()
            }) } }
            if (!state.loading && state.messages.isEmpty() && state.error == null) item { EmptyState("暂时没有群消息", "百度贴吧没有返回可显示的消息。") }
            items(state.messages, key = { it.id }) { message ->
                Column(Modifier.fillMaxWidth().bubbleBackground(androidx.compose.foundation.shape.RoundedCornerShape(24.dp)).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val person = Person(id = message.senderId.takeUnless { it == "0" }.orEmpty(),
                            name = if (message.senderId == "0") "系统消息" else "用户 ${message.senderId}")
                        BubbleTextButton(onClick = { model.openUser(person) }, enabled = person.id.isNotBlank()) { Text(person.name, style = MaterialTheme.typography.labelMedium) }
                        Spacer(Modifier.weight(1f))
                        Text(relativeTime(message.time), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(message.text.ifBlank { "[暂不支持的群消息类型 ${message.type}]" }, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
