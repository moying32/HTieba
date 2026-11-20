package dev.hydrogen.tieba.data

import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class ImageFormat(val extension: String, val mime: String)
internal data class DownloadedImage(val file: File, val format: ImageFormat)

/** Copy original file bytes, including GIF animation. Never export a decoded preview bitmap. */
internal object ImageFileCodec {
    const val MAX_BYTES = 80L * 1024 * 1024
    fun copy(input: InputStream, output: OutputStream, limit: Long = MAX_BYTES): ImageFormat {
        val stream = BufferedInputStream(input)
        stream.mark(32)
        val header = ByteArray(32)
        var size = 0
        while (size < header.size) {
            val count = stream.read(header, size, header.size - size)
            if (count < 0) break
            size += count
        }
        stream.reset()
        fun ascii(start: Int, length: Int) = if (size >= start + length) String(header, start, length, Charsets.US_ASCII) else ""
        val format = when {
            size >= 3 && header[0] == 0xff.toByte() && header[1] == 0xd8.toByte() && header[2] == 0xff.toByte() -> ImageFormat("jpg", "image/jpeg")
            size >= 8 && header.take(8) == byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a).toList() -> ImageFormat("png", "image/png")
            ascii(0, 6) in listOf("GIF87a", "GIF89a") -> ImageFormat("gif", "image/gif")
            ascii(0, 4) == "RIFF" && ascii(8, 4) == "WEBP" -> ImageFormat("webp", "image/webp")
            ascii(4, 4) == "ftyp" && (ascii(8, 4) in listOf("avif", "avis") || ascii(16, 16).contains("avif")) -> ImageFormat("avif", "image/avif")
            else -> throw IOException("地址未返回可保存的图片，请重新打开图片后重试")
        }
        var total = 0L
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            total += count
            if (total > limit) throw IOException("图片超过 80 MB，暂时无法保存")
            output.write(buffer, 0, count)
        }
        if (total <= 0) throw IOException("图片内容为空，请重试")
        return format
    }
}

internal object ImageDownload {
    // Public image addresses only. Do not forward account cookies to a media host.
    private val client = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS).followSslRedirects(false).build()

    suspend fun fetch(source: ImageSource, cacheDir: File): DownloadedImage = suspendCancellableCoroutine { continuation ->
        val url = source.url(ImageQuality.Original)
        if (IdentityParser.imageUrl(url).isBlank()) {
            continuation.resumeWithException(IOException("图片地址不可用")); return@suspendCancellableCoroutine
        }
        val file = File.createTempFile("h2-image-", ".download", cacheDir)
        val call = client.newCall(Request.Builder().url(url).build())
        continuation.invokeOnCancellation { call.cancel(); file.delete() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                file.delete()
                if (continuation.isActive) continuation.resumeWithException(IOException("图片下载失败，请检查网络后重试", e))
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val format = response.use {
                        if (!it.isSuccessful) throw IOException("图片下载失败（HTTP ${it.code}）")
                        val body = it.body ?: throw IOException("图片内容为空")
                        if (body.contentLength() > ImageFileCodec.MAX_BYTES) throw IOException("图片超过 80 MB，暂时无法保存")
                        file.outputStream().use { output -> body.byteStream().use { input -> ImageFileCodec.copy(input, output) } }
                    }
                    if (continuation.isActive) continuation.resume(DownloadedImage(file, format)) else file.delete()
                } catch (e: Exception) {
                    file.delete()
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        })
    }
}
