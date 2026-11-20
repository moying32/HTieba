package dev.hydrogen.tieba.data

import org.json.JSONObject
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.security.MessageDigest

data class ChatRoom(val id: String, val name: String, val avatar: String = "", val forum: String = "",
    val members: Long? = null, val announcement: String = "", val forumId: String = "",
    val talkThreshold: Int? = null, val talkBanned: Boolean? = null, val bots: List<Person> = emptyList(),
    val detailsLoaded: Boolean = false)
data class ChatMessage(val id: String, val senderId: String, val text: String, val time: Long, val type: Int,
    val person: Person = Person(id = senderId), val image: String = "", val link: String = "",
    val msgKey: String = "", val revoked: Boolean = false, val recalledIds: List<String> = emptyList())
data class ChatHistory(val messages: List<ChatMessage>, val hasMore: Boolean)

/** Official Baidu HTTPS chat reads. No implicit joins, receipts or sends. */
internal object ChatParser {
    fun room(raw: JSONObject, forum: String = ""): ChatRoom {
        val basic = raw.optJSONObject("basic_info") ?: raw.optJSONObject("room_info") ?: raw
        val extra = raw.optJSONObject("extra_info")
        val bots = CommunityParser.objects(raw.optJSONObject("bots")?.optJSONArray("bot_list")).mapNotNull { bot ->
            val user = bot.optJSONObject("user") ?: return@mapNotNull null
            val id = CommunityParser.text(user,"user_id","id").ifBlank { GroupChatProtocol.userId(CommunityParser.text(user,"uk")) }
            Person(id,CommunityParser.text(user,"name_show","name").ifBlank { "群机器人" },
                IdentityParser.chatAvatar(CommunityParser.text(user,"portrait")),role = "机器人")
        }
        return ChatRoom(CommunityParser.text(basic, "id", "room_id", "chatroom_id").ifBlank { CommunityParser.text(raw,"room_id","chatroom_id") },
            CommunityParser.text(basic, "name", "room_name").ifBlank { "吧群" },
            IdentityParser.imageUrl(CommunityParser.text(basic, "avatar")),
            CommunityParser.text(basic, "forum_name").ifBlank { forum },
            CommunityParser.number(basic, "discuss_num", "member_num")?.takeIf { it >= 0 },
            CommunityParser.text(extra, "announce"),CommunityParser.text(basic,"forum_id","fid"),
            CommunityParser.number(extra,"talk_threshold_level")?.takeIf { it in 0L..99L }?.toInt(),
            CommunityParser.text(extra,"is_talk_banned").let { when(it) { "1" -> true; "0" -> false; else -> null } },bots)
    }
    fun rooms(data: JSONObject, forum: String): List<ChatRoom> = CommunityParser.objects(data.optJSONArray("list"))
        .flatMap { CommunityParser.objects(it.optJSONArray("room_list")) }.map { room(it, forum) }
        .filter { it.id.toLongOrNull()?.let { id -> id > 0 } == true }.distinctBy { it.id }

