package dev.hydrogen.tieba.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CommunityParserTest {
    @Test fun newestForumPlaceholderIsAcceptedOnlyWithRealForumAndThreads() {
        assertTrue(CommunityParser.forumExists(JSONObject("""{"forum":{"id":21841105,"is_exists":0},"thread_list":[{"id":11216658413}]}"""), 1))
        assertTrue(CommunityParser.forumExists(JSONObject("""{"forum":{"id":2,"is_exists":1},"thread_list":[]}"""), 1))
        assertTrue(CommunityParser.forumExists(JSONObject("""{"forum":{"is_exists":0},"thread_list":[{"id":3}]}"""), 2))
        for (raw in listOf("{}", """{"forum":{"is_exists":0},"thread_list":[{"id":3}]}""",
            """{"forum":{"id":0,"is_exists":0},"thread_list":[{"id":3}]}""",
            """{"forum":{"id":2,"is_exists":0},"thread_list":[]}""",
            """{"forum":{"id":2,"is_exists":0},"thread_list":[{}, {"id":0}]}""")) {
            assertFalse(raw, CommunityParser.forumExists(JSONObject(raw), 1))
        }
    }
    @Test fun profileBackgroundUsesOnlyValidServerImage() {
        assertEquals("https://imgsa.baidu.com/cover.jpg", CommunityParser.profile(JSONObject("""{"user":{"bg_pic":"//imgsa.baidu.com/cover.jpg"}}""")).backgroundImage)
        listOf("", "null", "file:///sdcard/cover.jpg", "javascript:alert(1)", "{\"url\":\"https://imgsa.baidu.com/wrong.jpg\"}").forEach { image ->
            assertEquals("", CommunityParser.profile(JSONObject().put("bg_pic", image)).backgroundImage)
        }
        assertEquals("", CommunityParser.profile(JSONObject()).backgroundImage)
    }
    @Test fun missingCountsRemainUnknownAndZeroRemainsZero() {
        val profile = CommunityParser.profile(JSONObject("""{"id":"1","name_show":"显示昵称","name":"旧用户名","fans_num":"0"}"""))
        assertEquals("显示昵称", profile.person.name)
        assertNull(profile.follows)
        assertEquals(0L, profile.fans)
        assertNull(CommunityParser.views(JSONObject("""{"view_num":null,"read_num":-1}""")))
    }
    @Test fun replyCountOnlySubtractsRootForDetailProtocol() {
        val thread = JSONObject("""{"reply_num":"2"}""")
        assertEquals(1, CommunityParser.commentsCount(thread, true))
        assertEquals(2, CommunityParser.commentsCount(thread, false))
        assertEquals(0, CommunityParser.commentsCount(JSONObject(), true))
    }
    @Test fun groupedCommentsKeepTheirIndividualTargetsAndTimes() {
        val raw = JSONObject("""{"thread_id":"12","post_id":"13","create_time":10,"content":[
          {"post_id":"14","create_time":20,"post_content":[{"type":0,"text":"第一条"}]},
          {"post_id":"15","create_time":30,"post_content":[{"type":0,"text":"第二条"}]}]}""")
        val entries = CommunityParser.contentEntries(raw, false)
        assertEquals(listOf("14", "15"), entries.map { it.getString("post_id") })
        assertEquals(listOf(20L, 30L), entries.map { it.getLong("create_time") })
        assertEquals("第一条", entries[0].getJSONArray("content").getJSONObject(0).getString("text"))
        assertEquals("13", raw.getString("post_id"))
    }
    @Test fun groupedCommentWithoutOwnIdDoesNotInheritTheEnclosingId() {
        val raw = JSONObject("""{"thread_id":"12","post_id":"13","pid":"16","content":[
          {"post_id":"14","post_content":[]},{"post_content":[]}]}""")
        val entries = CommunityParser.contentEntries(raw, false)
        assertEquals(listOf("14", ""), entries.map { CommunityParser.text(it, "post_id", "pid") })
        assertFalse(UserContent(ThreadItem("12", "标题", "电脑")).hasDeleteTarget(false))
    }
    @Test fun duplicateOnlyIntermediatePageStillAllowsTheNextPage() {
        val thread = ThreadItem("12", "标题", "电脑")
        val first = UserContent(thread, "14")
        val second = UserContent(thread, "15")
        val overlap = ContentPage(listOf(first), 2, true).mergeUserContents(listOf(first), threads = false)
        assertEquals(ContentPage(listOf(first), 2, true), overlap)
        assertEquals(ContentPage(listOf(first, second), 3, false),
            ContentPage(listOf(second), 3, false).mergeUserContents(overlap.items, threads = false))
    }
    @Test fun threadAndCommentListsDeduplicateAtDifferentLevels() {
        val thread = ThreadItem("12", "标题", "电脑")
        val entries = listOf(UserContent(thread, "14"), UserContent(thread, "15"))
        val page = ContentPage(entries, 1, false)
        assertEquals(entries.take(1), page.mergeUserContents(emptyList(), threads = true).items)
        assertEquals(entries, page.mergeUserContents(emptyList(), threads = false).items)
    }
    @Test fun nestedIpMetadataIsNeverShownAsRawJson() {
        assertEquals("四川", CommunityParser.profile(JSONObject("""{"ip_address":{"location":"四川"}}""")).location)
        assertEquals("", CommunityParser.profile(JSONObject("""{"ip_address":{"other":1}}""")).location)
    }
    @Test fun optionalRichMediaSurvivesLocalStorage() {
        val thread = ThreadItem("1", "图文", "吧", views = 0L, mediaCount = 8,
            images = listOf("https://imgsrc.baidu.com/image.jpg"), video = "https://tb-video.bdstatic.com/test.mp4",
            poll = PollInfo("请选择", listOf("一", "二"), null))
        assertEquals(thread, ThreadStorage.decode(JSONObject(ThreadStorage.encode(thread).toString())))
        assertNull(ThreadStorage.decode(JSONObject("""{"id":"1","title":"旧记录"}""")).views)
    }
    @Test fun publishingDraftKeepsImagesAndUncertainSubmissionAcrossRestart() {
        val draft = PublishingDraft("owner", "电脑", "标题", "正文", listOf(DraftImage("content://picker/1", "#(pic,id,10,20)")), uncertain = true)
        assertEquals(draft, PublishingDraft.decode("owner", draft.encode(), "其他吧"))
        assertEquals("默认", PublishingDraft.decode("owner", "broken", "默认").forum)
    }
    @Test fun publishingRequiresConfirmedServerThreadId() {
        assertEquals("123", NativeProtocol.threadId(JSONObject("""{"thread":{"id":"123"}}""")))
        assertEquals("", NativeProtocol.threadId(JSONObject("""{"error_code":0,"tid":0}""")))
        assertEquals("", NativeProtocol.threadId(JSONObject("""{"error_code":0,"tid":"null"}""")))
    }
    @Test fun searchMarkupIsRenderedAsText() {
        assertEquals("Minecraft & 中文", CommunityParser.clean("<em>Minecraft</em> &amp; 中文"))
    }
}
