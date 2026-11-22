package dev.hydrogen.tieba.data

import java.io.IOException
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import dev.hydrogen.tieba.data.CommunityParser.objects
import dev.hydrogen.tieba.data.CommunityParser.text
import dev.hydrogen.tieba.data.CommunityParser.number

internal suspend fun TiebaApi.webGet(path: String, fields: Map<String, String>): JSONObject {
    val url = (NativeProtocol.WEB_HOST + path).toHttpUrl().newBuilder().apply { fields.forEach { (k,v) -> addQueryParameter(k,v) } }.build()
    val builder = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Linux; Android 12) HydrogenTieba")
        .header("Referer", "https://tieba.baidu.com/").header("Accept", "application/json")
    if (BaiduSession.hasLoginCookie()) builder.header("Cookie", BaiduSession.cookies())
    val data = try { JSONObject(execute(builder.build())) } catch (error: org.json.JSONException) { throw IOException("贴吧返回验证页面，请在官方页面完成验证后重试", error) }
    NativeProtocol.ensureSuccess(data)
    return data
}

internal suspend fun TiebaApi.following(page: Int, cursor: String): StreamPage {
    val data = request("/c/f/concern/userlike", mapOf("pn" to "$page", "rn" to "20", "page_tag" to cursor,
        "follow_type" to "1", "load_type" to if (page == 1) "1" else "2", "request_times" to "$page"), authenticated = true)
    val body = data.optJSONObject("data") ?: data
    val list = body.optJSONArray("thread_info") ?: body.optJSONArray("thread_list") ?: body.optJSONArray("feed_list")
        ?: throw IOException("百度贴吧未返回关注动态列表，请稍后重试")
    val users = objects(body.optJSONArray("user_list")).associate { text(it, "id") to IdentityParser.person(it) }
    val items = objects(list).map { it.optJSONObject("thread_list") ?: it.optJSONObject("thread") ?: it }
        .filter { it.optInt("is_ad") == 0 && it.optInt("is_tiebaplus_ad") == 0 }
        .map { thread(it, text(it,"fname","forum_name").ifBlank { text(it.optJSONObject("forum"),"name") }, users) }
        .filter { it.id.isNotBlank() }.distinctBy { it.id }
    if (list.length() > 0 && items.isEmpty()) throw IOException("关注动态返回了暂未支持的内容格式，未使用其他帖子替代")
    val next = text(body,"page_tag")
    return StreamPage(items,page,CommunityParser.more(body,page,next.isNotBlank() && next != cursor),next)
}

internal suspend fun TiebaApi.profile(uid: String): UserProfile {
    require(uid.toLongOrNull()?.let { it > 0 } == true) { "请输入有效的贴吧 UID" }
    val data = try { request("/c/u/user/profile", mapOf("uid" to uid, "need_post_count" to "1"), authenticated = true) }
    catch (error: TiebaRejectedException) {
        // Legacy form responses can report 220012 for otherwise accessible
        // identities. A second typed read must independently confirm the UID.
        if (error.code != "220012") throw error
        return binaryProfile(uid)
    }
    val user = data.optJSONObject("user") ?: throw IOException("贴吧没有返回用户资料")
    return CommunityParser.profile(user).also { if (it.person.id != uid) throw IOException("百度返回的用户资料与目标 UID 不一致") }
}

internal suspend fun TiebaApi.forumIdentity(person: Person, fid: String): Person {
    val portrait = person.avatar.substringAfterLast('/').substringBefore('?')
    if (portrait.isBlank() || fid.isBlank()) return person
    val data = request("/c/f/forum/getUserForumLevelInfo", mapOf("forum_id" to fid, "friend_portrait" to portrait, "_client_version" to "22.13.1.1"), authenticated = true)
        .optJSONObject("data") ?: return person
    val user = data.optJSONObject("user_info")
    if (text(user,"id") != person.id) return person
    val level = data.optJSONObject("user_forum_info") ?: return person
    return person.copy(level = number(level,"level_id")?.toInt()?.coerceIn(0,99) ?: person.level,
        levelName = text(level,"level_name").ifBlank { person.levelName },
        role = text(level,"role_name").takeIf { it in listOf("吧主","小吧主","吧务") } ?: person.role)
}

