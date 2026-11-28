package dev.hydrogen.tieba.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hydrogen.tieba.data.DraftImage
import dev.hydrogen.tieba.data.UploadStage

@Composable
fun PublishingScreen(model: TiebaViewModel) {
    val draft = model.publishing ?: return
    val context = LocalContext.current
    val account = model.account
    val editable = !draft.sending && !draft.uncertain
    var confirm by remember { mutableStateOf(false) }
    var clearConfirmed by remember { mutableStateOf(false) }
    var photoError by remember { mutableStateOf<String?>(null) }
    val bodyFocus = remember { FocusRequester() }
    fun addPhotos(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        val current = model.publishing?.takeIf { it.owner == draft.owner && !it.sending && !it.uncertain }
        if (current == null) return
        photoError = null
        val newUris = uris.distinct().filterNot { uri -> current.images.any { it.uri == uri.toString() } }
        val slots = 9 - current.images.size
        if (newUris.size > slots) photoError = "最多添加 9 张图片，超出的图片未加入草稿"
        val images = newUris.take(slots).mapNotNull { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                DraftImage(uri.toString())
            }.getOrElse { photoError = "无法保留这张图片的读取权限，请从系统相册重新选择"; null }
        }
        model.editPublishing(images = current.images + images)
    }
    val singlePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) addPhotos(listOf(uri))
    }
    val multiPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9), ::addPhotos)
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).navigationBarsPadding()
        .padding(horizontal = 14.dp).padding(top = 4.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LayeredInputCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PersonAvatar(account.identity.copy(name = account.name.ifBlank { "未登录" }, avatar = account.avatar), Modifier.size(42.dp))
                Column(Modifier.weight(1f)) {
                    Text(account.name.ifBlank { "未登录" }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("发布到百度贴吧", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            PublishingForumPicker(model)
        }
        LayeredInputCard {
            AppTextField(draft.title, { model.editPublishing(title = it) }, enabled = editable,
                modifier = Modifier.fillMaxWidth(), label = { Text("帖子标题") },
                placeholder = { Text("为讨论写一个标题") },
                supportingText = { Text("${draft.title.length} / 90") },
                maxLines = 2, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { bodyFocus.requestFocus() }))
            AppTextField(draft.text, { model.editPublishing(text = it) }, enabled = editable, minLines = 4, maxLines = 8,
                modifier = Modifier.fillMaxWidth().focusRequester(bodyFocus), label = { Text("帖子正文") },
                placeholder = { Text("分享你的想法…") },
                supportingText = { Text("${draft.text.length} / 10000 · 草稿自动保存在本机") })
        }
        LayeredInputCard {
            Text("图片", style = MaterialTheme.typography.titleSmall)
            BubbleOutlinedButton(onClick = {
                val remaining = 9 - draft.images.size
                if (remaining == 1) singlePicker.launch(photoPickerRequest())
                else if (remaining > 1) multiPicker.launch(photoPickerRequest(remaining))
            }, enabled = editable && draft.images.size < 9) {
                ThemeIcon(Icons.Outlined.AddPhotoAlternate, null, Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("添加图片（${draft.images.size}/9）")
            }
            draft.images.chunked(3).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { image -> Column(Modifier.weight(1f)) {
                    PostImage(image.uri, Modifier.fillMaxWidth().aspectRatio(1f))
                    val index = draft.images.indexOf(image)
                    Text("${index + 1} · " + when (image.stage) {
                        UploadStage.Queued -> "等待上传"
                        UploadStage.Preparing -> "处理图片"
                        UploadStage.Uploading -> if (image.percent >= 100) "等待百度确认" else "上传 ${image.percent}%"
                        UploadStage.Uploaded -> "已上传，可复用"
                        UploadStage.Failed -> "上传失败"
                    }, style = MaterialTheme.typography.labelSmall)
                    image.error?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
                    Row {
                        BubbleIconButton(onClick = { model.movePublishingImage(index, -1) }, enabled = editable && index > 0) { ThemeIcon(Icons.AutoMirrored.Outlined.ArrowBack, "第 ${index + 1} 张前移") }
                        BubbleIconButton(onClick = { model.movePublishingImage(index, 1) }, enabled = editable && index < draft.images.lastIndex) { ThemeIcon(Icons.AutoMirrored.Outlined.ArrowForward, "第 ${index + 1} 张后移") }
                    }
                    BubbleTextButton(onClick = { model.editPublishing(images = draft.images - image) }, enabled = editable) { Text("移除") }
                } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            } }
            if (draft.images.isEmpty()) Text("最多 9 张，确认发布后上传", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("图片按顺序上传，最长边缩至 2048 像素、转为 JPEG（质量 88%，每张不超过 8 MiB）；动图会变为静态图，透明效果不保留。恢复草稿时需保留原图片及读取权限。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            photoError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
        if (draft.error != null || draft.uncertain || draft.sending) LayeredInputCard {
            draft.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (draft.uncertain && !draft.sending) {
                Text("上次发布结果未确认，请先核对我的帖子，避免重复发布。", color = MaterialTheme.colorScheme.error)
                BubbleTextButton(onClick = { model.openPanel(PanelKind.Threads) }) { Text("核对我的帖子") }
                BubbleTextButton(onClick = { model.acknowledgePublishResult() }, enabled = model.pending == null) { Text("已确认未发布，允许重试") }
                BubbleTextButton(onClick = { clearConfirmed = true }, enabled = model.pending == null) { Text("已找到帖子，清除此草稿") }
            }
            if (draft.sending) {
                LoadingRow(draft.progress)
                BubbleTextButton(onClick = model::cancelSubmission) { Text("停止上传 / 等待") }
                Text("停止会保留草稿和已完成图片；已发出的提交请求不能撤回，需要先核对结果。", style = MaterialTheme.typography.bodySmall)
            }
        }
        Button(onClick = { confirm = true }, enabled = !draft.sending && !draft.uncertain && model.pending == null && draft.forum.isNotBlank() && draft.title.isNotBlank() && (draft.text.isNotBlank() || draft.images.isNotEmpty()),
            modifier = Modifier.fillMaxWidth().heightIn(min = LocalTouchTarget.current)) { Text("预览并发布") }
        Text("只有确认发布后才会上传所选图片并提交。视频、语音、投票等更多发布类型仍在接入。", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
    }
    if (clearConfirmed) AlertDialog(onDismissRequest = { clearConfirmed = false }, title = { Text("清除已发布的草稿？") },
        text = { Text("仅在已找到对应帖子并核对内容后清除。此操作不会再次提交或删除云端帖子。") },
        confirmButton = { BubbleTextButton(onClick = { clearConfirmed = false; model.acknowledgePublishResult(true) }) { Text("已核对，清除草稿") } },
        dismissButton = { BubbleTextButton(onClick = { clearConfirmed = false }) { Text("继续保留") } })
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("确认发布到 ${draft.forum.removeSuffix("吧")}吧？") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(draft.title, style = MaterialTheme.typography.titleMedium); Text(draft.text)
            if (draft.images.isNotEmpty()) Text("包含 ${draft.images.size} 张图片，将上传到百度贴吧。")
        } },
        confirmButton = { BubbleTextButton(onClick = { confirm = false; model.publishThread() }) { Text("确认发布") } },
        dismissButton = { BubbleTextButton(onClick = { confirm = false }) { Text("继续编辑") } })
}
