package dev.hydrogen.tieba.data

import dev.hydrogen.tieba.ui.ChatStorage
import dev.hydrogen.tieba.ui.Conversation
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GroupChatTest {
    private val room = ChatRoom("123","示例群",forum = "测试",forumId = "789")
    private fun message(id: String = "1", uid: String = "42", key: String = "key-$id") =
        ChatMessage(id,uid,"相同原文",100,81,Person(uid,"甲"),msgKey = key)

    @Test fun roomKeepsAnnouncementForumIdentityAndExplicitRestrictions() {
        val parsed = ChatParser.room(JSONObject().put("basic_info",JSONObject().put("id",123).put("forum_id",789).put("forum_name","测试").put("discuss_num",0))
            .put("extra_info",JSONObject().put("announce","公告\n第二行").put("talk_threshold_level","7").put("is_talk_banned","1")))
        assertEquals("789",parsed.forumId); assertEquals("测试",parsed.forum)
        assertEquals(0L,parsed.members); assertEquals(7,parsed.talkThreshold); assertEquals(true,parsed.talkBanned)
        assertEquals("公告\n第二行",parsed.announcement); assertNotNull(GroupChatRules.restriction(parsed))
        assertFalse(parsed.detailsLoaded)
    }
    @Test fun absentAndMalformedRightsNeverBecomePermissionToSpeak() {
        assertNull(ChatParser.room(JSONObject()).talkBanned)
        listOf("unknown","2","null","").forEach { value ->
            assertNull(ChatParser.room(JSONObject().put("extra_info",JSONObject().put("is_talk_banned",value))).talkBanned)
        }
        assertEquals(false,ChatParser.room(JSONObject().put("extra_info",JSONObject().put("is_talk_banned",0))).talkBanned)
        assertNull(GroupChatRules.restriction(room))
    }
    @Test fun invalidCountsAndOverflowingLevelsAreNotRenderedAsRealValues() {
        val parsed = ChatParser.room(JSONObject().put("basic_info",JSONObject().put("member_num",-1))
            .put("extra_info",JSONObject().put("talk_threshold_level",4294967297L)))
        assertNull(parsed.members); assertNull(parsed.talkThreshold)
        assertNull(ChatParser.room(JSONObject().put("extra_info",JSONObject().put("talk_threshold_level",-1))).talkThreshold)
    }
    @Test fun botsUseProtocolIdentityAndKeepMissingIdentityUnknown() {
        val list = JSONArray().put(JSONObject().put("user",JSONObject().put("name_show","助手").put("uk","nrB7MJMuOtaWXCVZoYekRA").put("portrait","tb.1.test")))
            .put(JSONObject().put("user",JSONObject().put("name","未知助手").put("uk","bad")))
        val bots = ChatParser.room(JSONObject().put("bots",JSONObject().put("bot_list",list))).bots
        assertEquals(listOf("42",""),bots.map { it.id }); assertEquals("助手",bots[0].name)
        assertEquals("机器人",bots[0].role); assertTrue(bots[0].avatar.endsWith("tb.1.test"))
    }
    @Test fun encryptedUserIdentityMatchesExternalFixtureAndRejectsGarbage() {
        assertEquals("42",GroupChatProtocol.userId("nrB7MJMuOtaWXCVZoYekRA"))
        listOf("","broken","0","null").forEach { assertEquals("",GroupChatProtocol.userId(it)) }
    }
    @Test fun messageKeepsCorrelationKeyAndForumLevelFromNestedStrings() {
        val wrapper = JSONObject().put("name","群友").put("content_body",JSONObject().put("text","正文").toString())
            .put("ext",JSONObject().put("level",12).toString())
        val parsed = ChatParser.message(JSONObject().put("msgid",7).put("msg_key","precise-key").put("from_user",42)
            .put("content",JSONObject().put("text",wrapper.toString()).toString()))
        assertEquals("precise-key",parsed.msgKey); assertEquals(12,parsed.person.level); assertEquals("正文",parsed.text)
    }
    @Test fun identicalTextsWithDifferentKeysCannotConfirmWrongSend() {
        val a = message("ack:a",key = "a"); val b = message("ack:b",key = "b")
        val result = ChatStorage.merge(listOf(a,b),listOf(message("7",key = "b")))
        assertEquals(listOf("ack:a","7"),result.map { it.id })
        assertEquals(3,ChatStorage.merge(listOf(a,b),listOf(message("7",key = "other"))).size)
    }
    @Test fun correlationKeyReconcilesRegardlessOfClockSkew() {
        val result = ChatStorage.merge(listOf(message("ack:a",key = "same")),listOf(message("7",key = "same").copy(time = 10000)))
        assertEquals(listOf("7"),result.map { it.id })
    }
    @Test fun missingKeyOnOneSideCannotFalselyConfirmKnownKey() {
        assertEquals(2,ChatStorage.merge(listOf(message("ack:a",key = "known")),listOf(message("7",key = ""))).size)
        assertEquals(2,ChatStorage.merge(listOf(message("ack:a",key = "")),listOf(message("7",key = "known"))).size)
    }
    @Test fun legacyMissingKeysKeepOneToOneFallbackWithoutMergingRealMessages() {
        val result = ChatStorage.merge(listOf(message("ack:a",key = ""),message("ack:b",key = "")),listOf(message("7",key = "")))
        assertEquals(2,result.size); assertEquals(1,result.count { it.id.startsWith("ack:") })
        assertEquals(2,ChatStorage.merge(listOf(message("1")),listOf(message("2"))).size)
    }
    @Test fun staleHistoryCannotResurrectLocallyConfirmedWithdrawal() {
        val result = ChatStorage.merge(listOf(message().copy(revoked = true)),listOf(message()))
        assertTrue(result.single().revoked); assertEquals("消息已撤回",GroupChatRules.preview(result.single()))
        assertFalse(ChatStorage.merge(listOf(message().copy(revoked = true)),listOf(message(uid = "43"))).last().revoked)
    }
    @Test fun revokedMessagesNeverPersistOrCopyPreviousMediaOrText() {
        val withdrawn = message().copy(revoked = true, image = "image", link = "link")
        val merged = ChatStorage.merge(listOf(withdrawn), listOf(message().copy(image = "image", link = "link"))).single()
        assertTrue(merged.revoked)
        assertEquals("", merged.text); assertEquals("", merged.image); assertEquals("", merged.link)
    }
    @Test fun explicitHistoryBoundaryOverridesWindowLength() {
        val messages = JSONArray().apply { repeat(60) { put(JSONObject().put("msgid", it + 1).put("from_user",42)) } }
        val raw = JSONObject().put("err_code",0).put("messages",messages).put("has_more",false)
        assertFalse(ChatParser.history(raw).hasMore)
        raw.put("messages", JSONArray().put(messages.getJSONObject(0))).put("has_more",true)
        assertTrue(ChatParser.history(raw).hasMore)
    }
    @Test fun recallRequiresOwnRealMessageAndCompleteIdentifiers() {
        assertTrue(GroupChatRules.canWithdraw(room,message(),"42"))
        assertFalse(GroupChatRules.canWithdraw(room,message(),"43"))
        assertFalse(GroupChatRules.canWithdraw(room,message(),""))
        assertFalse(GroupChatRules.canWithdraw(room,message("ack:a"),"42"))
        assertFalse(GroupChatRules.canWithdraw(room,message(key = ""),"42"))
        assertFalse(GroupChatRules.canWithdraw(room.copy(forumId = ""),message(),"42"))
        assertFalse(GroupChatRules.canWithdraw(room.copy(id = "0"),message(),"42"))
        assertFalse(GroupChatRules.canWithdraw(room,message(uid = "bad"),"bad"))
        assertFalse(GroupChatRules.canWithdraw(room,message().copy(revoked = true),"42"))
    }
    @Test fun historyParticipantIndexDeduplicatesAndExcludesSystemUsers() {
        val list = listOf(message(uid = "42").copy(person = Person("42","旧名")),message(uid = "0"),message(uid = "-1"),message(uid = "43"),message("2").copy(person = Person("42","新名",level = 8)))
        val people = GroupChatRules.participants(list)
        assertEquals(listOf("42","43"),people.map { it.id }); assertEquals("新名",people[0].name); assertEquals(8,people[0].level)
        assertTrue(GroupChatRules.participants(emptyList()).isEmpty())
    }
    @Test fun cacheRoundTripKeepsNewFieldsAndServerUnknownValues() {
        val full = room.copy(talkThreshold = 8,talkBanned = true,detailsLoaded = true,bots = listOf(Person("42","助手",role = "机器人")))
        val c = Conversation(room.id,Person(room.id,room.name),full,messages = listOf(message().copy(revoked = true,person = Person("42","甲",level = 9,role = "吧务"))))
        assertEquals(listOf(c),ChatStorage.decode(JSONObject().put("conversations",ChatStorage.encode(listOf(c))).toString()))
        val unknown = c.copy(room = room.copy(talkThreshold = null,talkBanned = null))
        assertEquals(listOf(unknown),ChatStorage.decode(JSONObject().put("conversations",ChatStorage.encode(listOf(unknown))).toString()))
    }
    @Test fun oldCacheRemainsReadableWithoutInventingRightsOrKeys() {
        val old = JSONObject().put("id","123").put("name","旧群").put("room",JSONObject().put("id","123").put("name","旧群"))
            .put("messages",JSONArray().put(JSONObject().put("id","1").put("sender","42").put("text","旧消息")))
        val c = ChatStorage.decode(JSONObject().put("conversations",JSONArray().put(old)).toString()).single()
        assertNull(c.room!!.talkBanned); assertNull(c.room.talkThreshold); assertFalse(c.room.detailsLoaded)
        assertEquals("",c.messages.single().msgKey); assertFalse(c.messages.single().revoked)
    }
    @Test fun joinFieldsTargetOnlyExplicitRoomAndAuthenticatedIdentity() {
        val fields = GroupChatProtocol.joinFields("123",456)
        assertEquals(201,fields.getInt("method")); assertEquals(123L,fields.getLong("mcast_id")); assertEquals(456L,fields.getLong("uk")); assertEquals(10773430,fields.getInt("appid"))
        listOf("0","-1","abc").forEach { assertTrue(runCatching { GroupChatProtocol.joinFields(it,456) }.isFailure) }
        assertTrue(runCatching { GroupChatProtocol.joinFields("123",0) }.isFailure)
    }
    @Test fun groupTextRoundTripsUnicodeEscapingAndUsesActualForumIdentity() {
        val text = "中文🙂\n\"引号\" \\ 路径"
        val outer = GroupChatProtocol.content(room,Person("42","发送者","https://himg.bdimg.com/sys/portrait/item/tb.1.test?t=1",level = 7),text,"bduk")
        val wrapper = JSONObject(outer.getString("text")); val body = JSONObject(wrapper.getString("content_body")); val ext = JSONObject(wrapper.getString("ext"))
        assertEquals(text,body.getString("text")); assertEquals("123",wrapper.getString("room_id")); assertEquals("bduk",wrapper.getString("baidu_uk"))
        assertEquals(789L,ext.getLong("forum_id")); assertEquals(7,ext.getInt("level")); assertEquals("tb.1.test",wrapper.getString("portrait"))
        assertFalse(wrapper.has("at_data"))
    }
    @Test fun groupTextRejectsEmptyOversizeAndDoesNotInventForumOrLevel() {
        listOf("","   ","字".repeat(2001)).forEach { assertTrue(runCatching { GroupChatProtocol.content(room,Person("42"),it,"key") }.isFailure) }
        val outer = GroupChatProtocol.content(room.copy(forumId = ""),Person("42"),"字".repeat(2000),"key")
        val ext = JSONObject(JSONObject(outer.getString("text")).getString("ext"))
        assertFalse(ext.has("forum_id")); assertFalse(ext.has("level"))
    }
    @Test fun previewRepresentsMediaUnknownTypesAndWithdrawalWithoutLeakingOldText() {
        assertEquals("[图片]",GroupChatRules.preview(message().copy(text = "",image = "image")))
        assertEquals("[帖子]",GroupChatRules.preview(message().copy(text = "",link = "link")))
        assertEquals("[消息类型 81]",GroupChatRules.preview(message().copy(text = "")))
        assertEquals("消息已撤回",GroupChatRules.preview(message().copy(revoked = true)))
    }
    @Test fun revokedQuoteNeverLeaksOriginalTextIntoNewMessage() {
        assertEquals("新消息",GroupChatRules.payload("新消息",message().copy(revoked = true)))
        assertEquals("新消息",GroupChatRules.payload("新消息",null))
        assertEquals("> 甲：[图片]\n\n新消息",GroupChatRules.payload("新消息",message().copy(text = "",image = "image")))
        assertEquals("> 甲：第一行 第二行\n\n新消息",GroupChatRules.payload("新消息",message().copy(text = "第一行\n第二行")))
    }
}
