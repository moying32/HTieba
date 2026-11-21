package dev.hydrogen.tieba.data

import org.junit.Assert.*
import org.junit.Test

class ContentFiltersTest {
    private val thread = ThreadItem("10", "标题", "DeepSeek", author = Person("20", "同名用户"))

    @Test fun filteringUsesStableAuthorIdentifiersAndExactForums() {
        val rules = ContentFilters(users = mapOf("20" to "旧昵称"), forums = setOf("minecraft"), threads = setOf("30"))
        assertTrue(rules.hides(thread))
        assertFalse(rules.hides(thread.copy(author = Person("21", "旧昵称"))))
        assertFalse(rules.hides(thread.copy(author = Person("", "旧昵称"))))
        assertTrue(rules.hides(thread.copy(author = Person(), forum = " Minecraft ")))
        assertFalse(rules.hides(thread.copy(author = Person(), forum = "Minecraft交流")))
        assertTrue(rules.hides(thread.copy(id = "30", author = Person())))
    }

    @Test fun preferencesRoundTripChineseNamesAndSpecialCharacters() {
        val rules = ContentFilters(mapOf("20" to "吧友\"甲\n乙"), setOf("中文吧名", "DeepSeek"), setOf("10", "30"))
        assertEquals(rules, ContentFilters.decode(rules.encode()))
        assertEquals(ContentFilters(), ContentFilters.decode("{}"))
        assertEquals(ContentFilters(), ContentFilters.decode("not-json"))
        assertTrue(ContentFilters.decode("""{"forums":[null,"",3],"threads":[{},null]}""").forums.isEmpty())
    }

    @Test fun removingRulesRestoresItemsWithoutChangingOriginalFeedOrOrder() {
        val original = listOf(thread, thread.copy(id = "11", author = Person("21")))
        val rules = ContentFilters(threads = setOf("10"))
        assertEquals(listOf("11"), original.filterNot(rules::hides).map { it.id })
        assertEquals(listOf("10", "11"), original.filterNot(rules.copy(threads = emptySet())::hides).map { it.id })
    }
}
