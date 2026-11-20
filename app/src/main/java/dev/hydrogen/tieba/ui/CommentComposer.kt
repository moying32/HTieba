package dev.hydrogen.tieba.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentComposer(model: TiebaViewModel) {
    val state = model.composer ?: return
    val focus = remember { FocusRequester() }
    ModalBottomSheet(onDismissRequest = model::closeComposer, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { model.composer?.sending != true || it != SheetValue.Hidden }),
        modifier = Modifier.imePadding(), containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text((state.target ?: state.parent)?.let { "回复 ${it.author.name}" } ?: "参与讨论", style = MaterialTheme.typography.titleLarge)
            Text((state.target ?: state.parent)?.content?.joinToString("") { it.text } ?: state.thread.title,
                maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(state.text, model::editComment, enabled = !state.sending,
                label = { Text("评论内容") }, placeholder = { Text("说说你的想法…") },
                modifier = Modifier.fillMaxWidth().focusRequester(focus), minLines = 4, maxLines = 7,
                supportingText = { Text("${state.text.length} / 10000 · 草稿自动保存") })
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (state.uncertain) {
                BubbleTextButton(onClick = { model.officialThread(state.thread.id) }) { Text("查看原帖确认结果") }
                BubbleTextButton(onClick = model::acknowledgeCommentResult) { Text("已确认没有发送，允许重试") }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BubbleTextButton(onClick = model::closeComposer, enabled = !state.sending) { Text("保存并关闭") }
                Spacer(Modifier.weight(1f))
                if (!model.account.loggedIn) Button(onClick = model::login, enabled = !model.account.loading) { Text("登录后发送") }
                else Button(onClick = model::sendComment,
                    enabled = state.text.isNotBlank() && !state.sending && !state.uncertain && model.pending == null && !model.account.loading) {
                    if (state.sending) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(if (state.sending) " 正在发送" else "发送评论")
                }
            }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}
