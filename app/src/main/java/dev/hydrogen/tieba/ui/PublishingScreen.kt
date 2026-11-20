package dev.hydrogen.tieba.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hydrogen.tieba.data.DraftImage

@Composable
fun PublishingScreen(model: TiebaViewModel) {
    val draft = model.publishing ?: return
    val context = LocalContext.current
    val account = model.account
    var confirm by remember { mutableStateOf(false) }
    var photoError by remember { mutableStateOf<String?>(null) }
    val bodyFocus = remember { FocusRequester() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) photoError = null
        val images = uris.mapNotNull { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                DraftImage(uri.toString())
            }.getOrElse { photoError = "无法保留这张图片的读取权限，请从系统相册重新选择"; null }
        }
        model.editPublishing(images = draft.images + images)
    }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).navigationBarsPadding()
        .padding(horizontal = 14.dp).padding(top = 4.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LayeredInputCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PersonAvatar(account.identity.copy(name = account.name.ifBlank { "未登录" }, avatar = account.avatar), Modifier.size(38.dp))
                Column(Modifier.weight(1f)) {
                    Text(account.name.ifBlank { "未登录" }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("发布到百度贴吧", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            PublishingForumPicker(model)
        }
        LayeredInputCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("帖子标题", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("${draft.title.length} / 90", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BasicTextField(draft.title, { model.editPublishing(title = it) }, enabled = !draft.sending,
                modifier = Modifier.fillMaxWidth().heightIn(min = LocalTouchTarget.current).semantics { contentDescription = "帖子标题" },
                textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { bodyFocus.requestFocus() }),
                decorationBox = { field -> Box(Modifier.padding(vertical = 8.dp)) {
                    if (draft.title.isEmpty()) Text("为讨论写一个标题", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    field()
                } })
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            Text("正文", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(draft.text, { model.editPublishing(text = it) }, enabled = !draft.sending, minLines = 6,
                modifier = Modifier.fillMaxWidth().heightIn(min = LocalTouchTarget.current).focusRequester(bodyFocus).semantics { contentDescription = "帖子正文" },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { field -> Box {
                    if (draft.text.isEmpty()) Text("分享你的想法…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    field()
                } })
            Text("${draft.text.length} / 10000 · 草稿自动保存在本机", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LayeredInputCard {
            Text("图片", style = MaterialTheme.typography.titleSmall)
            BubbleOutlinedButton(onClick = { picker.launch(arrayOf("image/*")) }, enabled = !draft.sending && draft.images.size < 9) {
                Icon(Icons.Outlined.AddPhotoAlternate, null, Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("添加图片（${draft.images.size}/9）")
            }
            draft.images.chunked(3).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { image -> Column(Modifier.weight(1f)) {
                    PostImage(image.uri, Modifier.fillMaxWidth().aspectRatio(1f))
                    BubbleTextButton(onClick = { model.editPublishing(images = draft.images - image) }, enabled = !draft.sending) { Text("移除") }
                } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            } }
            if (draft.images.isEmpty()) Text("最多 9 张，确认发布后上传", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            photoError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
        if (draft.error != null || draft.uncertain || draft.sending) LayeredInputCard {
            draft.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (draft.uncertain) {
                Text("上次发布结果未确认，请先核对我的帖子，避免重复发布。", color = MaterialTheme.colorScheme.error)
                BubbleTextButton(onClick = { model.openPanel(PanelKind.Threads) }) { Text("核对我的帖子") }
                BubbleTextButton(onClick = model::acknowledgePublishResult) { Text("已确认未发布，允许重试") }
            }
            if (draft.sending) LoadingRow(draft.progress)
        }
        Button(onClick = { confirm = true }, enabled = !draft.sending && !draft.uncertain && model.pending == null && draft.forum.isNotBlank() && draft.title.isNotBlank() && (draft.text.isNotBlank() || draft.images.isNotEmpty()),
            modifier = Modifier.fillMaxWidth().heightIn(min = LocalTouchTarget.current)) { Text("预览并发布") }
        Text("只有确认发布后才会上传所选图片并提交。视频、语音、投票等更多发布类型仍在接入。", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("确认发布到 ${draft.forum.removeSuffix("吧")}吧？") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(draft.title, style = MaterialTheme.typography.titleMedium); Text(draft.text)
            if (draft.images.isNotEmpty()) Text("包含 ${draft.images.size} 张图片，将上传到百度贴吧。")
        } },
        confirmButton = { BubbleTextButton(onClick = { confirm = false; model.publishThread() }) { Text("确认发布") } },
        dismissButton = { BubbleTextButton(onClick = { confirm = false }) { Text("继续编辑") } })
}
