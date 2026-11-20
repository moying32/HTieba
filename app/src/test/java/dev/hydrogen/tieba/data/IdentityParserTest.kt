package dev.hydrogen.tieba.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class IdentityParserTest {
    @Test fun portraitsKeepCacheVersionAndUseWorkingTlsHost() {
        assertEquals("https://himg.bdimg.com/sys/portrait/item/tb.1.token?t=1794364995",
            IdentityParser.portraitUrl("tb.1.token?t=1794364995"))
        assertEquals("https://himg.bdimg.com/sys/portrait/item/token?t=1",
            IdentityParser.portraitUrl("http://tb.himg.baidu.com/sys/portrait/item/token?t=1"))
        assertEquals("https://imgsrc.baidu.com/a.png?q=1%202", IdentityParser.imageUrl("//imgsrc.baidu.com/a.png?q=1%202"))
    }

    @Test fun rejectsInvalidUrlsWithoutInventingAvatars() {
        for (value in listOf("", "null", "javascript:alert(1)", "file:///sdcard/a", "../bad", "https://user:secret@example.com/a")) {
            assertEquals(value, "", IdentityParser.portraitUrl(value))
        }
        assertEquals(Person(), IdentityParser.person(null))
    }

    @Test fun readsPublicPbMetadataAndOriginalBadgeArt() {
        val person = IdentityParser.person(JSONObject("""{
          "id":"123", "name_show":"测试吧友", "portrait":"tb.1.token?t=1",
          "level_id":14, "level_name":"", "is_bawu":1,"bawu_type":"assist",
          "iconinfo":[{"name":"gold_shizi","value":1,
            "icon":"https://imgsrc.baidu.com/forum/pic/item/4bed2e738bd4b31ce82c003485d6277f9e2ff829.png",
            "terminal":{"pc":1,"wap":1,"client":1}, "position":{"frs":1,"pb":1,"home":1,"card":1}}]
        }"""))
        assertEquals(14, person.level)
        assertEquals("小吧主", person.role)
        assertEquals("测试吧友", person.name)
        assertEquals(1, person.badges.size)
        assertTrue(person.badges.single().image.endsWith("ff829.png"))
        assertEquals(IdentitySurface.entries.toSet(), person.badges.single().surfaces)
    }

    @Test fun absentLevelsAndInactiveRolesStayAbsent() {
        val person = IdentityParser.person(JSONObject("""{"uid":"456","name":"吧友","level_name":null,"is_bawu":0,"bawu_type":"manager"}"""))
        assertEquals("456", person.id)
        assertEquals(0, person.level)
        assertEquals("", person.levelName)
        assertEquals("", person.role)
        assertTrue(person.badges.isEmpty())
    }

    @Test fun respectsClientVisibilityAndSurfaceFlagsAndBadImages() {
        val person = IdentityParser.person(JSONObject("""{"iconinfo":[
          {"name":"web-only","icon":"https://imgsrc.baidu.com/web.png","terminal":{"pc":1,"client":0}},
          {"name":"post-only","title":"接口称号","icon":"http://tb1.bdstatic.com/post.png","position":{"pb":1,"frs":0,"home":0}},
          {"name":"invalid","icon":"file:///bad.png"},
          {"name":"hidden","icon":"https://imgsrc.baidu.com/hidden.png","position":{"card":1}},
          {"name":"post-only","icon":"http://tb1.bdstatic.com/post.png","position":{"pb":1}}
        ]}"""))
        assertEquals(1, person.badges.size)
        assertEquals(setOf(IdentitySurface.Post), person.badges.single().surfaces)
        assertEquals("接口称号", person.badges.single().label)
        assertEquals("https://tb1.bdstatic.com/post.png", person.badges.single().image)
    }
}
