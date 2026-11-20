package dev.hydrogen.tieba.data

import dev.hydrogen.tieba.ui.GroupBlockListing
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class GroupModerationTest {
    private val room = ChatRoom("12","测试群",forumId = "34")
    private val profile = UserProfile(Person("56","测试账号"),portrait = "tb.1_ABC?t=123")
    private val target = GroupModerationProtocol.target(profile,"56")
    private fun page(p: Int = 1, total: Long = 31, users: String = """[{"name":"旧名","show_nickname":"新昵称","portrait_id":"abc?t=123","left_time_tip":"1小时"}]""") =
        JSONObject("""{"no":0,"data":{"pn":$p,"rn":30,"total":$total,"user_forbidden_list":$users}}""")

    @Test fun listUsesServerNamePortraitAndRemainingTime() {
        val result = GroupModerationProtocol.blocks(page(),1)
        assertEquals("新昵称",result.users.single().name)
        assertEquals("abc",result.users.single().portrait)
        assertEquals("1小时",result.users.single().remaining)
        assertEquals("https://himg.bdimg.com/sys/portrait/item/abc",result.users.single().avatar)
        assertTrue(result.hasMore)
    }
    @Test fun duplicatePortraitsAreOneAccount() {
        val result = GroupModerationProtocol.blocks(page(users = """[{"portrait_id":"abc"},{"portrait_id":"abc?t=2"}]"""),1)
        assertEquals(1,result.users.size)
    }
    @Test fun emptyConfirmedListIsSupported() {
        val result = GroupModerationProtocol.blocks(page(total = 0,users = "[]"),1)
        assertTrue(result.users.isEmpty()); assertFalse(result.hasMore)
    }
    @Test fun largeTotalsDoNotOverflowPageMath() {
        val result = GroupModerationProtocol.blocks(page(Int.MAX_VALUE,Long.MAX_VALUE),Int.MAX_VALUE)
        assertTrue(result.hasMore)
    }
    @Test fun namesAndUnknownTimeStayHonest() {
        val user = GroupModerationProtocol.blocks(page(total = 1,users = """[{"name":"用户名","portrait_id":"abc"}]"""),1).users.single()
        assertEquals("用户名",user.name); assertEquals("",user.remaining)
    }
    @Test(expected = IOException::class) fun permissionFailureIsNotEmptyList() {
        GroupModerationProtocol.blocks(JSONObject("""{"no":403,"error":"没有吧务权限"}"""),1)
    }
    @Test fun errorMessageAndCodeArePreserved() {
        try { GroupModerationProtocol.ensureWebSuccess(JSONObject("""{"no":403,"error":"没有吧务权限"}""")); fail() }
        catch(e: TiebaRejectedException) { assertEquals("403",e.code); assertTrue(e.message!!.contains("没有吧务权限")) }
    }
    @Test(expected = IOException::class) fun missingSuccessIsNotSuccess() {
        GroupModerationProtocol.ensureWebSuccess(JSONObject("""{"data":{}}"""))
    }
    @Test(expected = IOException::class) fun wrongPageIsRejected() { GroupModerationProtocol.blocks(page(2),1) }
    @Test(expected = IOException::class) fun invalidPageSizeIsRejected() { GroupModerationProtocol.blocks(page().apply { getJSONObject("data").put("rn",0) },1) }
    @Test(expected = IOException::class) fun missingListIsNotEmpty() { GroupModerationProtocol.blocks(page().apply { getJSONObject("data").remove("user_forbidden_list") },1) }
    @Test(expected = IOException::class) fun unknownListEntryIsRejected() { GroupModerationProtocol.blocks(page(users = "[null]"),1) }
    @Test(expected = IOException::class) fun missingPortraitIsRejected() { GroupModerationProtocol.blocks(page(users = """[{"name":"相同名字"}]"""),1) }
    @Test(expected = IOException::class) fun emptyNonFinalPageIsRejected() { GroupModerationProtocol.blocks(page(users = "[]"),1) }
    @Test fun targetVerifiedByUidAndExpectedPortrait() {
        assertEquals("tb.1_ABC",GroupModerationProtocol.target(profile,"56","tb.1_ABC").portrait)
    }
    @Test(expected = IllegalArgumentException::class) fun sameNameDifferentUidCannotBeTarget() { GroupModerationProtocol.target(profile,"57") }
    @Test(expected = IllegalArgumentException::class) fun selectedListAccountMustMatchProfile() { GroupModerationProtocol.target(profile,"56","other_account") }
    @Test(expected = IllegalArgumentException::class) fun missingProfilePortraitBlocksWrite() { GroupModerationProtocol.target(profile.copy(portrait = ""),"56") }
    @Test fun portraitDoesNotAcceptUrlOrInjection() {
        listOf("https://himg.bdimg.com/a","abc/def","a b","a#b","a&x=1","").forEach { assertEquals("",GroupModerationProtocol.portrait(it)) }
    }
    @Test fun blockUsesPortraitAndKnownDuration() {
        val fields = GroupModerationProtocol.blockFields(room,target,GroupBlockDuration.HalfDay,"token")
        assertEquals(mapOf("forum_id" to "34","chatroom_id" to "12","forbid_user_id" to "tb.1_ABC","forbid_time" to "43200","tbs" to "token"),fields)
        assertEquals(listOf(3600,43200,86400,604800,1296000,2592000),GroupBlockDuration.entries.map { it.seconds })
    }
    @Test fun unblockUsesNumericUidAndOperationTwo() {
        val fields = GroupModerationProtocol.unblockFields(room,target,"token")
        assertEquals("56",fields["block_uid"]); assertEquals("2",fields["op_type"])
        assertFalse(fields.containsKey("forbid_user_id"))
    }
    @Test(expected = IllegalArgumentException::class) fun invalidForumBlocksWrite() { GroupModerationProtocol.blockFields(room.copy(forumId = ""),target,GroupBlockDuration.Hour,"token") }
    @Test(expected = IllegalArgumentException::class) fun invalidGroupBlocksWrite() { GroupModerationProtocol.unblockFields(room.copy(id = "-1"),target,"token") }
    @Test(expected = IllegalArgumentException::class) fun missingSecurityTokenBlocksWrite() { GroupModerationProtocol.unblockFields(room,target,"") }
    @Test fun paginationMergesOverlapsAndRefreshReplaces() {
        val first = GroupBlockedUser("一","one",""); val second = GroupBlockedUser("二","two","")
        val state = GroupBlockListing().accept(GroupBlockPage(listOf(first),1,61,true),false)
            .accept(GroupBlockPage(listOf(first,second),2,61,true),true)
        assertEquals(listOf(first,second),state.users); assertEquals(2,state.page)
        val reset = state.accept(GroupBlockPage(emptyList(),1,0,false),false)
        assertTrue(reset.users.isEmpty()); assertFalse(reset.hasMore)
    }
    @Test(expected = IOException::class) fun nonAdvancingListPageIsRejected() {
        val user = GroupBlockedUser("一","one","")
        GroupBlockListing(listOf(user),1,90,true).accept(GroupBlockPage(listOf(user),2,90,true),true)
    }
    @Test(expected = IOException::class) fun skippedPageDoesNotOverwriteExistingList() {
        GroupBlockListing().accept(GroupBlockPage(emptyList(),2,0,false),true)
    }
}
