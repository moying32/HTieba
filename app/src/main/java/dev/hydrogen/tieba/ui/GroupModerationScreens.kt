package dev.hydrogen.tieba.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hydrogen.tieba.data.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupModerationSheet(model: TiebaViewModel, c: Conversation, person: Person, onDismiss: () -> Unit) {
    ChatSheet(onDismiss) {
        GroupModerationContent(model,c,Modifier.fillMaxWidth().fillMaxHeight(.9f).navigationBarsPadding().padding(horizontal = 16.dp),person)
    }
}

@Composable
internal fun GroupModerationContent(model: TiebaViewModel, c: Conversation, modifier: Modifier, initialPerson: Person? = null) {
    val room = c.room ?: return
    val chat = model.chats; val control = chat.moderation
    DisposableEffect(model.account.uid,room.id) {
        control.open(room,initialPerson)
        onDispose { control.close() }
    }
    var duration by rememberSaveable(room.id) { mutableStateOf(GroupBlockDuration.Hour) }
    var query by rememberSaveable(room.id) { mutableStateOf("") }
    var chooseSpeaker by rememberSaveable(room.id) { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<Pair<GroupModerationTarget,Boolean>?>(null) }
    val people = remember(c.messages) { GroupChatRules.participants(c.messages) }.filter { it.id != model.account.uid }
    val target = control.target
    val validRoom = room.id.toLongOrNull()?.let { it > 0 } == true && room.forumId.toLongOrNull()?.let { it > 0 } == true
    val ready = !chat.actionBusy && !chat.sending && !chat.loading && !control.targetLoading && target != null && validRoom
    val scroll = rememberLazyListState(); val scope = rememberCoroutineScope()
    LazyColumn(modifier,state = scroll,contentPadding = PaddingValues(bottom = 24.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("群禁言管理",style = MaterialTheme.typography.titleMedium)
            Text("需要当前账号具有对应吧务权限，百度会验证每次请求。这里只管理发言限制，不代表完整成员名单。",style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
            if(!validRoom) Text("群或所属吧编号缺失，请返回刷新群资料后重试。",style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.error)
        }
        item {
            Column(Modifier.fillMaxWidth().navigationBubble().padding(12.dp),verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("核对目标账号",style = MaterialTheme.typography.titleSmall)
                if(control.expectedPortrait.isNotBlank()) {
                    Text("名单账号：${control.expectedName}",style = MaterialTheme.typography.bodyMedium)
                    Text("请输入此账号的贴吧 UID；读取后将核对头像标识是否一致。",style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BubbleTextButton(onClick = control::clearExpected,enabled = !chat.actionBusy) { Text("取消名单账号选择") }
                }
                AppTextField(control.uid,control::changeUid, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("目标账号的贴吧 UID") }, enabled = !chat.actionBusy)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    BubbleTextButton(onClick = control::prepare,enabled = !control.targetLoading && !chat.actionBusy && control.uid.isNotBlank()) { Text("读取账号资料") }
                    BubbleTextButton(onClick = { chooseSpeaker = !chooseSpeaker }, enabled = !chat.actionBusy) { Text(if(chooseSpeaker) "收起发言者" else "从已加载发言者选择") }
                }
                if(control.targetLoading) LoadingRow("正在核对账号")
                control.targetError?.let { Text(it,style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.error) }
                if(target != null) {
                    Row(verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PersonAvatar(target.person,Modifier.size(36.dp))
                        Column(Modifier.weight(1f)) {
                            Text(target.person.name,style = MaterialTheme.typography.titleSmall,maxLines = 2,overflow = TextOverflow.Ellipsis)
                            Text("UID ${target.person.id}",style = MaterialTheme.typography.labelSmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text("禁言时长",style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        GroupBlockDuration.entries.forEach { option -> BubbleFilterChip(duration == option,{ duration = option },{ Text(option.label) }, enabled = !chat.actionBusy) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        BubbleTextButton(onClick = { confirmation = target to false },enabled = ready) { Text("禁言此账号") }
                        BubbleTextButton(onClick = { confirmation = target to true },enabled = ready) { Text("解除禁言") }
                    }
                }
            }
        }
        if(chooseSpeaker) {
            item { Text("仅含已加载消息中的发言者",style = MaterialTheme.typography.labelSmall,color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if(people.isEmpty()) item { Text("暂无可选账号，可输入 UID 读取。",style = MaterialTheme.typography.bodySmall) }
            items(people,key = { "speaker:${it.id}" }) { person ->
                Row(Modifier.fillMaxWidth().navigationBubble().padding(horizontal = 12.dp,vertical = 4.dp),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PersonAvatar(person,Modifier.size(32.dp))
                    Text(person.name,Modifier.weight(1f),style = MaterialTheme.typography.bodyMedium,maxLines = 2,overflow = TextOverflow.Ellipsis)
                    BubbleTextButton(onClick = { control.choosePerson(person); chooseSpeaker = false; scope.launch { scroll.scrollToItem(0) } }, enabled = !chat.actionBusy) { Text("选择") }
                }
            }
        }
        if(chat.actionBusy) item { LoadingRow("正在向百度提交群操作，请勿重复提交") }
        chat.actionResult?.let { result -> item { Text(result,style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("禁言名单",Modifier.weight(1f),style = MaterialTheme.typography.titleSmall)
                BubbleTextButton(onClick = { control.refresh() },enabled = !control.loading && !chat.actionBusy) { Text("刷新名单") }
            }
            if(control.syncedAt > 0) Text("读取于 ${chatSyncTime(control.syncedAt)} · 已加载 ${control.listing.users.size} / ${control.listing.total ?: 0}",style = MaterialTheme.typography.labelSmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppTextField(query,{ query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("筛选已加载的禁言账号") })
        }
        if(control.loading) item { LoadingRow(if(control.listing.page == 0) "正在读取禁言名单" else "正在更新禁言名单") }
        control.listError?.let { error -> item {
            ChatError(error,control::retry)
            if(control.listing.page > 0) Text("以下为上次读取的名单，可能已经变化。",style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        val shown = control.listing.users.filter { query.isBlank() || it.name.contains(query,true) || it.portrait.contains(query,true) }
        if(shown.isEmpty() && control.listing.page > 0 && !control.loading && control.listError == null) item {
            Text(if(query.isNotBlank()) "已加载范围内无匹配账号" else "本次读取的名单中没有禁言账号",Modifier.padding(vertical = 16.dp),style = MaterialTheme.typography.bodyMedium)
        }
        items(shown,key = { "blocked:${it.portrait}" }) { user ->
            Column(Modifier.fillMaxWidth().navigationBubble().padding(horizontal = 12.dp,vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PersonAvatar(Person(name = user.name,avatar = user.avatar),Modifier.size(36.dp))
                    Column(Modifier.weight(1f)) {
                        Text(user.name,style = MaterialTheme.typography.titleSmall,maxLines = 2,overflow = TextOverflow.Ellipsis)
                        Text(user.remaining.ifBlank { "百度未返回剩余时长" },style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                BubbleTextButton(onClick = { control.chooseBlocked(user,people); scope.launch { scroll.scrollToItem(0) } }, enabled = !chat.actionBusy) { Text("选择并核对 UID") }
            }
        }
        if(control.listing.hasMore) item {
            BubbleTextButton(onClick = { control.refresh(true) },enabled = !control.loading && !chat.actionBusy,modifier = Modifier.fillMaxWidth()) { Text("加载更多禁言账号") }
            if(query.isNotBlank()) Text("筛选只覆盖已加载页面。",style = MaterialTheme.typography.labelSmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    confirmation?.let { (person,unblock) ->
        AlertDialog(onDismissRequest = { confirmation = null },title = { Text(if(unblock) "解除此账号的禁言？" else "禁言此账号 ${duration.label}？") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("群：${room.name}（${room.id}）")
                Text("账号：${person.person.name}\nUID：${person.person.id}")
                Text("将修改百度贴吧中的群发言权限。操作结果由百度确认；网络中断时先刷新核对，不会自动重试。",style = MaterialTheme.typography.bodySmall)
            } },confirmButton = { BubbleTextButton(onClick = { confirmation = null; chat.moderate(person,duration,unblock) },enabled = ready && control.target == person) { Text("确认提交") } },
            dismissButton = { BubbleTextButton(onClick = { confirmation = null }) { Text("取消") } })
    }
}
