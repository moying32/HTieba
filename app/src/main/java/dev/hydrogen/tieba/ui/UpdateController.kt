package dev.hydrogen.tieba.ui

import androidx.compose.runtime.*
import dev.hydrogen.tieba.data.*
import java.io.File
import kotlinx.coroutines.*

internal class UpdateController(private val scope: CoroutineScope, private val source: UpdateSource,
    private val current: AppVersion, private val readSkip: () -> String?, private val writeSkip: (String) -> Unit) {
    var checking by mutableStateOf(false); private set
    var checked by mutableStateOf(false); private set
    var release by mutableStateOf<AppRelease?>(null); private set
    var remoteVersion by mutableStateOf<AppVersion?>(null); private set
    var dialog by mutableStateOf(false); private set
    var downloading by mutableStateOf(false); private set
    var validating by mutableStateOf(false); private set
    var downloaded by mutableLongStateOf(0L); private set
    var total by mutableLongStateOf(0L); private set
    var ready by mutableStateOf<File?>(null); private set
    var error by mutableStateOf<String?>(null); private set
    var message by mutableStateOf<String?>(null); private set
    private var skippedVersion by mutableStateOf(readSkip())
    val skipped get() = release?.version?.key == skippedVersion
    private var launchChecked = false
    private var manual = false
    private var checkJob: Job? = null
    private var downloadJob: Job? = null
    private var generation = 0

    fun onLaunch() { if (!launchChecked) { launchChecked = true; check(false) } }
    fun check(manualCheck: Boolean = true) {
        if (downloading || validating) return
        if (checking) { manual = manual || manualCheck; return }
        manual = manualCheck; checking = true; error = null; message = null; skippedVersion = readSkip()
        val ticket = ++generation
        checkJob = scope.launch {
            try {
                val feed = source.fetch()
                if (ticket != generation) return@launch
                remoteVersion = feed.newest
                val candidate = feed.release?.takeIf { it.version > current }
                if (candidate?.version != release?.version || candidate?.url != release?.url) ready = null
                release = candidate; checked = true
                if (candidate != null) {
                    if (manual || !skipped) dialog = true
                } else if (feed.newest != null && feed.newest > current) {
                    error = "GitHub 新版尚未提供适用的 APK，请稍后重试"
                } else message = if (feed.newest == null) "GitHub 暂无可识别的发布版本" else "当前版本无需更新"
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { if (ticket == generation) error = friendly(e, "检查更新失败，请检查网络后重试")
            } finally { if (ticket == generation) checking = false }
        }
    }
    fun show() { if (release != null) dialog = true }
    fun dismiss() { dialog = false }
    fun skip() {
        if (downloading || validating) return
        release?.let { writeSkip(it.version.key); skippedVersion = it.version.key }; dialog = false
    }
    fun download() {
        val candidate = release ?: return
        if (downloading || validating || checking) return
        if (ready != null) { dialog = true; return }
        val ticket = ++generation
        downloading = true; downloaded = 0; total = candidate.bytes; error = null; message = null
        downloadJob = scope.launch {
            try {
                val file = source.download(candidate) { count, bytes -> scope.launch {
                    if (ticket == generation && downloading) { downloaded = count; total = bytes }
                } }
                if (ticket == generation) { ready = file; downloaded = candidate.bytes; dialog = true }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { if (ticket == generation) error = friendly(e, "下载失败，请检查网络后重试")
            } finally { if (ticket == generation) downloading = false }
        }
    }
    fun cancelDownload() {
        generation++; downloadJob?.cancel(); downloading = false; downloaded = 0; total = 0
        message = "下载已取消，可以稍后重试"; error = null
    }
    fun install(launch: (File) -> Unit) {
        val file = ready ?: return
        val candidate = release ?: return
        if (validating || downloading) return
        validating = true; error = null
        downloadJob = scope.launch {
            var verified = false
            try {
                source.verify(file, candidate)
                verified = true
                launch(file)
                message = "已打开系统安装界面；取消后可再次安装"
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { error = friendly(e, "无法打开系统安装界面，请重试")
                if (!verified) { ready = null; file.delete() }
            } finally { validating = false }
        }
    }
    fun installPermissionDenied() { message = "尚未允许安装更新，可再次点击安装并授权" }
    fun installSettingsUnavailable() { error = "无法打开安装授权设置，请在系统设置中允许氢贴吧安装应用后重试" }
    fun close() { generation++; checkJob?.cancel(); downloadJob?.cancel() }
    private fun friendly(error: Exception, fallback: String) = when (error) {
        is java.net.UnknownHostException, is java.net.SocketTimeoutException -> "网络不可用或连接超时，请稍后重试"
        is java.io.IOException -> error.message ?: fallback
        else -> fallback
    }
}