internal suspend fun TiebaApi.userContents(uid: String, threads: Boolean, page: Int): ContentPage {
    val data = request("/c/u/feed/userpost", mapOf("uid" to uid, "pn" to "$page", "rn" to "20", "is_thread" to if (threads) "1" else "0", "need_content" to "1"), authenticated = true)
    if (!data.has("post_list") && !data.has("hide_post")) throw IOException("贴吧未返回帖子列表，请重试")
    if (data.optInt("hide_post") == 1) throw IOException("此用户设置了隐藏发帖记录")
    val items = objects(data.optJSONArray("post_list")).flatMap { CommunityParser.contentEntries(it, threads) }.mapNotNull { item ->
        val tid = text(item, "thread_id", "tid")
        if (tid.isBlank()) return@mapNotNull null
        val author = JSONObject().put("id", text(item,"user_id")).put("name_show", text(item,"name_show", "user_name"))
            .put("portrait", text(item,"user_portrait"))
        val parts = content(item.optJSONArray("content") ?: item.optJSONArray("abstract"))
        val result = thread(item, text(item,"forum_name")).copy(id=tid, author=IdentityParser.person(author),
            excerpt=parts.joinToString("") { it.text }, images=parts.map { it.image }.filter(String::isNotBlank).take(3),
            imageSources=parts.mapNotNull { it.imageSource })
        UserContent(result, text(item,"post_id", "pid"), parts.joinToString("") { it.text }, item.optInt("is_post_deleted") == 1)
    }.distinctBy { it.postId.ifBlank { it.thread.id } }
    return ContentPage(items, page, CommunityParser.more(data,page,items.size >= 20))
}

internal suspend fun TiebaApi.people(uid: String, fans: Boolean, page: Int): PeoplePage {
    val data = request(if (fans) "/c/u/fans/page" else "/c/u/follow/followList", mapOf("uid" to uid, "pn" to "$page", "_client_version" to "22.13.1.1"), authenticated = true)
    val array = data.optJSONArray(if (fans) "user_list" else "follow_list") ?: data.optJSONArray("user_list")
        ?: data.optJSONArray("fans_list")
    if (array == null && !data.has("has_more") && !data.has("page")) throw IOException("贴吧没有返回关系列表")
    val users = objects(array).map(CommunityParser::profile)
    return PeoplePage(users,page,CommunityParser.more(data,page,users.size>=20))
}

internal suspend fun TiebaApi.followUser(profile: UserProfile, undo: Boolean) {
    require(profile.portrait.isNotBlank()) { "没有获取到该用户的头像标识，请刷新资料" }
    request(if (undo) "/c/c/user/unfollow" else "/c/c/user/follow", mapOf("portrait" to profile.portrait, "tbs" to tbs()), authenticated = true)
}

internal suspend fun TiebaApi.cloudCollection(uid: String, page: Int): ContentPage {
    val data = request("/c/f/post/threadstore", mapOf("user_id" to uid, "rn" to "20", "offset" to "${(page-1)*20}"), authenticated = true)
    val array = data.optJSONArray("store_thread") ?: throw IOException("贴吧未返回云收藏列表")
    val items = objects(array).map { item ->
        val a = item.optJSONObject("author") ?: JSONObject()
        val author = JSONObject(a.toString()).put("id",text(a,"lz_uid", "id")).put("portrait",text(a,"user_portrait", "portrait"))
        UserContent(thread(item, text(item,"forum_name")).copy(author=IdentityParser.person(author), timestamp=item.optLong("last_time")), deleted=item.optInt("is_deleted")==1)
    }.filter { it.thread.id.isNotBlank() }
    return ContentPage(items,page,CommunityParser.more(data,page,items.size>=20))
}

internal suspend fun TiebaApi.saveCloud(threadId: String, remove: Boolean) {
    val fields = mutableMapOf("tbs" to tbs())
    if (remove) fields["tid"] = threadId else fields["data"] = JSONArray().put(JSONObject().put("tid",threadId).put("pid","0").put("status","0").put("type","0")).toString()
    request(if (remove) "/c/c/post/rmstore" else "/c/c/post/addstore", fields, authenticated=true)
}

internal suspend fun TiebaApi.deleteOwn(content: UserContent, thread: Boolean) {
    val fields = mutableMapOf("fid" to content.thread.forumId, "word" to content.thread.forum, "z" to content.thread.id,
        "tbs" to tbs(), "src" to "1", "delete_my_post" to "1", "is_vipdel" to "0")
    if (!thread) { require(content.postId.isNotBlank()); fields["pid"]=content.postId; fields["isfloor"]="0" }
    request(if (thread) "/c/c/bawu/delthread" else "/c/c/bawu/delpost",fields,authenticated=true)
}

