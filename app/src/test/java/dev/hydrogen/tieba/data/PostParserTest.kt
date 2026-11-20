package dev.hydrogen.tieba.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PostParserTest {
    private val api = TiebaApi("tests")
    private fun parse(json: String, people: Map<String, Person> = emptyMap(), nested: Boolean = false) =
        PostParser.post(JSONObject(json), people, api::content, nested)

    @Test fun actualInlineEnvelopeResolvesItsOwnAuthorsAndKeepsServiceTotal() {
        val post = parse("""{"id":"100","floor":2,"author_id":"1","sub_post_number":8,
          "sub_post_list":{"pid":"100","sub_post_list":[{"id":"101","author_id":"2","floor":1,
          "content":[{"type":0,"text":"楼中楼内容"}]}]}}""",
            mapOf("1" to Person("1", "父楼", location = "北京"), "2" to Person("2", "子回复", location = "上海")))
        assertEquals(8, post.subReplies)
        assertEquals("北京", post.location)
        assertEquals("2", post.previewReplies.single().author.id)
        assertEquals("上海", post.previewReplies.single().location)
        assertEquals(0, post.previewReplies.single().floor)
        assertEquals("楼中楼内容", post.previewReplies.single().copyableText())
    }

    @Test fun previewDeduplicatesIdentifiersAndDoesNotInventMissingAuthorsOrLocations() {
        val post = parse("""{"id":"100","sub_post_list":[{"id":"101"},{"id":"101"},{"id":""}]}""")
        assertEquals(1, post.previewReplies.size)
        assertEquals("", post.location)
        assertEquals("", post.previewReplies.single().author.id)
        assertTrue(post.previewReplies.single().previewReplies.isEmpty())
    }

    @Test fun fullSubRepliesPreserveAgreeSelectionInsteadOfDefaultingToFalse() {
        val post = parse("""{"id":"101","floor":9,"author":{"id":"2","name_show":"回复人"},
            "agree":{"agree_num":43,"has_agree":1},"sub_post_list":[{"id":"999"}]}""", nested = true)
        assertTrue(post.liked)
        assertEquals(43, post.likes)
        assertEquals(0, post.floor)
        assertTrue(post.previewReplies.isEmpty())
    }

    @Test fun ipLocationDoesNotUseCoordinatesOrSerializeObjectsAsText() {
        assertEquals("四川", PostParser.location(JSONObject("""{"ip_address":"四川"}""")))
        assertEquals("广东", PostParser.location(JSONObject("""{"ip_address":{"location":"广东"}}""")))
        for (raw in listOf("{}", """{"ip_address":null}""", """{"ip_address":{},"location":{"lat":1,"lng":1}}"""))
            assertEquals("", PostParser.location(JSONObject(raw)))
        assertEquals("江苏", IdentityParser.person(JSONObject("""{"ip_location":"江苏"}""")).location)
    }

    @Test fun userMentionRemainsClickableAndDoesNotMergeWithNeighborText() {
        val parts = api.content(JSONArray("""[{"type":0,"text":"回复 "},
          {"type":4,"text":"吧友甲","uid":"555"},{"type":0,"text":"：内容"}]"""))
        assertEquals(3, parts.size)
        assertEquals("555", parts[1].mention?.id)
        assertEquals("吧友甲", parts[1].mention?.name)
        assertNull(api.content(JSONArray("""[{"type":4,"text":"匿名","uid":0}]""")).single().mention)
        assertEquals("回复 吧友甲：内容", PostItem("1", 0, Person(), parts, 0, 0, 0).copyableText())
    }

    @Test fun copiedMediaHasMeaningfulPlaceholdersAndLinksSurvive() {
        val post = PostItem("1", 2, Person(), listOf(ContentPart(text = "前文"), ContentPart(image = "https://example.org/1.png"),
            ContentPart(isVideo = true), ContentPart(link = "https://tieba.baidu.com/p/1")), 0, 0, 0)
        assertEquals("前文[图片][视频]https://tieba.baidu.com/p/1", post.copyableText())
    }

    @Test fun historyKeepsPublicIpFieldAndOldRecordsStillRead() {
        val thread = ThreadItem("1", "标题", "测试", author = Person("2", "作者", location = "山东"))
        assertEquals(thread, ThreadStorage.decode(ThreadStorage.encode(thread)))
        assertEquals("", ThreadStorage.decode(JSONObject("""{"id":"1","title":"旧记录"}""")).author.location)
    }

    @Test fun galleryKeepsAllServiceSourcesAndLegacyImagesWithoutLosingTheClickedItem() {
        val sources = (1..6).map { ImageSource("https://example.org/$it-small.jpg", original = "https://example.org/$it.jpg") }
        val thread = ThreadItem("1", "六图帖", "测试", images = sources.take(3).map { it.preview }, imageSources = sources)
        assertEquals(sources, thread.galleryImages())
        assertEquals(sources[2], ImageGallery(thread.galleryImages(), 2).sources[2])
        assertEquals(listOf(ImageSource("https://example.org/old.jpg")), thread.copy(images = listOf("https://example.org/old.jpg"), imageSources = emptyList()).galleryImages())
        assertTrue(runCatching { ImageGallery(emptyList()) }.isFailure)
        assertTrue(runCatching { ImageGallery(sources, 6) }.isFailure)
    }

    @Test fun replyProtocolKeepsParentAndSpecificTargetSeparate() {
        val parent = PostItem("100", 3, Person("10"), emptyList(), 0, 0, 0)
        val target = PostItem("101", 0, Person("11"), emptyList(), 0, 0, 0)
        assertEquals(mapOf("quote_id" to "100", "floor_num" to "3", "reply_uid" to "10"), NativeProtocol.replyFields(parent, null))
        assertEquals(mapOf("quote_id" to "100", "floor_num" to "3", "sub_post_id" to "101", "reply_uid" to "11"), NativeProtocol.replyFields(parent, target))
        assertTrue(NativeProtocol.replyFields(null, null).isEmpty())
        assertTrue(runCatching { NativeProtocol.replyFields(null, target) }.isFailure)
        assertTrue(runCatching { NativeProtocol.replyFields(parent, parent) }.isFailure)
    }
}
