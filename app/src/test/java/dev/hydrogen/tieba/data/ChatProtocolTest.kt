package dev.hydrogen.tieba.data

import dev.hydrogen.tieba.ui.ChatStorage
import dev.hydrogen.tieba.ui.Conversation
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class ChatProtocolTest {
    @Test fun protobufKeepsEmptyStringsNestedMessagesAndLargeIdentifiers() = ChatProto.run {
        val payload = encode(n(1,Long.MAX_VALUE),s(2,"中文🙂"),b(3,encode(n(2,11187491253))),s(4,""))
        val parsed = parse(payload)
        assertEquals(Long.MAX_VALUE,parsed.number(1)); assertEquals("中文🙂",parsed.text(2))
        assertEquals(11187491253,parse(parsed.bytes(3)).number(2))
        assertEquals(2,parsed.first { it.number == 4 }.wire)
        assertArrayEquals(payload,encode(*parsed.toTypedArray()))
    }
    @Test fun protobufRejectsTruncationOversizedLengthsAndInvalidTags() {
        listOf(byteArrayOf(10,10,1),byteArrayOf(0),ByteArray(11) { 0xff.toByte() },byteArrayOf(15)).forEach { bad ->
            assertTrue(runCatching { ChatProto.parse(bad) }.isFailure)
        }
    }
    @Test fun serverErrorMustNotBecomeSuccessfulEmptyHistory() = ChatProto.run {
        val error = parse(encode(b(1,encode(n(1,300),s(2,"拒绝发送")))))
        assertEquals("拒绝发送",runCatching { check(error) }.exceptionOrNull()?.message)
    }
    @Test fun boundedGzipAcceptsOrdinaryDataAndRejectsExpansionBombs() {
        fun gzip(bytes: ByteArray): ByteArray { val out = ByteArrayOutputStream(); GZIPOutputStream(out).use { it.write(bytes) }; return out.toByteArray() }
        val text = "贴吧消息".toByteArray(); assertArrayEquals(text,ChatProto.inflate(gzip(text)))
        assertTrue(runCatching { ChatProto.inflate(gzip(ByteArray(4 * 1024 * 1024 + 1))) }.isFailure)
    }
    @Test fun nestedGroupContentReadsActualNameAvatarAndImage() {
        val wrapper = JSONObject().put("name","真实昵称").put("portrait","tb.1.portrait?t=12")
            .put("content_body",JSONObject().put("text","原文").put("pic_url","https://example.com/image.jpg").toString())
        val raw = JSONObject().put("msgid","5").put("from_user",42).put("create_time",1000).put("type",81)
            .put("content",JSONObject().put("text",wrapper.toString()).toString())
        val parsed = ChatParser.message(raw)
        assertEquals("真实昵称",parsed.person.name); assertEquals("42",parsed.person.id)
        assertEquals("https://himg.bdimg.com/sys/portrait/item/tb.1.portrait?t=12",parsed.person.avatar)
        assertEquals("原文",parsed.text); assertEquals("https://example.com/image.jpg",parsed.image)
        assertEquals("系统消息",ChatParser.message(JSONObject().put("from_user",0)).person.name)
    }
    @Test fun badHistoryDoesNotWipeExistingContentWithInventedSuccess() {
        assertTrue(runCatching { ChatParser.history(JSONObject().put("err_code",300)) }.isFailure)
        assertTrue(runCatching { ChatParser.history(JSONObject().put("err_code",0)) }.isFailure)
        assertEquals(emptyList<ChatMessage>(),ChatParser.history(JSONObject().put("err_code",0).put("messages",JSONArray())).messages)
    }
    @Test fun unknownHistoryEntriesCannotMasqueradeAsEmptyOrExhaustedHistory() {
        listOf(JSONArray().put("unknown"), JSONArray().put(JSONObject().put("msgid", "not-a-number")),
            JSONArray().put(JSONObject().put("msgid", 0))).forEach { malformed ->
            assertTrue(runCatching { ChatParser.history(JSONObject().put("err_code",0).put("messages",malformed)) }.isFailure)
        }
    }
    @Test fun directoryReadsNestedRoomInfoAndOuterIdentifier() {
        val raw = JSONObject().put("room_id","8359094429").put("room_info",JSONObject()
            .put("name","android吧签到打卡").put("avatar","https://tiebapic.baidu.com/chatroom/pic/item/example.jpg").put("forum_name",""))
        val parsed = ChatParser.rooms(JSONObject().put("list",JSONArray().put(JSONObject().put("room_list",JSONArray().put(raw)))),"android").single()
        assertEquals("8359094429",parsed.id); assertEquals("android吧签到打卡",parsed.name)
        assertEquals("android",parsed.forum); assertTrue(parsed.avatar.endsWith("example.jpg"))
        val detail = ChatParser.room(JSONObject().put("basic_info",JSONObject().put("id",parsed.id).put("name","详情名称").put("discuss_num",12)))
        assertEquals(parsed.id,detail.id); assertEquals("详情名称",detail.name); assertEquals(12L,detail.members)
    }
    @Test fun groupImageUsesMediaUrlButThreadLinkIsNotAnImage() {
        fun message(body: JSONObject): ChatMessage = ChatParser.message(JSONObject().put("type",81)
            .put("content",JSONObject().put("text",JSONObject().put("name","示例吧友")
                .put("content_body",body.toString()).toString()).toString()))
        val image = message(JSONObject().put("url","https://tiebapic.baidu.com/forum/pic/item/full.jpg")
            .put("thumbnail","https://tiebapic.baidu.com/forum/pic/item/small.jpg").put("pic_id","sample"))
        assertTrue(image.image.endsWith("full.jpg")); assertEquals("",image.text); assertEquals("",image.link)
        val link = message(JSONObject().put("url","https://tieba.baidu.com/p/11187366252"))
        assertEquals("",link.image); assertTrue(link.link.endsWith("11187366252"))
        assertEquals("",message(JSONObject().put("unrecognized","value")).image)
    }
    @Test fun actualGroupServiceEnvelopeReadsServerNoticeAndRecallTarget() {
        val service = JSONObject().put("recall_msgid", "123").put("msg_conf", JSONObject().put("show_content", "撤回了一条消息"))
        val wrapper = JSONObject().put("room_id", "9").put("data", JSONObject().put("service_info", service).toString())
        val raw = JSONObject().put("msgid", "456").put("type",81).put("from_user",0)
            .put("content",JSONObject().put("text",wrapper.toString()).toString())
        val notice = ChatParser.message(raw)
        assertEquals("撤回了一条消息",notice.text)
        assertEquals(listOf("123"),notice.recalledIds)
        val old = m("123").copy(image = "https://example.com/a.jpg")
        val merged = ChatStorage.merge(listOf(old),listOf(notice))
        assertTrue(merged.first { it.id == "123" }.revoked)
        assertEquals("",merged.first { it.id == "123" }.image)
        val restored = ChatStorage.decode(JSONObject().put("conversations",ChatStorage.encode(listOf(Conversation("9",Person("9","群"),messages = merged)))).toString()).single()
        assertEquals(listOf("123"),restored.messages.first { it.id == "456" }.recalledIds)
        assertTrue(ChatStorage.merge(restored.messages,listOf(old)).first { it.id == "123" }.revoked)
    }
    @Test fun unknownGroupEnvelopesRemainUnknownAndInvalidRecallIdsAreIgnored() {
        val wrapper = JSONObject().put("data",JSONObject().put("service_info",JSONObject().put("recall_msgid",0)))
        val parsed = ChatParser.message(JSONObject().put("content",JSONObject().put("text",wrapper)))
        assertTrue(parsed.text.isEmpty()); assertTrue(parsed.recalledIds.isEmpty())
    }
    private fun m(id: String, sender: String = "42", text: String = "相同文本", time: Long = 100) = ChatMessage(id,sender,text,time,1,Person(sender,"甲"))
    @Test fun acknowledgedGroupSendReconcilesOnceButRealRepeatedTextsRemain() {
        val merged = ChatStorage.merge(listOf(m("ack:1"),m("ack:2")),listOf(m("100")))
        assertEquals(2,merged.size); assertEquals(1,merged.count { it.id.startsWith("ack:") })
        assertEquals(2,ChatStorage.merge(listOf(m("ack:1"),m("ack:2")),listOf(m("100"),m("100"))).size)
        val again = ChatStorage.merge(merged,listOf(m("100"),m("101")))
        assertEquals(listOf("100","101"),again.map { it.id })
        assertEquals(3,ChatStorage.merge(listOf(m("1"),m("2")),listOf(m("3"))).size)
        assertEquals(2,ChatStorage.merge(listOf(m("1")),listOf(m("1","43"))).size)
    }
    @Test fun olderPagesAreNotDroppedByInMemoryCacheLimit() {
        val existing = (100..499).map { m("$it",time = it.toLong()) }
        assertEquals(460,ChatStorage.merge(existing,(40..99).map { m("$it",time = it.toLong()) }).size)
    }
    @Test fun cacheRoundTripKeepsBaiduIdentityLocalPreferencesAndDifferentConversationKinds() {
        val private = Conversation("42",Person("42","甲","https://example.com/a"),messages = listOf(m("1")),pinned = true,saved = setOf("42:1"),seen = 9,syncedAt = 123)
        val group = Conversation("42",Person("42","群"),ChatRoom("42","群",forum = "示例",members = 100,announcement = "公告"),messages = listOf(m("2")))
        val result = ChatStorage.decode(JSONObject().put("conversations",ChatStorage.encode(listOf(private,group))).toString())
        assertEquals(listOf(private,group),result)
        assertTrue(ChatStorage.decode("not json").isEmpty())
    }
    @Test fun groupBdukMatchesIndependentProtocolFixture() {
        // Independently generated using .NET AES-CBC/PKCS7, not this codec.
        assertEquals("nrB7MJMuOtaWXCVZoYekRA",GroupChatProtocol.bduk("42"))
        assertTrue(runCatching { GroupChatProtocol.bduk("0") }.isFailure)
    }
}
