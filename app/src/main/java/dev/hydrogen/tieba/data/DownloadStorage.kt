package dev.hydrogen.tieba.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Publish complete original bytes in the public Download/HTieBa directory. */
internal object DownloadStorage {
    const val FOLDER = "HTieBa"

    suspend fun save(context: Context, image: DownloadedImage): String = withContext(Dispatchers.IO) {
        val name = "HTieBa_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.${image.format.extension}"
        val coroutine = currentCoroutineContext()
        coroutine.ensureActive()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, image.format.mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("无法创建下载文件，请检查存储空间")
            var published = false
            try {
                resolver.openOutputStream(uri, "w")?.use { output ->
                    image.file.inputStream().use { input ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            coroutine.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                    }
                } ?: throw IOException("无法写入下载文件")
                coroutine.ensureActive()
                if (resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) != 1) {
                    throw IOException("无法完成下载，请重试")
                }
                published = true
            } finally {
                if (!published) runCatching { resolver.delete(uri, null, null) }
            }
        } else {
            @Suppress("DEPRECATION")
            val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER)
            val target = OriginalFileStorage.copy(image.file, directory, name) { coroutine.ensureActive() }
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(image.format.mime), null)
        }
        "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER/$name"
    }
}

/** Legacy public storage: reserve a fresh name, never overwrite, and remove partial copies. */
internal object OriginalFileStorage {
    fun copy(source: File, directory: File, name: String, checkActive: () -> Unit = {}): File {
        require(name.isNotBlank() && name == File(name).name && name !in listOf(".", ".."))
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("无法创建 Download/HTieBa 文件夹")
        val target = File(directory, name)
        if (!target.createNewFile()) throw IOException("下载文件已存在，请重试")
        var completed = false
        try {
            source.inputStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        checkActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
            }
            checkActive()
            completed = true
            return target
        } finally {
            if (!completed) target.delete()
        }
    }
}
