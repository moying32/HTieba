package dev.hydrogen.tieba.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Local appearance only. No upload or change to Baidu account data. */
internal object ProfileCoverStore {
    suspend fun import(context: Context, uri: Uri, onReady: (File) -> Unit): File = withContext(Dispatchers.IO) {
        val folder = File(context.filesDir, "profile-covers")
        if (!folder.isDirectory && !folder.mkdirs()) throw IOException("无法保存主页背景")
        val file = File(folder, "${UUID.randomUUID()}.image")
        var completed = false
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > 20 * 1024 * 1024) throw IOException("请选择不超过 20 MB 的图片")
                        output.write(buffer, 0, count)
                    }
                }
            } ?: throw IOException("无法读取所选图片")
            val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, size)
            if (size.outWidth <= 0 || size.outHeight <= 0) throw IOException("所选文件不是可读取的图片")
            currentCoroutineContext().ensureActive()
            onReady(file)
            completed = true
            file
        } finally { if (!completed) file.delete() }
    }
}
