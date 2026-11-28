package dev.hydrogen.tieba.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentComposer(model: TiebaViewModel) {
    val state = model.composer ?: return
    val focus = remember { FocusRequester() }
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var clearConfirmed by remember { mutableStateOf(false) }
    var showOfficialFallback by remember(state.thread.id) { mutableStateOf(false) }
    val officialUri = state.thread.id.takeIf { it.all { char -> char in '0'..'9' } && it.toLongOrNull()?.let { id -> id > 0 } == true }
        ?.let { Uri.parse("com.baidu.tieba://unidispatch/pb").buildUpon().appendQueryParameter("tid", it).build() }
    ModalBottomSheet(onDismissRequest = model::closeComposer, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { model.composer?.sending != true || it != SheetValue.Hidden }),
        modifier = Modifier.imePadding(), containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text((state.target ?: state.parent)?.let { "回复 ${it.author.name}" } ?: "参与讨论", style = MaterialTheme.typography.titleLarge)
            Text(when {
                state.target != null -> "回复第 ${state.parent?.floor} 楼中的 ${state.target.author.name}，保留子回复指向"
                state.parent != null -> "回复第 ${state.parent.floor} 楼"
                else -> "回复主帖"
            }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text((state.target ?: state.parent)?.content?.joinToString("") { it.text } ?: state.thread.title,
                maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (officialUri != null) {
                BubbleTextButton(onClick = {
                    keyboard?.hide()
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, officialUri).setPackage("com.baidu.tieba"))
                    } catch (_: ActivityNotFoundException) {
                        showOfficialFallback = true
                    } catch (_: SecurityException) {
                        showOfficialFallback = true
                    }
                }, enabled = !state.sending, modifier = Modifier.heightIn(min = LocalTouchTarget.current)) {
                    Text("去百度贴吧评论")
                }
            }
            AppTextField(state.text, model::editComment, enabled = !state.sending && !state.uncertain,
                label = { Text("评论内容") }, placeholder = { Text("说说你的想法…") },
                modifier = Modifier.fillMaxWidth().focusRequester(focus), minLines = 4, maxLines = 7,
                supportingText = { Text("${state.text.length} / 10000 · 草稿自动保存") })
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (state.uncertain) {
                BubbleTextButton(onClick = { model.officialThread(state.thread.id) }) { Text("查看原帖确认结果") }
                BubbleTextButton(onClick = { model.acknowledgeCommentResult() }, enabled = model.pending == null) { Text("已确认没有发送，允许重试") }
                BubbleTextButton(onClick = { clearConfirmed = true }, enabled = model.pending == null) { Text("已找到回复，清除此草稿") }
            }
            if (state.sending) {
                BubbleTextButton(onClick = model::cancelSubmission) { Text("停止等待，保留草稿") }
                Text("已发出的请求不能撤回；停止后先核对原帖，避免重复评论。", style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BubbleTextButton(onClick = model::closeComposer, enabled = !state.sending) { Text("保存并关闭") }
                Spacer(Modifier.weight(1f))
                if (!model.account.loggedIn) Button(onClick = model::login, enabled = !model.account.loading) { Text("登录后发送") }
                else Button(onClick = model::sendComment,
                    enabled = state.text.isNotBlank() && !state.sending && !state.uncertain && model.pending == null && !model.account.loading) {
                    if (state.sending) AppCircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(if (state.sending) " 正在发送" else "发送评论")
                }
            }
        }
    }
    if (showOfficialFallback) AlertDialog(onDismissRequest = { showOfficialFallback = false },
        title = { Text("无法打开百度贴吧客户端") },
        text = { Text("请确认已安装百度贴吧。也可以打开同一篇帖子的网页。") },
        confirmButton = { BubbleTextButton(onClick = { showOfficialFallback = false; model.officialThread(state.thread.id) }) { Text("打开网页原帖") } },
        dismissButton = { BubbleTextButton(onClick = { showOfficialFallback = false }) { Text("取消") } })
    if (clearConfirmed) AlertDialog(onDismissRequest = { clearConfirmed = false }, title = { Text("清除已发送的草稿？") },
        text = { Text("确认已在原帖找到对应回复。此操作仅清除本机草稿，不会重发或删除云端回复。") },
        confirmButton = { BubbleTextButton(onClick = { clearConfirmed = false; model.acknowledgeCommentResult(true) }) { Text("已核对，清除草稿") } },
        dismissButton = { BubbleTextButton(onClick = { clearConfirmed = false }) { Text("继续保留") } })
    LaunchedEffect(Unit) { if (!state.uncertain) focus.requestFocus() }
}
