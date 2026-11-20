package dev.hydrogen.tieba.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MediaParserTest {
    @Test fun highPreviewReadsOriginalWhileKeepingItsOwnDecodeLimit() {
        val source = ImageSource("https://imgsrc.baidu.com/tiny.jpg", high="https://imgsrc.baidu.com/960.jpg",
            original="https://tiebapic.baidu.com/forum/pic/item/original.jpg?tbpicau=original%2Bsig")
        assertEquals(source.original, source.previewUrl(ImageQuality.High))
        assertEquals(source.original, source.previewUrl(ImageQuality.Original))
        assertEquals(source.high, source.previewUrl(ImageQuality.Low))
        assertTrue(ImageQuality.High.edge < ImageQuality.Original.edge)
        assertFalse(source.needsPreviewDetails(ImageQuality.High))
    }

    @Test fun thumbnailOnlySourceTriggersDetailResolutionForEveryPreviewQuality() {
        val thumb = ImageSource("https://imgsrc.baidu.com/tiny.jpg")
        assertTrue(thumb.needsPreviewDetails(ImageQuality.Medium))
        assertTrue(thumb.needsPreviewDetails(ImageQuality.High))
        assertTrue(thumb.needsPreviewDetails(ImageQuality.Original))
        assertTrue(thumb.needsPreviewDetails(ImageQuality.Low))
        assertFalse(thumb.copy(medium="https://imgsrc.baidu.com/960.jpg").needsPreviewDetails(ImageQuality.Medium))
        val originalOnly = ImageSource("https://tiebapic.baidu.com/forum/pic/item/image.jpg",
            original="https://tiebapic.baidu.com/forum/pic/item/image.jpg")
        assertFalse(originalOnly.needsPreviewDetails(ImageQuality.Original))
    }

    @Test fun legacyCroppedMediumIsNeverUsedAsCompleteImage() {
        val filename = "d3fa04763912b31bb2ab8d21c018367adbb4e168.jpg"
        val cropped = "https://imgsrc.baidu.com/forum/whfpf%3D160%2C160/sign=thumb/$filename"
        val full = "https://tiebapic.baidu.com/forum/pic/item/$filename?tbpicau=original%2Bsig"
        val source = ImageSource(cropped, medium=cropped, original=full)
        ImageQuality.entries.forEach { assertEquals(full, source.previewUrl(it)) }
        assertFalse(source.needsPreviewDetails(ImageQuality.Low))
        assertTrue(source.copy(original="").needsPreviewDetails(ImageQuality.Medium))
    }

    @Test fun thumbnailOnlyGalleryGetsMatchingSignedOriginalWithoutChangingOrder() {
        val a = "0d0c462762d0f7035a5bd1db4efa513d2797c5e8.jpg"
        val b = "af53efa6d933c89509ae2d73971373f08302005b.jpg"
        val first = MediaParser.image(JSONObject().put("big_pic", "https://imgsrc.baidu.com/forum/whfpf%3D160%2C160/sign=small/$a?tbpicau=small%2Fsig"), true)
        val second = ImageSource("https://imgsrc.baidu.com/forum/w%3D320/sign=small/$b")
        val aFull = ImageSource("https://tiebapic.baidu.com/forum/w%3D720/sign=new/$a",
            original="https://tiebapic.baidu.com/forum/pic/item/$a?tbpicau=origin%2Bsig")
        val bFull = ImageSource("https://tiebapic.baidu.com/forum/w%3D720/sign=new/$b",
            original="https://tiebapic.baidu.com/forum/pic/item/$b?tbpicau=b-original")
        val detail = ThreadPage(ThreadItem("123", "图片", "测试"), listOf(
            PostItem("1", 1, Person(), listOf(ContentPart(imageSource=bFull), ContentPart(imageSource=aFull)), 0, 0, 0)), 1, false)
        val gallery = ImageGallery(listOf(first, second), 1, "123").withDetailImages(detail)
        assertEquals(1, gallery.initialIndex)
        assertEquals(listOf(aFull.original, bFull.original), gallery.sources.map { it.original })
        assertEquals(first.preview, gallery.sources.first().preview)
        assertFalse(gallery.sources.first().needsPreviewDetails(ImageQuality.High))
    }

    @Test fun galleryNeverUsesReplyImagesOrUnknownHostFilenameCollisions() {
        val gallery = ImageGallery(listOf(ImageSource("https://example.org/photo.jpg")), threadId="123")
        val unrelated = ImageSource("https://another.example/photo.jpg", original="https://another.example/original.jpg")
        val reply = ImageSource(gallery.sources.single().preview, original="https://example.org/reply.jpg")
        val detail = ThreadPage(ThreadItem("123", "图片", "测试"), listOf(
            PostItem("1",1,Person(),listOf(ContentPart(imageSource=unrelated)),0,0,0),
            PostItem("2",2,Person(),listOf(ContentPart(imageSource=reply)),0,0,0)),1,false)
        assertEquals(gallery, gallery.withDetailImages(detail))
        assertTrue(gallery.withDetailImages(detail).sources.single().needsPreviewDetails(ImageQuality.High))
    }

    @Test(expected = IllegalArgumentException::class)
    fun galleryRejectsDifferentThreadDetails() {
        ImageGallery(listOf(ImageSource("https://example.org/a.jpg")), threadId="123")
            .withDetailImages(ThreadPage(ThreadItem("456", "其他帖", "测试"), emptyList(),1,false))
    }

    @Test fun feedPreviewNeverReusesAThumbnailSignatureForOriginal() {
        val source = MediaParser.image(JSONObject("""{
          "small_pic":"https://imgsrc.baidu.com/small.jpg?tbpicau=small%2Fsig&x=1",
          "big_pic":"https://imgsrc.baidu.com/big.jpg?tbpicau=medium",
          "src_pic":"https://imgsrc.baidu.com/large.jpg?tbpicau=high",
          "origin_pic":"https://imgsrc.baidu.com/original.jpg?tbpicau=original%2Bsig"
        }"""), true)
        assertEquals("https://imgsrc.baidu.com/small.jpg?tbpicau=small%2Fsig&x=1", source.url(ImageQuality.Low))
        assertTrue(source.url(ImageQuality.Medium).endsWith("tbpicau=high"))
        assertTrue(source.url(ImageQuality.High).endsWith("tbpicau=high"))
        assertEquals("https://imgsrc.baidu.com/original.jpg?tbpicau=original%2Bsig", source.url(ImageQuality.Original))
    }

    @Test fun detailSourcesAndFallbackArePreservedAcrossStorage() {
        val source = MediaParser.image(JSONObject("""{"cdn_src":"https://imgsrc.baidu.com/720.jpg",
          "big_cdn_src":"https://imgsrc.baidu.com/960.jpg","origin_src":"https://imgsrc.baidu.com/origin.jpg?sig=abc%2Bdef"}"""), false)
        val item = ThreadItem("1", "图片帖", "测试", images=listOf(source.preview), imageSources=listOf(source))
        assertEquals(item, ThreadStorage.decode(JSONObject(ThreadStorage.encode(item).toString())))
        assertEquals(source.original, source.url(ImageQuality.Original))
        val legacy = ImageSource("https://imgsrc.baidu.com/old.jpg")
        ImageQuality.entries.forEach { assertEquals(legacy.preview, legacy.url(it)) }
        assertTrue(ThreadStorage.decode(JSONObject("""{"id":"1","title":"旧收藏"}""")).imageSources.isEmpty())
    }

    @Test fun emptyOrUnsafeImageCandidatesFallThroughToActualServiceUrls() {
        val source=MediaParser.image(JSONObject("""{"small_pic":"file:///private","big_pic":null,
          "origin_pic":"https://imgsrc.baidu.com/actual.jpg?sig=origin"}"""),true)
        assertEquals(source.original,source.url(ImageQuality.Low))
        assertEquals("https://imgsrc.baidu.com/actual.jpg?sig=origin",source.original)
    }

    @Test fun videoPrefersOriginalAndRejectsPostPagesAndLocalUris() {
        assertEquals("https://tb-video.bdstatic.com/original.mp4?sig=x",MediaParser.video(JSONObject("""{
          "origin_video_url":"https://tb-video.bdstatic.com/original.mp4?sig=x",
          "video_url":"https://tb-video.bdstatic.com/low.mp4"}""")))
        assertEquals("https://tb-video.bdstatic.com/video.mp4",MediaParser.video(JSONObject("""{
          "video_url":"https://tieba.baidu.com/p/1","src":"https://tb-video.bdstatic.com/video.mp4"}""")))
        listOf("javascript:alert(1)","file:///video.mp4","https://tieba.baidu.com/p/123").forEach { assertEquals("",MediaParser.playable(it)) }
    }

    @Test fun videoWithoutUrlDoesNotGetMergedIntoSurroundingText() {
        val parts=TiebaApi("test").content(JSONArray("""[{"type":0,"text":"前文"},{"type":5},
          {"type":0,"text":"后文"}]"""))
        assertEquals(3,parts.size)
        assertTrue(parts[1].isVideo)
        assertEquals("后文",parts[2].text)
    }

    @Test fun realDetailVideoUsesLinkWhileSrcRemainsItsCover() {
        val parts=TiebaApi("test").content(JSONArray("""[{"type":5,
          "src":"https://tiebapic.baidu.com/forum/cover.jpg?tbpicau=cover",
          "link":"http://tb-video.bdstatic.com/tieba-smallvideo/video.mp4?tbau=video"}]"""))
        assertEquals("https://tb-video.bdstatic.com/tieba-smallvideo/video.mp4?tbau=video",parts.single().video)
        assertEquals("https://tiebapic.baidu.com/forum/cover.jpg?tbpicau=cover",parts.single().videoCover)
        assertEquals("",MediaParser.video(JSONObject("""{"src":"https://tiebapic.baidu.com/cover.jpg"}""")))
    }
}
