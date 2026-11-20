package dev.hydrogen.tieba.data

import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

class LoginExpiredException : IOException("登录已失效，请重新登录百度账号")

class TiebaApi(private val installationId: String = java.util.UUID.randomUUID().toString()) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    // The service chooses content and ordering. Never synthesize a local
    // recommendation stream from forum reads when this response is empty.
    suspend fun personalized(page: Int): StreamPage {
        val data = request("/c/f/excellent/personalized", mapOf(
            "_client_version" to "8.0.8.0", "pn" to "$page", "rn" to "20", "q_type" to "1",
            "load_type" to if (page == 1) "1" else "2", "page_thread_count" to "20", "need_tags" to "0",
        ))
        return withContext(Dispatchers.Default) {
            val people = users(data)
            val threads = data.optJSONArray("thread_list").objects()
                .filter { it.optInt("is_tiebaplus_ad") == 0 && it.optInt("is_ad") == 0 }
                .map { thread(it, it.optString("fname").ifBlank { it.optJSONObject("forum")?.optString("name").orEmpty() }, people) }
                .filter { it.id.isNotBlank() }.distinctBy { it.id }
            StreamPage(threads, page, hasMore(data, page, threads.isNotEmpty()))
        }
    }

    suspend fun discovery(): DiscoveryPage {
        val data = request("/c/f/forum/forumrecommend", mapOf("like_forum" to "1", "recommend" to "1", "topic" to "0"))
        fun parse(array: JSONArray?) = array.objects().map {
            ForumItem(it.optString("forum_id").ifBlank { it.optString("id") },
                it.optString("forum_name").ifBlank { it.optString("name") }, secure(it.optString("avatar")),
                it.optString("slogan").ifBlank { it.optString("intro") })
        }.filter { it.name.isNotBlank() }.distinctBy { it.id.ifBlank { it.name } }
        return DiscoveryPage(parse(data.optJSONArray("like_forum")),
            parse(data.optJSONArray("recommend_forum") ?: data.optJSONArray("recommend")))
    }

    suspend fun forum(name: String, page: Int, sort: ForumSort = ForumSort.Reply): ForumPage {
        val data = request("/c/f/frs/page", mapOf("kw" to name, "pn" to "$page", "rn" to "30", "q_type" to "2", "sort_type" to sort.code, "is_good" to if (sort.good) "1" else "0"))
        return withContext(Dispatchers.Default) {
            val forum = data.optJSONObject("forum")
            // Later pages return placeholder forum metadata, including is_exists=0,
            // even when thread_list is valid. Only the first page establishes it.
            if (page == 1 && (forum == null || forum.optInt("is_exists", 1) == 0)) {
                throw IOException("没有找到这个吧，请检查吧名")
            }
            val forumName = forum?.optString("name").orEmpty().ifBlank { name }
            val people = users(data)
            val threads = data.optJSONArray("thread_list").objects()
                .filter { it.optInt("is_tiebaplus_ad") == 0 && it.optInt("is_ad") == 0 }
                .map { thread(it, forumName, people) }.filter { it.id.isNotBlank() }.distinctBy { it.id }
            ForumPage(forumName, forum?.optLong("member_num") ?: 0, threads, page, hasMore(data, page, threads.isNotEmpty()),
                forum?.optString("id").orEmpty().ifBlank { forum?.optString("fid").orEmpty() },
                forum?.optInt("is_like") == 1 || forum?.optInt("is_follow") == 1, CommunityParser.forum(data, name))
        }
    }

    suspend fun account(): AccountState {
        if (!BaiduSession.hasLoginCookie()) return AccountState()
        val data = request("/c/s/login", mapOf("bdusstoken" to BaiduSession.cookie("BDUSS")), authenticated = true)
        val user = data.optJSONObject("user")
        val uid = user?.optString("id").orEmpty().ifBlank { user?.optString("uid").orEmpty() }
        val name = user?.optString("name_show").orEmpty().ifBlank { user?.optString("name").orEmpty() }
        if (uid.isBlank() || uid == "0") throw IOException("账号状态暂时无法确认，请重新打开官方登录页")
        val identity = IdentityParser.person(user)
        val base = AccountState(loggedIn = true, uid = uid, name = name.ifBlank { "百度用户" }, avatar = identity.avatar, identity = identity)
        return try {
            val profile = profile(uid)
            base.copy(name = profile.person.name, avatar = profile.person.avatar.ifBlank { identity.avatar }, identity = profile.person, profile = profile)
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
          catch (error: Exception) { base.copy(profileError = "资料未完整同步，可点刷新重试") }
    }

    suspend fun likePost(threadId: String, postId: String, undo: Boolean, isComment: Boolean = false, forumId: String = "") {
        val tbs = tbs()
        val fields = NativeProtocol.agreeFields(threadId, postId, undo, isComment, forumId).toMutableMap()
        fields["BDUSS"] = BaiduSession.cookie("BDUSS")
        fields["tbs"] = tbs
        fields["cuid"] = ClientIdentity.cuid(installationId)
        BaiduSession.cookie("STOKEN").takeIf { it.isNotBlank() }?.let { fields["stoken"] = it }
        request("/c/c/agree/opAgree", fields, authenticated = true)
    }

    suspend fun followForum(forumId: String, forumName: String, undo: Boolean) {
        require(forumId.isNotBlank()) { "这个吧暂时没有可用的吧 ID" }
        request("/c/c/forum/${if (undo) "unfavolike" else "like"}", mapOf(
            "BDUSS" to BaiduSession.cookie("BDUSS"), "fid" to forumId, "kw" to forumName, "tbs" to tbs(),
        ), authenticated = true)
    }

    suspend fun comment(forumId: String, forumName: String, threadId: String, parent: PostItem?, text: String, target: PostItem? = null) {
        require(text.trim().isNotEmpty()) { "评论内容不能为空" }
        require(forumId.isNotBlank()) { "没有获取到吧信息，请刷新后重试" }
        val fields = mutableMapOf(
            "fid" to forumId, "kw" to forumName, "tid" to threadId, "content" to text.trim(),
            "tbs" to try { tbs() } catch (error: kotlinx.coroutines.CancellationException) { throw error }
                catch (error: Exception) { throw CommentPreparationException(error) },
            "cuid" to "baidutiebaapp$installationId",
            "anonymous" to "0", "new_vcode" to "1", "is_ad" to "0", "is_giftpost" to "0",
        )
        fields.putAll(NativeProtocol.replyFields(parent, target))
        val data = request("/c/c/post/add", fields, authenticated = true)
        if (NativeProtocol.commentId(data).isBlank()) throw IOException("服务端未确认评论结果，请先查看原帖，避免重复发送。草稿已保留")
    }

    internal suspend fun tbs(expectedUid: String? = null): String {
        if (!BaiduSession.hasLoginCookie()) throw IOException("请先登录百度账号")
        val data = request("/c/s/login", mapOf("bdusstoken" to BaiduSession.cookie("BDUSS")), authenticated = true)
        val uid = data.optJSONObject("user")?.optString("id").orEmpty()
        if (uid.isBlank() || uid == "0" || (expectedUid != null && uid != expectedUid)) throw LoginExpiredException()
        return NativeProtocol.tbs(data)
            .ifBlank { throw IOException("账号安全校验未完成，请重新登录") }
    }

    suspend fun detail(id: String, page: Int, onlyAuthor: Boolean = false, postId: String = ""): ThreadPage {
        val fields = mutableMapOf("kz" to id, "pn" to "$page", "rn" to "20", "with_floor" to "1", "lz" to if (onlyAuthor) "1" else "0")
        if (postId.isNotBlank()) { fields["pid"] = postId; fields.remove("pn") }
        val data = request("/c/f/pb/page", fields)
        return withContext(Dispatchers.Default) {
            val rawThread = data.optJSONObject("thread") ?: throw IOException("帖子暂时无法查看，可能已被删除或需要登录")
            val people = users(data)
            val posts = data.optJSONArray("post_list").objects().map { post ->
                PostParser.post(post, people, ::content)
            }.filter { it.id.isNotBlank() }.distinctBy { it.id }
            val forumName = data.optJSONObject("forum")?.optString("name").orEmpty()
            val summary = thread(rawThread, forumName, people).copy(
                id = rawThread.optString("id").ifBlank { id },
                author = posts.firstOrNull { it.floor == 1 }?.author ?: people[rawThread.optString("author_id")] ?: person(rawThread.optJSONObject("author")),
                forumId = data.optJSONObject("forum")?.optString("id").orEmpty(),
                replies = CommunityParser.commentsCount(rawThread, includesRoot = true),
                views = CommunityParser.views(rawThread) ?: CommunityParser.number(data, "thread_freq_num"),
            )
            val actualPage = data.optJSONObject("page")?.optInt("current_page", page)?.coerceAtLeast(1) ?: page
            ThreadPage(summary, posts.map { if (it.floor == 1) it.copy(liked = summary.liked, likes = summary.likes) else it }, actualPage, hasMore(data, actualPage, posts.isNotEmpty()))
        }
    }

    internal suspend fun request(path: String, extra: Map<String, String>, authenticated: Boolean = false, commonFields: Boolean = true): JSONObject {
        val parameters = sortedMapOf<String, String>()
        if (commonFields) parameters.putAll(mapOf("_client_type" to "2", "_client_version" to "12.35.1.0"))
        parameters.putAll(extra)
        if (BaiduSession.hasLoginCookie()) parameters["BDUSS"] = BaiduSession.cookie("BDUSS")
        val sign = NativeProtocol.sign(parameters)
        val body = FormBody.Builder().apply {
            parameters.forEach { (key, value) -> add(key, value) }
            add("sign", sign)
        }.build()
        val host = if (authenticated) NativeProtocol.APP_HOST else NativeProtocol.WEB_HOST
        val builder = Request.Builder().url("$host$path")
            .header("User-Agent", "bdtb for Android ${parameters["_client_version"]}")
            .header("Accept", "application/json")
        if (!authenticated && BaiduSession.hasLoginCookie()) builder.header("Cookie", BaiduSession.cookies())
        val data = parseJson(execute(builder.post(body).build()))
        if (path == "/c/s/login" && data.optString("error_code", "0") != "0") throw LoginExpiredException()
        ensureSuccess(data)
        return data
    }

    private fun ensureSuccess(data: JSONObject) = NativeProtocol.ensureSuccess(data)

    internal suspend fun execute(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) { if (continuation.isActive) continuation.resumeWithException(error) }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        if (!response.isSuccessful) throw IOException("贴吧暂时无法连接，请稍后重试（${response.code}）")
                        val result = response.body?.string() ?: throw IOException("贴吧返回了空内容")
                        if (continuation.isActive) continuation.resume(result)
                    } catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
                }
            }
        })
    }

    private fun parseJson(text: String): JSONObject = try { JSONObject(text) } catch (_: Exception) {
        throw IOException("贴吧返回了验证页面，请稍后重试或在原帖中查看")
    }

    suspend fun floor(threadId: String, postId: String, page: Int): FloorPage {
        val data = request("/c/f/pb/floor", mapOf("kz" to threadId, "pid" to postId, "pn" to "$page", "rn" to "30"))
        return withContext(Dispatchers.Default) {
            val people = users(data)
            val posts = data.optJSONArray("subpost_list").objects().map { post ->
                PostParser.post(post, people, ::content, nested = true)
            }.filter { it.id.isNotBlank() }.distinctBy { it.id }
            FloorPage(posts, page, hasMore(data, page, false))
        }
    }

    private fun users(data: JSONObject) = data.optJSONArray("user_list").objects().associate { it.optString("id") to person(it) }

    private fun person(data: JSONObject?) = IdentityParser.person(data)

    internal fun thread(data: JSONObject, forum: String, people: Map<String, Person> = emptyMap()): ThreadItem {
        val abstract = content(data.optJSONArray("abstract"))
        val sources = data.optJSONArray("media").objects().filter { it.optInt("type") == 3 }
            .map { MediaParser.image(it,true) }.filter { it.preview.isNotBlank() }
            .ifEmpty { abstract.mapNotNull { it.imageSource } }
        val media = sources.map { it.preview }
        return ThreadItem(
            id = CommunityParser.text(data, "id", "tid", "thread_id"),
            title = data.optString("title").ifBlank { "无标题帖子" }, forum = forum,
            author = people[data.optString("author_id")] ?: person(data.optJSONObject("author")),
            excerpt = abstract.joinToString("") { it.text }.trim(), images = media.take(3),
            replies = data.optInt("reply_num"), likes = data.optJSONObject("agree")?.optInt("agree_num") ?: data.optInt("agree_num"),
            timestamp = data.optLong("create_time"), pinned = data.optInt("is_top") == 1,
            liked = data.optJSONObject("agree")?.optInt("has_agree") == 1,
            forumId = data.optString("fid").ifBlank { data.optString("forum_id") },
            views = CommunityParser.views(data), mediaCount = maxOf(media.size, data.optInt("media_num")),
            video = MediaParser.video(data.optJSONObject("video_info")),
            videoCover = IdentityParser.imageUrl(CommunityParser.text(data.optJSONObject("video_info"), "thumbnail_url", "video_cover")),
            poll = data.optJSONObject("poll_info")?.let { poll -> PollInfo(CommunityParser.text(poll, "title"),
                CommunityParser.objects(poll.optJSONArray("options")).map { CommunityParser.text(it, "text", "title", "option") }, CommunityParser.number(poll, "total_num")) },
            likesKnown = CommunityParser.number(data.optJSONObject("agree"), "agree_num") != null || CommunityParser.number(data, "agree_num") != null,
            repliesKnown = CommunityParser.number(data, "reply_num") != null,
            imageSources = sources,
            collections = CommunityParser.number(data, "collect_num"),
        )
    }

    internal fun content(array: JSONArray?): List<ContentPart> = array.objects().mapNotNull { segment ->
        when (segment.optInt("type")) {
            2 -> EmojiParser.parse(segment)
            3 -> {
                val source = MediaParser.image(segment,false)
                val image = source.url(ImageQuality.High)
                val size = segment.optString("bsize").split(',')
                val width = size.getOrNull(0)?.toFloatOrNull() ?: 1f
                val height = size.getOrNull(1)?.toFloatOrNull() ?: 1f
                if (image.isBlank()) null else ContentPart(image = image, ratio = (width / height.coerceAtLeast(1f)).coerceIn(.45f, 2.5f), imageSource = source)
            }
            1 -> ContentPart(text = segment.optString("text"), link = secure(segment.optString("link").ifBlank { segment.optString("text") }))
            4 -> ContentPart(text = segment.optString("text"), mention = CommunityParser.text(segment, "uid").takeIf { it.toLongOrNull()?.let { id -> id > 0 } == true }
                ?.let { Person(id = it, name = segment.optString("text").ifBlank { "吧友" }, avatar = IdentityParser.portraitUrl(segment.optString("portrait"))) })
            5 -> ContentPart(video = MediaParser.video(segment), videoCover = IdentityParser.imageUrl(CommunityParser.text(segment,"thumbnail_url","cover","src")), isVideo = true)
            else -> segment.optString("text").takeIf { it.isNotEmpty() }?.let { ContentPart(text = it) }
        }
    }.fold(mutableListOf<ContentPart>()) { result, part ->
        val previous = result.lastOrNull()
        if (previous != null && previous.emoji.isBlank() && part.emoji.isBlank() && previous.mention == null && part.mention == null && !previous.isVideo && !part.isVideo && previous.image.isEmpty() && previous.link.isEmpty() && previous.video.isEmpty() && part.image.isEmpty() && part.link.isEmpty() && part.video.isEmpty()) {
            result[result.lastIndex] = previous.copy(text = previous.text + part.text)
        } else result.add(part)
        result
    }

    private fun hasMore(data: JSONObject, page: Int, fallback: Boolean): Boolean {
        val pagination = data.optJSONObject("page") ?: return fallback
        return if (pagination.has("has_more")) pagination.optInt("has_more") == 1 else page < pagination.optInt("total_page", page)
    }

    private fun secure(url: String) = url.replaceFirst("http://", "https://")
    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}
