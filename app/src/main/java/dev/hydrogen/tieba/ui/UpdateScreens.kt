package dev.hydrogen.tieba.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.hydrogen.tieba.BuildConfig
import dev.hydrogen.tieba.data.ReleaseUpdates
import java.io.File
import java.util.Locale

@Composable
internal fun UpdateSettings(updates: UpdateController) {
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("当前版本 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
        Text("每次启动时自动检查 GitHub 发布版本，包含开发预发布。发现新版后由你选择下载和安装。",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (updates.checking) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("正在检查更新…")
        }
        updates.remoteVersion?.let { Text("GitHub 发布版本 ${it.key}", style = MaterialTheme.typography.bodyMedium) }
        updates.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        updates.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        updates.release?.let { release ->
            Text("可更新至 ${release.version.key}${if (release.preview) " · 预发布" else ""}", style = MaterialTheme.typography.titleMedium)
            if (updates.skipped) Text("已跳过此版本；手动检查仍可下载更新。", style = MaterialTheme.typography.bodySmall)
            if (updates.downloading) UpdateProgress(updates)
            BubbleOutlinedButton(onClick = updates::show, modifier = Modifier.heightIn(min = LocalTouchTarget.current)) {
                Text(if (updates.ready != null) "查看并安装更新" else if (updates.downloading) "查看下载进度" else "查看更新")
            }
        }
        Button(onClick = { updates.check() }, enabled = !updates.checking && !updates.downloading && !updates.validating,
            modifier = Modifier.fillMaxWidth().heightIn(min = LocalTouchTarget.current)) {
            Text(if (updates.checking) "正在检查" else "检查更新")
        }
        Text("更新来源：${ReleaseUpdates.REPOSITORY}\n安装前会核对包名、版本和签名；应用数据会保留。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun UpdatePrompt(updates: UpdateController) {
    val context = LocalContext.current
    val installer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    fun launchInstaller(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        installer.launch(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (context.packageManager.canRequestPackageInstalls()) updates.install(::launchInstaller)
        else updates.installPermissionDenied()
    }
    if (!updates.dialog) return
    val release = updates.release ?: return
    AlertDialog(onDismissRequest = updates::dismiss,
        title = { Text(if (updates.ready != null) "更新已准备好" else "发现新版本 ${release.version.key}") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${BuildConfig.VERSION_NAME} → ${release.version.key}${if (release.preview) " · 预发布" else ""} · ${updateBytes(release.bytes)}")
                if (updates.ready != null) Text("更新包已校验。点击安装后，请在系统界面确认更新。")
                if (updates.downloading) UpdateProgress(updates)
                if (updates.validating) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("正在核对安装包…") }
                if (release.notes.isNotBlank()) SelectionContainer { Text(release.notes, style = MaterialTheme.typography.bodyMedium) }
                else Text("此版本未附更新说明。")
                updates.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                updates.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (!updates.downloading && !updates.validating) BubbleTextButton(onClick = updates::skip,
                    modifier = Modifier.heightIn(min = LocalTouchTarget.current)) { Text("跳过此版本") }
            }
        },
        confirmButton = {
            BubbleTextButton(enabled = !updates.downloading && !updates.validating, onClick = {
                if (updates.ready != null) {
                    if (context.packageManager.canRequestPackageInstalls()) updates.install(::launchInstaller)
                    else try {
                        permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                    } catch (_: android.content.ActivityNotFoundException) { updates.installSettingsUnavailable()
                    } catch (_: SecurityException) { updates.installSettingsUnavailable() }
                } else updates.download()
            }) { Text(if (updates.ready != null) "安装更新" else if (updates.downloading) "正在下载" else "下载更新") }
        },
        dismissButton = {
            BubbleTextButton(onClick = { if (updates.downloading) updates.cancelDownload() else updates.dismiss() },
                enabled = !updates.validating) { Text(if (updates.downloading) "取消下载" else "稍后提醒") }
        })
}

@Composable
private fun UpdateProgress(updates: UpdateController) {
    val progress = if (updates.total > 0) (updates.downloaded.toFloat() / updates.total).coerceIn(0f, 1f) else 0f
    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    Text("正在下载 ${(progress * 100).toInt()}% · ${updateBytes(updates.downloaded)} / ${updateBytes(updates.total)}",
        style = MaterialTheme.typography.bodySmall)
}
private fun updateBytes(bytes: Long) = String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024))
