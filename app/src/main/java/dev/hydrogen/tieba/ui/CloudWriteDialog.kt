package dev.hydrogen.tieba.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CloudWriteDialog(model: TiebaViewModel) {
    val state = model.cloudWrite?.takeIf { model.cloudWriteVisible } ?: return
    var acknowledge by remember(state.threadId, state.remove) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = model::dismissCloudWrite,
        title = { Text(when {
            state.busy -> "正在提交或核对云收藏"
            state.confirmed -> if (state.remove) "已核对移出云收藏" else "已核对加入云收藏"
            state.uncertain -> "云收藏结果待核对"
            else -> "云收藏操作未完成"
        }) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(state.title.ifBlank { "帖子 ${state.threadId}" })
            Text(if (state.remove) "操作：从百度账号云收藏移出" else "操作：添加到百度账号云收藏", style = MaterialTheme.typography.bodySmall)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            when {
                state.busy -> {
                    LoadingRow("请稍候…")
                    BubbleTextButton(onClick = model::cancelSubmission) { Text("停止等待") }
                    Text("已发出的请求无法撤回。停止后应用会保留核对记录。", style = MaterialTheme.typography.bodySmall)
                }
                state.uncertain -> {
                    BubbleTextButton(onClick = model::verifyCloudWrite, enabled = model.pending == null) { Text("重新核对（不重发）") }
                    BubbleTextButton(onClick = { model.officialThread(state.threadId) }) { Text("在官方页面核对") }
                    BubbleTextButton(onClick = { acknowledge = true }) { Text("已人工核对，清除恢复记录") }
                }
            }
        } },
        confirmButton = { BubbleTextButton(onClick = model::dismissCloudWrite) { Text(if (state.busy || state.uncertain) "稍后查看" else "完成") } })
    if (acknowledge) AlertDialog(onDismissRequest = { acknowledge = false }, title = { Text("已在官方端核对结果？") },
        text = { Text("清除记录后可再次操作。此按钮只清除本机恢复记录，不会向百度重新提交。") },
        confirmButton = { BubbleTextButton(onClick = { acknowledge = false; model.acknowledgeCloudWrite() }) { Text("已核对，清除记录") } },
        dismissButton = { BubbleTextButton(onClick = { acknowledge = false }) { Text("继续保留") } })
}
