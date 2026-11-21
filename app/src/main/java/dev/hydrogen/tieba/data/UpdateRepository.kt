package dev.hydrogen.tieba.data

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal interface UpdateSource {
    suspend fun fetch(): UpdateFeed
    suspend fun download(release: AppRelease, progress: (Long, Long) -> Unit): File
    suspend fun verify(file: File, release: AppRelease)
}

internal class UpdateRepository(private val context: Context) : UpdateSource {
    private val preferences = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    var skippedVersion: String?
        get() = preferences.getString("skipped_version", null)
        set(value) { preferences.edit().putString("skipped_version", value).apply() }
    // No Tieba interceptors, credentials, cookies or installation identifier.
    private val client = OkHttpClient.Builder().cookieJar(CookieJar.NO_COOKIES)
        .connectTimeout(12, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
        .addNetworkInterceptor { chain ->
            val url = chain.request().url
            if (!url.isHttps || url.host !in setOf("api.github.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com"))
                throw IOException("更新地址不受支持")
            chain.proceed(chain.request())
        }.build()
    private fun request(url: String) = Request.Builder().url(url).header("User-Agent", "HTieba-Android-Updater")
        .header("Accept", "application/vnd.github+json").header("Accept-Language", "en-US").build()
    override suspend fun fetch(): UpdateFeed = withContext(Dispatchers.IO) {
        UpdateFeedLoader(
            get = { url, accept ->
                client.newCall(request(url).newBuilder().header("Accept", accept).build()).await().use { response ->
                    if (!response.isSuccessful) throw IOException("GitHub 发布页访问失败（HTTP ${response.code}）")
                    response.readFeed()
                }
            },
            size = { url ->
                client.newCall(request(url).newBuilder().head().header("Accept", "application/octet-stream").build()).await().use { response ->
                    if (!response.isSuccessful) throw IOException("GitHub 附件访问失败（HTTP ${response.code}）")
                    response.header("Content-Length")?.toLongOrNull() ?: throw IOException("无法确认更新包大小")
                }
            },
            api = { fetchApi() },
        ).fetch()
    }
    private suspend fun fetchApi(): UpdateFeed {
        val cache = preferences.getString("release_cache", null)
        val etag = preferences.getString("release_etag", null)
        val request = request(ReleaseUpdates.API).newBuilder().apply {
            if (cache != null && etag != null) header("If-None-Match", etag)
        }.build()
        return client.newCall(request).await().use { response ->
            if (response.code == 304 && cache != null) return@use ReleaseUpdates.parse(cache)
            when (response.code) {
                403, 429 -> throw IOException("GitHub 请求受限，请稍后重试")
                404 -> throw IOException("暂时无法访问 GitHub 发布记录，请稍后重试")
            }
            if (!response.isSuccessful) throw IOException("检查更新失败（HTTP ${response.code}），请稍后重试")
            val json = response.readFeed()
            val feed = ReleaseUpdates.parse(json)
            preferences.edit().putString("release_cache", json).putString("release_etag", response.header("ETag")).apply()
            feed
        }
    }
    private fun Response.readFeed(): String {
            val body = body ?: throw IOException("GitHub 未返回发布记录")
            val bytes = body.byteStream().use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (output.size() <= 4 * 1024 * 1024) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            if (bytes.size > 4 * 1024 * 1024) throw IOException("发布记录过大，请稍后重试")
            return bytes.toString(Charsets.UTF_8)
    }
    override suspend fun download(release: AppRelease, progress: (Long, Long) -> Unit): File = withContext(Dispatchers.IO) {
        if (!ReleaseUpdates.validAssetUrl(release.url)) throw IOException("更新地址不受支持")
        val directory = File(context.filesDir, "updates").apply { mkdirs() }
        val target = File(directory, "update.apk")
        if (target.isFile) {
            try { verify(target, release); return@withContext target }
            catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (_: IOException) { /* Discard an obsolete or incomplete cached APK. */ }
        }
        target.delete()
        // A cancelled response may finish after a retry has begun. Each request owns its file.
        val part = File.createTempFile("update-", ".part", directory)
        try {
            // Streaming on OkHttp's worker thread lets cancellation close a blocked socket immediately.
            val call = client.newCall(request(release.url).newBuilder().header("Accept", "application/octet-stream").build())
            suspendCancellableCoroutine<Unit> { continuation ->
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWith(Result.failure(e)) }
                    override fun onResponse(call: Call, response: Response) {
                        try {
                            response.use {
                                if (!continuation.isActive) return
                                if (!it.isSuccessful) throw IOException("下载失败（HTTP ${it.code}），请重试")
                                val body = it.body ?: throw IOException("下载内容为空")
                                if (body.contentLength() >= 0 && body.contentLength() != release.bytes) throw IOException("更新包大小与发布记录不符，请重试")
                                part.outputStream().use { output -> body.byteStream().use { input ->
                                    ReleaseUpdates.copyApk(input, output, release, { continuation.isActive }, progress)
                                } }
                            }
                            if (continuation.isActive) continuation.resumeWith(Result.success(Unit))
                        } catch (error: Exception) { if (continuation.isActive) continuation.resumeWith(Result.failure(error))
                        } finally { if (call.isCanceled()) part.delete() }
                    }
                })
            }
            verify(part, release)
            if (!part.renameTo(target)) throw IOException("无法保存更新包，请重试")
            target
        } finally { part.delete() }
    }
    override suspend fun verify(file: File, release: AppRelease) = withContext(Dispatchers.IO) {
        if (!file.isFile || file.length() != release.bytes) throw IOException("更新包缺失或不完整，请重新下载")
        file.inputStream().use { input -> ReleaseUpdates.copyApk(input, OutputSink, release) }
        val manager = context.packageManager
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        @Suppress("DEPRECATION")
        val installed = manager.getPackageInfo(context.packageName, flags)
        @Suppress("DEPRECATION")
        val archive = manager.getPackageArchiveInfo(file.absolutePath, flags) ?: throw IOException("更新包无法解析，请重新下载")
        ApkCompatibility.requireUpgrade(identity(installed), identity(archive), release, Build.VERSION.SDK_INT)
    }
    @Suppress("DEPRECATION")
    private fun identity(info: PackageInfo): ApkIdentity {
        val signers = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        val hashes = signers.orEmpty().map { signer ->
            MessageDigest.getInstance("SHA-256").digest(signer.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
        return ApkIdentity(info.packageName, if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong(),
            AppVersion.parse(info.versionName.orEmpty()), hashes, info.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE)
    }
    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWith(Result.failure(e)) }
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response, onCancellation = { _, value, _ -> value.close() })
            }
        })
    }
    private object OutputSink : java.io.OutputStream() {
        override fun write(b: Int) = Unit
        override fun write(b: ByteArray, off: Int, len: Int) = Unit
    }
}