    private fun objectValue(value: Any?): JSONObject? = when (value) {
        is JSONObject -> value; is String -> runCatching { JSONObject(value) }.getOrNull(); else -> null
    }
    fun message(raw: JSONObject): ChatMessage {
        val content = objectValue(raw.opt("content"))
        val wrapper = objectValue(content?.opt("text"))
        val body = objectValue(wrapper?.opt("content_body")) ?: wrapper
        val ext = objectValue(wrapper?.opt("ext"))
        val sender = CommunityParser.text(raw, "from_user")
        val name = CommunityParser.text(wrapper, "name").ifBlank { CommunityParser.text(ext, "user_name") }
        val portrait = CommunityParser.text(wrapper, "portrait").ifBlank { CommunityParser.text(ext, "portrait") }
        val data = objectValue(wrapper?.opt("data"))
        val service = objectValue(data?.opt("service_info"))
        val config = objectValue(service?.opt("msg_conf"))
        val recalled = CommunityParser.text(service,"recall_msgid").takeIf { it.toLongOrNull()?.let { id -> id > 0 } == true }
        val text = (body?.opt("text") as? String).orEmpty().ifBlank {
            CommunityParser.text(config,"show_content").ifBlank {
                when {
                    recalled != null -> "消息已撤回"
                    data?.optJSONArray("msgids") != null && data.has("mcast_id") -> "群消息状态已更新"
                    else -> ""
                }
            }
        }
        val level = CommunityParser.number(ext,"level")?.takeIf { it in 0L..99L }?.toInt() ?: 0
        return ChatMessage(CommunityParser.text(raw, "msgid"), sender, text,
            raw.optLong("create_time"), raw.optInt("type"),
            Person(sender.takeUnless { it == "0" }.orEmpty(), name.ifBlank { if (sender == "0") "系统消息" else "吧友" },
                IdentityParser.chatAvatar(portrait),level = level), IdentityParser.imageUrl(CommunityParser.text(body, "image_url", "pic_url").ifBlank {
                    if(body?.has("thumbnail") == true || body?.has("pic_id") == true) CommunityParser.text(body,"url","thumbnail") else ""
                }),
            CommunityParser.text(body, "link", "url").takeIf { it.startsWith("https://tieba.baidu.com/p/") }.orEmpty(),
            CommunityParser.text(raw,"msg_key"), recalledIds = listOfNotNull(recalled))
    }
    fun history(raw: JSONObject): ChatHistory {
        val code = CommunityParser.text(raw, "err_code")
        if (code != "0") throw IOException(CommunityParser.text(raw, "err_msg").ifBlank { "群消息暂不可用（$code）" })
        val array = raw.optJSONArray("messages") ?: throw IOException("贴吧未返回群消息列表")
        if ((0 until array.length()).any { array.optJSONObject(it) == null }) throw IOException("贴吧返回了无法识别的群消息，请重试")
        val messages = CommunityParser.objects(array).map(::message)
        if (messages.any { it.id.toLongOrNull()?.let { id -> id > 0 } != true }) throw IOException("贴吧群消息缺少有效编号，请重试")
        return ChatHistory(messages.distinctBy { it.id }.sortedWith(compareBy<ChatMessage> { it.time }.thenBy { it.id.toLongOrNull() ?: 0L }), array.length() >= 60)
    }
    fun sign(fields: Map<String, Any>): String = MessageDigest.getInstance("MD5")
        .digest(fields.toSortedMap().entries.joinToString("") { "${it.key}=${it.value}" }.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}

internal suspend fun TiebaApi.chatRooms(forumName: String): List<ChatRoom> {
    val info = forum(forumName, 1)
    require(info.forumId.isNotBlank()) { "没有获取到吧 ID" }
    val response = request("/c/f/chat/getRoomListByFid", mapOf("fid" to info.forumId, "call_from" to "frs", "_client_version" to "22.13.1.1"), authenticated = true)
    return ChatParser.rooms(response.optJSONObject("data") ?: throw IOException("贴吧未返回吧群列表"), info.name)
        .map { it.copy(forumId = it.forumId.ifBlank { info.forumId }) }
}

internal suspend fun TiebaApi.chatRoom(room: ChatRoom): ChatRoom {
    val fields = sortedMapOf("_client_version" to "22.13.1.1", "chatroom_id" to room.id)
    val url = (NativeProtocol.APP_HOST + "/c/f/chat/getChatRoomDetail").toHttpUrl().newBuilder().apply {
        fields.forEach { (k, v) -> addQueryParameter(k, v) }; addQueryParameter("sign", NativeProtocol.sign(fields))
    }.build()
    val request = Request.Builder().url(url).header("Cookie", BaiduSession.cookies()).build()
    val response = JSONObject(execute(request))
    NativeProtocol.ensureSuccess(response)
    val detail = ChatParser.room(response.optJSONObject("data") ?: throw IOException("贴吧未返回群资料"), room.forum)
    require(detail.id == room.id) { "贴吧返回的群资料与当前群不一致，请重试" }
    return detail.copy(detailsLoaded = true)
}

internal suspend fun TiebaApi.withdrawGroupMessage(room: ChatRoom, message: ChatMessage, owner: String) {
    require(GroupChatRules.canWithdraw(room,message,owner)) { "只能撤回有完整编号的本人群消息，请先刷新" }
    request("/c/c/chatroom/withdrawMsg",mapOf("forum_id" to room.forumId,"chatroom_id" to room.id,
        "msg_id" to message.id,"msg_key" to message.msgKey,"uid_to" to owner,"tbs" to tbs(owner)),authenticated = true)
}

internal suspend fun TiebaApi.chatMessages(roomId: String, deviceId: String, before: String = ""): ChatHistory {
    val fields = sortedMapOf<String, Any>("appid" to 10773430, "mcast_id" to roomId.toLong(),
        "msgid_end" to (before.toLongOrNull() ?: Long.MAX_VALUE), "count" to -60, "category" to 4,
        "app_version" to "22.13.1.1", "sdk_version" to 11250036, "device_id" to deviceId,
        "timestamp" to System.currentTimeMillis() / 1000)
    val body = JSONObject(fields).put("sign", ChatParser.sign(fields)).toString().toRequestBody("application/json; charset=utf-8".toMediaType())
    val request = Request.Builder().url("https://pim.baidu.com/rest/3.0/im/fetch_mcast_msg_client")
        .header("Cookie", "BDUSS=${BaiduSession.cookie("BDUSS")}").post(body).build()
    return ChatParser.history(JSONObject(execute(request)))
}
