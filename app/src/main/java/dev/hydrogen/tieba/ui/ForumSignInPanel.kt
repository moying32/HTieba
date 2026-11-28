package dev.hydrogen.tieba.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.hydrogen.tieba.data.ForumSignStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumSignInPanel(model: TiebaViewModel) {
    val state = model.currentForumSignState
    var details by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("一键签到", style = MaterialTheme.typography.titleSmall)
                Text(when {
                    state.loading -> "正在读取完整关注吧列表…"
                    state.officialBatch -> "正在批量签到并核对结果…"
                    state.running -> "${state.completed} / ${state.entries.size} · ${state.entries.firstOrNull { it.status == ForumSignStatus.Signing }?.forum?.name?.let { "${it}吧" }.orEmpty()}"
                    state.stopped -> "已停止 · 已处理 ${state.completed} / ${state.entries.size}"
                    state.entries.isNotEmpty() -> "成功 ${state.signed} · 已签到 ${state.alreadySigned} · 失败 ${state.failed}"
                    else -> "优先使用贴吧批量签到"
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.running) BubbleTextButton(onClick = model::stopForumSignIn, enabled = !state.stopRequested) {
                Text(if (state.stopRequested) "正在停止" else "停止")
            } else FilledTonalButton(onClick = { model.startForumSignIn() }, enabled = model.pending == null && !model.account.loading) {
                Text(if (model.account.loggedIn) "一键签到" else "登录签到")
            }
        }
        if (state.running) {
            if (state.loading || state.officialBatch || state.entries.isEmpty()) AppLinearProgressIndicator(Modifier.fillMaxWidth())
            else AppLinearProgressIndicator(progress = { state.completed.toFloat() / state.entries.size }, modifier = Modifier.fillMaxWidth())
            if (state.stopRequested) Text("当前请求结束后停止，已完成结果保留。", style = MaterialTheme.typography.bodySmall)
        }
        state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        if (state.entries.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BubbleTextButton(onClick = { details = true }) { Text("查看签到明细") }
            if (!state.running && state.failed > 0) BubbleTextButton(onClick = { model.startForumSignIn(retryFailed = true) }, enabled = model.pending == null) { Text("仅重试失败（${state.failed}）") }
            if (!state.running && state.waiting > 0) BubbleTextButton(onClick = { model.startForumSignIn(resume = true) }, enabled = model.pending == null) { Text("继续未完成（${state.waiting}）") }
        }
    }
    if (details) ModalBottomSheet(onDismissRequest = { details = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(.78f), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
            item {
                Text("签到明细", style = MaterialTheme.typography.titleLarge)
                Text("${state.day} · 结果以百度贴吧返回为准", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 12.dp))
            }
            itemsIndexed(state.entries, key = { _, entry -> entry.forum.id.ifBlank { entry.forum.name } }) { _, entry ->
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Text("${entry.forum.name}吧", style = MaterialTheme.typography.titleSmall)
                    Text(when (entry.status) {
                        ForumSignStatus.Waiting -> "待签到"
                        ForumSignStatus.Signing -> "签到中"
                        ForumSignStatus.Signed -> "签到成功"
                        ForumSignStatus.AlreadySigned -> "今日已签到"
                        ForumSignStatus.Failed -> entry.message.ifBlank { "签到失败，可重试" }
                    }, style = MaterialTheme.typography.bodySmall,
                        color = if (entry.status == ForumSignStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
