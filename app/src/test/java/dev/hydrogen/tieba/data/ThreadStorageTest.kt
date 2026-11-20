package dev.hydrogen.tieba.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ThreadStorageTest {
    @Test fun collectionCountKeepsUnknownZeroAndServerValueDistinct() {
        val api = TiebaApi("collection-count-test")
        for ((raw, expected) in listOf("{}" to null, "{\"collect_num\":null}" to null,
            "{\"collect_num\":-1}" to null, "{\"collect_num\":0}" to 0L,
            "{\"collect_num\":\"512\"}" to 512L)) {
            val thread = api.thread(JSONObject(raw).put("id", "123"), "测试")
            assertEquals(expected, thread.collections)
            assertEquals(expected, ThreadStorage.decode(JSONObject(ThreadStorage.encode(thread).toString())).collections)
        }
        assertNull(ThreadStorage.decode(JSONObject("""{"id":"123","title":"旧收藏"}""")).collections)
        // A local bookmark never supplies or increments the Baidu collection count.
        assertNull(api.thread(JSONObject("""{"id":"123","saved":true,"favorite_num":99}"""), "测试").collections)
    }

    @Test fun oldNameOnlyHistoryRemainsReadable() {
        val item = ThreadStorage.decode(JSONObject("""{"id":"123","title":"旧收藏","author":"老吧友","replies":12}"""))
        assertEquals("123", item.id)
        assertEquals("老吧友", item.author.name)
        assertEquals(12, item.replies)
        assertTrue(item.images.isEmpty())
        assertTrue(item.author.badges.isEmpty())
    }

    @Test fun mediaIdentityAndCountsSurviveRestart() {
        val item = ThreadItem("123", "中文与 👨‍👩‍👦 标题", "Minecraft",
            Person("456", "长名字吧友", "https://himg.bdimg.com/sys/portrait/item/token?t=1", 12, "社区称号", "吧主",
                listOf(UserBadge("badge", "https://imgsrc.baidu.com/badge.png", "徽章", setOf(IdentitySurface.Post)))),
            "正文", listOf("https://imgsrc.baidu.com/pic.png"), 31, 8, 12345, true, "789", true)
        assertEquals(item, ThreadStorage.decode(JSONObject(ThreadStorage.encode(item).toString())))
    }

    @Test fun savedLegacyAvatarsAreRepaired() {
        val item = ThreadStorage.decode(JSONObject("""{"id":"1","title":"收藏", "author_details":{"name":"吧友",
          "avatar":"http://tb.himg.baidu.com/sys/portrait/item/token"},"images":["http://imgsrc.baidu.com/pic.png","file:///bad"]}"""))
        assertEquals("https://himg.bdimg.com/sys/portrait/item/token", item.author.avatar)
        assertEquals(listOf("https://imgsrc.baidu.com/pic.png"), item.images)
    }
}
