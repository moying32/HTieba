package dev.hydrogen.tieba.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException

data class DraftImage(val uri: String, val code: String = "")
data class PublishingDraft(val owner: String, val forum: String = "", val title: String = "", val text: String = "",
    val images: List<DraftImage> = emptyList(), val sending: Boolean = false, val progress: String = "",
    val error: String? = null, val uncertain: Boolean = false) {
    fun encode(): String = JSONObject().put("forum", forum).put("title", title).put("text", text).put("uncertain", uncertain)
        .put("images", JSONArray(images.map { JSONObject().put("uri", it.uri).put("code", it.code) })).toString()
    companion object {
        fun decode(owner: String, value: String, forum: String): PublishingDraft = runCatching {
            val o = JSONObject(value)
            PublishingDraft(owner, o.optString("forum"), o.optString("title"), o.optString("text"),
                CommunityParser.objects(o.optJSONArray("images")).map { DraftImage(it.optString("uri"), it.optString("code")) }.take(9), uncertain = o.optBoolean("uncertain"))
        }.getOrDefault(PublishingDraft(owner, forum))
    }
}

internal suspend fun TiebaApi.uploadImage(context: Context, localUri: String): String = withContext(Dispatchers.IO) {
    val uri = Uri.parse(localUri)
    require(uri.scheme == "content") { "请选择系统相册中的图片" }
    val resolver = context.contentResolver
    val bitmap = if (Build.VERSION.SDK_INT >= 28) ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
        val scale = (2048f / maxOf(info.size.width, info.size.height)).coerceAtMost(1f)
        decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    } else {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        options.inJustDecodeBounds = false
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize.coerceAtLeast(1) > 2048) options.inSampleSize = options.inSampleSize.coerceAtLeast(1) * 2
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: throw IOException("无法读取这张图片，请重新选择")
    }
    val width = bitmap.width; val height = bitmap.height
    val bytes = try { ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it); it.toByteArray() } } finally { bitmap.recycle() }
    require(bytes.size <= 8 * 1024 * 1024) { "图片过大，请选择其他图片" }
    val body = FormBody.Builder().add("pic", Base64.encodeToString(bytes, Base64.NO_WRAP)).build()
    val data = JSONObject(execute(Request.Builder().url("${NativeProtocol.WEB_HOST}/mo/q/cooluploadpic?type=ajax")
        .header("Cookie", BaiduSession.cookies()).header("Referer", "https://tieba.baidu.com/")
        .header("User-Agent", "Mozilla/5.0 (Linux; Android 12) HydrogenTieba").post(body).build()))
    if (data.has("no") || data.has("error_code")) NativeProtocol.ensureSuccess(data)
    val error = CommunityParser.text(data, "errorMsg")
    if (error.isNotBlank()) throw IOException(error.take(160))
    val src = CommunityParser.text(data, "imageSrc", "imageBaseSrc")
    val id = Regex("/([0-9a-fA-F]{32,64})(?:\\.[a-zA-Z]+)?(?:[?#].*)?$").find(src)?.groupValues?.get(1)
        ?: throw IOException("百度未返回可用于发帖的图片标识，图片未加入正文，请重试")
    "#(pic,$id,$width,$height)\n"
}

internal suspend fun TiebaApi.publish(forum: String, fid: String, title: String, content: String): String {
    val token = try { tbs() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { throw CommentPreparationException(e) }
    val data = request("/c/c/thread/add", mapOf("fid" to fid, "kw" to forum, "title" to title, "content" to content,
        "anonymous" to "0", "can_no_forum" to "0", "is_feedback" to "0", "new_vcode" to "1", "is_ntitle" to "0",
        "is_pictxt" to "0", "tbs" to token), authenticated = true)
    return NativeProtocol.threadId(data).ifBlank { throw IOException("服务端未确认新帖编号，请先到我的帖子核对，避免重复发布") }
}