internal suspend fun TiebaApi.updateProfile(name: String, intro: String, gender: Int) {
    require(name.isNotBlank()); require(gender in 0..2)
    request("/c/c/profile/modify",mapOf("nick_name" to name, "intro" to intro, "sex" to "$gender", "tbs" to tbs()),authenticated=true)
}

internal suspend fun TiebaApi.signForum(name: String, token: String? = null): ForumSignResult {
    val actualToken = token ?: tbs()
    try {
        return ForumSignParser.result(request("/c/c/forum/sign", mapOf("kw" to name, "tbs" to actualToken,
            "_client_version" to "22.13.1.1"), authenticated = true))
    } catch (e: kotlinx.coroutines.CancellationException) { throw e
    } catch (e: LoginExpiredException) { throw e
    } catch (e: Exception) {
        // Already-signed codes vary. Confirm by reading the forum instead of guessing
        // that an error (or a transport timeout) means success. Never resend here.
        if (e is TiebaRejectedException && e.code == "need_vcode") throw e
        val signed = try { forum(name, 1).info?.signed == true
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel
        } catch (_: Exception) { false }
        if (signed) return ForumSignResult.AlreadySigned
        throw e
    }
}

internal suspend fun TiebaApi.search(query: String, kind: SearchKind, page: Int): SearchPage {
    val data = webGet("/mo/q/search/${kind.name.lowercase()}",mapOf("word" to query, "pn" to "$page", "st" to "0", "tt" to "1", "ct" to "2"))
    val body = data.optJSONObject("data") ?: throw IOException("贴吧未返回搜索结果")
    val more = CommunityParser.more(body,page,false)
    fun matches(): List<JSONObject> = buildList {
        body.optJSONObject("exactMatch")?.let { exact -> if (exact.has("forum_id") || exact.has("id")) add(exact) else exact.keys().forEach { k -> exact.optJSONObject(k)?.let(::add) } }
        objects(body.optJSONArray("exactMatch")).forEach(::add)
        objects(body.optJSONArray("fuzzyMatch")).forEach(::add)
        body.optJSONObject("fuzzyMatch")?.let { fuzzy -> fuzzy.keys().forEach { key -> fuzzy.optJSONObject(key)?.let(::add) } }
        objects(body.optJSONArray("user_list")).forEach(::add)
    }
    return when(kind) {
        SearchKind.Thread -> {
            val items = objects(body.optJSONArray("post_list")).map { item ->
                val user = item.optJSONObject("user") ?: JSONObject()
                val author = JSONObject(user.toString()).put("id",text(user,"user_id","id")).put("name_show",text(user,"name_show","user_name"))
                thread(item,text(item,"forum_name")).copy(title=CommunityParser.clean(text(item,"title")).ifBlank { "搜索结果" },
                    excerpt=CommunityParser.clean(text(item,"content")), timestamp=item.optLong("time"), author=IdentityParser.person(author), replies=item.optInt("post_num"), repliesKnown = number(item,"post_num") != null)
            }.filter { it.id.isNotBlank() }.distinctBy { it.id }
            SearchPage(threads=items,page=page,hasMore=more)
        }
        SearchKind.Forum -> SearchPage(forums=matches().map { ForumItem(text(it,"forum_id","id"),text(it,"forum_name","name"),IdentityParser.imageUrl(text(it,"avatar")),text(it,"intro","slogan")) }.filter { it.name.isNotBlank() }.distinctBy { it.name },page=page,hasMore=more)
        SearchKind.User -> SearchPage(users=matches().map(CommunityParser::profile).filter { it.person.id.isNotBlank() }.distinctBy { it.person.id },page=page,hasMore=more)
    }
}

internal suspend fun TiebaApi.notices(kind: NoticeKind, page: Int, cursor: String): NoticePage {
    val fields=mutableMapOf("pn" to "${page-1}","rn" to "20")
    if(kind==NoticeKind.Like) { fields["id"]=cursor; fields["_client_version"]="22.13.1.1" }
    val data=request("/c/u/feed/${kind.path}",fields,authenticated=true)
    val array=data.optJSONArray(kind.listKey)
    if(array==null && !data.has("page") && !data.has("has_more")) throw IOException("贴吧没有返回此分类消息")
    val items=objects(array).map(NoticeParser::parse)
    return NoticePage(items,page,CommunityParser.more(data,page,items.size>=20),items.lastOrNull()?.id ?: cursor)
}
