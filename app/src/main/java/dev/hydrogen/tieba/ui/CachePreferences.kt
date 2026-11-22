package dev.hydrogen.tieba.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Use Coil's own cache APIs, without traversing app files or WebView data. */
@Composable
internal fun CachePreferences() {
    val context = LocalContext.current
    val loader = context.imageLoader
    val scope = rememberCoroutineScope()
    var bytes by remember { mutableStateOf<Long?>(null) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(loader) { bytes = withContext(Dispatchers.IO) { loader.diskCache?.size ?: 0L } }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("图片缓存", style = MaterialTheme.typography.titleSmall)
        Text(bytes?.let { "磁盘缓存 ${String.format(Locale.CHINA, "%.1f", it / 1048576.0)} MB" } ?: "正在统计图片缓存",
            style = MaterialTheme.typography.bodyMedium)
        Text("清理图片内存和磁盘缓存，之后查看时会重新下载。保留登录、草稿、本地收藏、聊天记录及已保存到下载目录的图片。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BubbleOutlinedButton(enabled = !busy && bytes != null, onClick = {
            busy = true; result = null
            scope.launch {
                try {
                    (context.applicationContext as dev.hydrogen.tieba.HydrogenApplication).clearImageMemory()
                    withContext(Dispatchers.IO) { loader.diskCache?.clear() }
                    bytes = withContext(Dispatchers.IO) { loader.diskCache?.size ?: 0L }
                    result = "图片缓存已清理"
                } catch (e: kotlinx.coroutines.CancellationException) { throw e
                } catch (_: Exception) { result = "缓存清理未完成，请重试"
                } finally { busy = false }
            }
        }) { Text(if (busy) "正在清理" else "清理图片缓存") }
        result?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
