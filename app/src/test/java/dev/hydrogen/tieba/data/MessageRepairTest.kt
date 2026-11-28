package dev.hydrogen.tieba.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class MessageRepairTest {
    @Test fun agreeNotificationReadsActualNestedUserAndTarget() {
        val notice = NoticeParser.parse(JSONObject("""{"id":"cursor42","agreeer":{"id":89,"name_show":"点赞者","portrait":"tb.1.abc?token=a%2Bb"},
            "thread_info":{"id":111,"title":"原帖"},"post_info":{"id":222,"content":[{"text":"我的"},{"text":"评论"}]},"time":1000}"""))
        assertEquals("点赞者", notice.person.name)
        assertEquals("89", notice.person.id)
        assertEquals("https://himg.bdimg.com/sys/portrait/item/tb.1.abc?token=a%2Bb", notice.person.avatar)
        assertEquals("111", notice.threadId)
        assertEquals("222", notice.postId)
        assertEquals("cursor42", notice.id)
        assertEquals("我的评论", notice.text)
        assertEquals("原帖", notice.title)
    }
    @Test fun threadLikeMayOmitPostAndReplyNoticeKeepsBodyUnchanged() {
        assertEquals("", NoticeParser.parse(JSONObject("""{"agreeer":{"name":"甲"},"thread_info":{"id":"1"}}""")).postId)
        val notice = NoticeParser.parse(JSONObject("""{"replyer":{"name_show":"乙"},"thread_id":"1","post_id":"2","is_floor":"1","content":"回复 某某：正文","quote_content":"引用"}"""))
        assertTrue(notice.isFloor)
        assertEquals("回复 某某：正文", notice.text)
        assertEquals("回复我：", NoticeParser.action(NoticeKind.Reply))
        assertEquals("引用", notice.quote)
        assertEquals("正文", NoticeParser.body(notice, NoticeKind.Reply, "某某"))
        assertEquals("正文", NoticeParser.body(notice.copy(text = "回复 [某某]：正文"), NoticeKind.Reply, "某某"))
        assertEquals(notice.text, NoticeParser.body(notice, NoticeKind.Reply, "其他人"))
        assertEquals(notice.text, NoticeParser.body(notice, NoticeKind.At, "某某"))
    }
    @Test fun missingNotificationUserStaysHonest() {
        val notice = NoticeParser.parse(JSONObject("""{"agreeer":null,"content":null}"""))
        assertEquals("吧友", notice.person.name)
        assertEquals("", notice.person.avatar)
        assertEquals("", notice.text)
    }

    private fun post(id: String, floor: Int, nested: List<PostItem> = emptyList()) = PostItem(id, floor, Person(),
        listOf(ContentPart(text = "正文$id")), 0, 0, nested.size, previewReplies = nested)
    private val thread = ThreadItem("1", "原帖", "测试")

    @Test fun pidResponseLocatesPageWithoutReplacingIt() {
        val locator = ThreadPage(thread, listOf(post("46", 46)), 1, true)
        assertEquals(3, ReplyLocation.page(locator, "46"))
        val first = ThreadPage(thread, (1..20).map { post("$it", it) }, 1, true)
        val full = ThreadPage(thread.copy(title = "后页占位"), (41..60).map { post("$it", it) }, 3, true)
        val result = ReplyLocation.withRoot(first, full)
        assertEquals(21, result.posts.size)
        assertEquals("正文1", result.posts.first().copyableText())
        assertTrue(result.posts.any { it.id == "45" })
        assertTrue(result.posts.any { it.id == "47" })
        assertEquals(thread, result.thread)
        assertEquals(3, result.page)
        assertTrue(result.hasMore)
    }
    @Test fun subReplyFindsParentAndRootIsNotDuplicated() {
        val parent = post("50", 50, listOf(post("501", 0)))
        assertEquals(parent, ReplyLocation.parent(listOf(post("1", 1), parent), "501"))
        assertEquals(3, ReplyLocation.page(ThreadPage(thread, listOf(parent), 1, false), "501"))
        assertNull(ReplyLocation.parent(listOf(parent), "deleted"))
        val first = ThreadPage(thread, listOf(post("1", 1), post("2", 2)), 1, false)
        assertEquals(first, ReplyLocation.withRoot(first, first))
    }
    @Test fun aLocatorWithOnlyTheParentRequiresAnExactSubreplyMatch() = kotlinx.coroutines.runBlocking {
        val parent = post("50", 5).copy(subReplies = 31)
        val locator = ThreadPage(thread, listOf(parent), 1, false)
        val full = ThreadPage(thread, listOf(post("1", 1), parent), 1, false)
        val matched = ReplyLocation.confirmNested(locator, full, "501") { parentId, page ->
            assertEquals("50", parentId)
            FloorPage(listOf(post(if (page == 1) "500" else "501", 0)), page, page == 1)
        }
        assertEquals(parent, matched?.first)
        assertEquals(FloorPage(listOf(post("500", 0), post("501", 0)), 2, false), matched?.second)
        assertNull(ReplyLocation.confirmNested(locator, full, "deleted") { _, page ->
            FloorPage(listOf(post("500", 0)), page, false)
        })
    }

    @Test fun personalReplyUsesFirstPageWhenLocatorSuggestsUnrelatedLaterPage() {
        val parent = post("5", 5, listOf(post("501", 0)))
        val first = ThreadPage(thread, listOf(post("1", 1), parent), 1, true)
        val suggested = ThreadPage(thread, listOf(post("33", 33)), 2, false)
        assertEquals(first, ReplyLocation.matchingPage(first, suggested, "501"))
        assertEquals(suggested, ReplyLocation.matchingPage(first, suggested, "33"))
        assertEquals(suggested, ReplyLocation.matchingPage(first, suggested, "unknown"))
    }

    @Test fun actualServicePageTakesPrecedenceOverEstimatedFloorPage() {
        assertEquals(7, ReplyLocation.page(ThreadPage(thread, listOf(post("99", 46)), 7, true), "99"))
    }

    @Test fun standardEmojiAndLegacyUrlResolveToTlsCdn() {
        val first = EmojiParser.parse(JSONObject("""{"type":2,"text":"image_emoticon","c":"呵呵"}"""))
        assertEquals("https://tb2.bdstatic.com/tb/editor/images/client/image_emoticon1.png", first.emoji)
        val face = EmojiParser.parse(JSONObject("""{"text":"image_emoticon25","c":"滑稽","src":"http://static.tieba.baidu.com/tb/editor/images/client/image_emoticon25.png?x=a%2Bb"}"""))
        assertTrue(face.emoji.endsWith("image_emoticon25.png?x=a%2Bb"))
        assertTrue(face.emoji.startsWith("https://tb2.bdstatic.com/"))
        assertEquals("[滑稽]", face.text)
        assertEquals("", face.image)
    }
    @Test fun customEmojiUsesReturnedResourceAndUnknownKeepsDescription() {
        val custom = EmojiParser.parse(JSONObject("""{"text":"new-package-123","c":"[自定义]","src":"https://tiebapic.baidu.com/emoji/a.gif?sig=abc"}"""))
        assertEquals("https://tiebapic.baidu.com/emoji/a.gif?sig=abc", custom.emoji)
        assertEquals("[自定义]", custom.text)
        assertEquals("", EmojiParser.parse(JSONObject("""{"text":"unknown","c":"未知"}""")).emoji)
        for (url in listOf("file:///x.png", "https://baidu.com.evil.example/a.png", "javascript:alert(1)", "https://x@tb2.bdstatic.com/a.png"))
            assertEquals(url, "", EmojiParser.resource(url))
    }
    @Test fun emojiPrefersOriginalResourceOverThumbnailAndRejectsInvalidOriginal() {
        val raw = JSONObject().put("text", "custom").put("src", "https://tb2.bdstatic.com/thumb.png")
            .put("origin_src", "https://tiebapic.baidu.com/original.gif?sig=a%2Bb")
        assertEquals("https://tiebapic.baidu.com/original.gif?sig=a%2Bb", EmojiParser.parse(raw).emoji)
        raw.put("origin_src", "file:///invalid.png")
        assertEquals("https://tb2.bdstatic.com/thumb.png", EmojiParser.parse(raw).emoji)
    }
    @Test fun emojiIsNotMergedIntoTextOrIncludedInImageGallery() {
        val parts = TiebaApi("tests").content(JSONArray("""[{"type":0,"text":"前"},{"type":2,"text":"image_emoticon25","c":"滑稽"},{"type":0,"text":"后"}]"""))
        assertEquals(3, parts.size)
        assertTrue(parts[1].emoji.isNotBlank())
        assertTrue(parts.all { it.image.isBlank() })
        assertEquals("前[滑稽]后", post("2", 2).copy(content = parts).copyableText())
    }
    @Test fun groupHistoryUnwrapsNestedJsonAndSortsChronologically() {
        val content = JSONObject().put("text", JSONObject().put("content_body", JSONObject().put("text", "群消息").toString()).toString())
        val first = JSONObject().put("msgid", "12").put("from_user", "33").put("create_time", 20).put("type", 1).put("content", content.toString())
        val earlier = JSONObject().put("msgid", "11").put("from_user", 0).put("create_time", 10).put("content", "{}")
        val history = ChatParser.history(JSONObject().put("err_code", 0).put("messages", JSONArray().put(first).put(earlier).put(first)))
        assertEquals(listOf("11", "12"), history.messages.map { it.id })
        assertEquals("群消息", history.messages.last().text)
        assertEquals("33", history.messages.last().senderId)
        assertFalse(history.hasMore)
    }
    @Test fun groupProtocolDoesNotTreatMissingResultAsSuccess() {
        for (raw in listOf("{}", """{"err_code":0}""", """{"err_code":12,"err_msg":"没有权限"}""")) {
            assertTrue(runCatching { ChatParser.history(JSONObject(raw)) }.exceptionOrNull() is IOException)
        }
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", ChatParser.sign(emptyMap()))
        assertEquals(ChatParser.sign(mapOf("b" to 2, "a" to 1)), ChatParser.sign(mapOf("a" to 1, "b" to 2)))
    }
    @Test fun groupDirectoryFlattensServerGroupsWithoutInventingRooms() {
        val rooms = ChatParser.rooms(JSONObject("""{"list":[{"room_list":[{"id":"12","name":"测试群","avatar":"https://himg.bdimg.com/a.jpg"},{"id":""}]},{"room_list":[{"id":"12"},{"room_id":"13","room_name":"其他群"}]}]}"""), "测试")
        assertEquals(listOf("12", "13"), rooms.map { it.id })
        assertEquals("测试群", rooms.first().name)
        assertEquals("测试", rooms.first().forum)
        assertNull(rooms.first().members)
        val detail = ChatParser.room(JSONObject("""{"basic_info":{"id":"12","name":"真群名","forum_name":"真实吧名","discuss_num":123},"extra_info":{"announce":"公告"}}"""))
        assertEquals(123L, detail.members)
        assertEquals("公告", detail.announcement)
    }
}
