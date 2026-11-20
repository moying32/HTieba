package dev.hydrogen.tieba.data

import java.io.IOException
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject

internal enum class GroupBlockDuration(val seconds: Int, val label: String) {
    Hour(3600,"1 小时"), HalfDay(43200,"12 小时"), Day(86400,"1 天"),
    Week(604800,"7 天"), HalfMonth(1296000,"15 天"), Month(2592000,"30 天")
}

internal data class GroupBlockedUser(val name: String, val portrait: String, val remaining: String) {
    val avatar get() = IdentityParser.chatAvatar(portrait)
}
internal data class GroupBlockPage(val users: List<GroupBlockedUser>, val page: Int, val total: Long, val hasMore: Boolean)
internal data class GroupModerationTarget(val person: Person, val portrait: String)

/** Exact fields from the researched web/client endpoints; no inferred member roles. */
internal object GroupModerationProtocol {
    fun portrait(raw: String): String = raw.substringBefore('?').takeIf {
        it.length in 1..256 && Regex("[A-Za-z0-9._~-]+").matches(it)
    }.orEmpty()

    fun ensureWebSuccess(raw: JSONObject) {
        val code = CommunityParser.text(raw,"no")
        if(code.isBlank()) throw IOException("百度未确认群管理结果，请刷新核对")
        if(code != "0") throw TiebaRejectedException(code,CommunityParser.text(raw,"error","error_msg").ifBlank { "群管理被拒绝，请检查当前账号的吧务权限" }.take(160))
    }

    fun blocks(raw: JSONObject, requestedPage: Int): GroupBlockPage {
        require(requestedPage > 0)
        ensureWebSuccess(raw)
        val data = raw.optJSONObject("data") ?: throw IOException("百度未返回禁言名单")
        val page = CommunityParser.number(data,"pn")?.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt()
        val size = CommunityParser.number(data,"rn")?.takeIf { it in 1..100 }
        val total = CommunityParser.number(data,"total")
        if(page != requestedPage || size == null || total == null) throw IOException("禁言名单分页信息不完整，请重试")
        val array = data.optJSONArray("user_forbidden_list") ?: throw IOException("百度未返回禁言名单列表")
        val users = (0 until array.length()).map { i ->
            val user = array.optJSONObject(i) ?: throw IOException("禁言名单包含无法识别的账号")
            val id = portrait(CommunityParser.text(user,"portrait_id"))
            if(id.isBlank()) throw IOException("禁言名单缺少有效的账号标识")
            GroupBlockedUser(CommunityParser.text(user,"show_nickname","name").ifBlank { "吧友" },id,CommunityParser.text(user,"left_time_tip"))
        }.distinctBy { it.portrait }
        val more = page.toLong() * size < total
        if(more && users.isEmpty()) throw IOException("禁言名单未返回下一页内容，请重试")
        return GroupBlockPage(users,page,total,more)
    }

    fun target(profile: UserProfile, requestedUid: String, expectedPortrait: String = ""): GroupModerationTarget {
        require(requestedUid.toLongOrNull()?.let { it > 0 } == true) { "请输入有效的贴吧 UID" }
        require(profile.person.id == requestedUid) { "百度返回的账号与输入 UID 不一致，请重新核对" }
        val id = portrait(profile.portrait)
        require(id.isNotBlank()) { "百度未返回该账号的有效头像标识，暂不能执行群管理" }
        require(expectedPortrait.isBlank() || id == expectedPortrait) { "该 UID 与所选禁言账号不一致，请重新核对" }
        return GroupModerationTarget(profile.person,id)
    }

    private fun validate(room: ChatRoom, target: GroupModerationTarget, tbs: String) {
        require(room.id.toLongOrNull()?.let { it > 0 } == true && room.forumId.toLongOrNull()?.let { it > 0 } == true) { "群或所属吧编号缺失，请刷新群资料" }
        require(target.person.id.toLongOrNull()?.let { it > 0 } == true && portrait(target.portrait) == target.portrait && target.portrait.isNotBlank()) { "目标账号资料无效，请重新读取" }
        require(tbs.isNotBlank()) { "账号安全校验未完成" }
    }
    fun blockFields(room: ChatRoom, target: GroupModerationTarget, duration: GroupBlockDuration, tbs: String): Map<String,String> {
        validate(room,target,tbs)
        return mapOf("forum_id" to room.forumId,"chatroom_id" to room.id,"forbid_user_id" to target.portrait,"forbid_time" to duration.seconds.toString(),"tbs" to tbs)
    }
    fun unblockFields(room: ChatRoom, target: GroupModerationTarget, tbs: String): Map<String,String> {
        validate(room,target,tbs)
        return mapOf("forum_id" to room.forumId,"chatroom_id" to room.id,"op_type" to "2","block_uid" to target.person.id,"tbs" to tbs)
    }
}

internal suspend fun TiebaApi.groupBlocks(roomId: String, page: Int): GroupBlockPage {
    require(roomId.toLongOrNull()?.let { it > 0 } == true && page > 0)
    val url = (NativeProtocol.APP_HOST + "/mo/q/chat/getFobiddenList").toHttpUrl().newBuilder()
        .addQueryParameter("chatroom_id",roomId).addQueryParameter("pn",page.toString()).addQueryParameter("rn","30").build()
    val raw = JSONObject(execute(Request.Builder().url(url).header("Cookie",BaiduSession.cookies())
        .header("User-Agent","Mozilla/5.0 (Linux; Android 12) HydrogenTieba").build()))
    return GroupModerationProtocol.blocks(raw,page)
}

internal suspend fun TiebaApi.blockGroupUser(room: ChatRoom, target: GroupModerationTarget, duration: GroupBlockDuration, owner: String) {
    val session = BaiduSession.cookies()
    val fields = GroupModerationProtocol.blockFields(room,target,duration,tbs(owner))
    if(session != BaiduSession.cookies()) throw LoginExpiredException()
    val body = FormBody.Builder().apply { fields.forEach { (key,value) -> add(key,value) } }.build()
    val raw = JSONObject(execute(Request.Builder().url(NativeProtocol.APP_HOST + "/mo/q/chat/operateSpeech")
        .header("Cookie",session).header("Referer","https://tieba.baidu.com/")
        .header("User-Agent","Mozilla/5.0 (Linux; Android 12) HydrogenTieba").post(body).build()))
    GroupModerationProtocol.ensureWebSuccess(raw)
}

internal suspend fun TiebaApi.unblockGroupUser(room: ChatRoom, target: GroupModerationTarget, owner: String) {
    val session = BaiduSession.cookies()
    val fields = GroupModerationProtocol.unblockFields(room,target,tbs(owner))
    if(session != BaiduSession.cookies()) throw LoginExpiredException()
    request("/c/c/chatroom/controlSpeak",fields,authenticated = true)
}
