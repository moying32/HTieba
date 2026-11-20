package dev.hydrogen.tieba.data

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ForumSignInTest {
    private fun forum(id: Int) = ForumItem("$id", "吧名$id")

    @Test fun parsesBothFollowGroupsWithoutDroppingCommonForums() {
        val data = JSONObject("""{"has_more":"1","forum_list":{"non-gconforum":[{"id":"1","name":"电脑"}],"gconforum":[{"id":"2","name":"游戏"}]},"common_forum_list":{"non-gconforum":[{"id":"1","name":"电脑"},{"id":"3","name":"阅读"}]}}""")
        val result = ForumSignParser.followed(data)
        assertEquals(listOf("电脑", "游戏", "阅读"), result.forums.map { it.name })
        assertTrue(result.hasMore)
    }

    @Test fun emptyAndMalformedFollowResponsesStayDistinct() {
        assertTrue(ForumSignParser.followed(JSONObject("""{"has_more":0,"forum_list":[]}""")).forums.isEmpty())
        listOf("{}", """{"forum_list":[]}""", """{"forum_list":[],"has_more":"unknown"}""",
            """{"forum_list":[{"id":"1"}],"has_more":0}""", """{"forum_list":{"unknown":[]},"has_more":0}""",
            """{"forum_list":["bad"],"has_more":0}""").forEach { raw ->
            assertThrows(IOException::class.java) { ForumSignParser.followed(JSONObject(raw)) }
        }
    }

    @Test fun readsAllPagesAndDeduplicatesBeforeReturningTargets() = runBlocking {
        val pages = mutableListOf<Int>()
        val result = allFollowedForums { page ->
            pages += page
            if (page == 1) FollowedForumPage(listOf(forum(1), forum(2)), true)
            else FollowedForumPage(listOf(forum(2), forum(3)), false)
        }
        assertEquals(listOf(1, 2), pages)
        assertEquals(listOf("1", "2", "3"), result.map { it.id })
    }

    @Test fun repeatingPaginationCannotPretendItReadTheWholeList() {
        assertThrows(IOException::class.java) { runBlocking {
            allFollowedForums { FollowedForumPage(listOf(forum(1)), true) }
        } }
        assertThrows(IOException::class.java) { runBlocking {
            allFollowedForums { page -> if (page == 1) FollowedForumPage(listOf(forum(1)), true) else throw IOException("第二页失败") }
        } }
    }

    @Test fun onlyConfirmedSignResultsCountAsSuccess() {
        assertEquals(ForumSignResult.Signed, ForumSignParser.result(JSONObject("""{"error_code":0,"user_info":{"sign_bonus_point":8}}""")))
        assertEquals(ForumSignResult.AlreadySigned, ForumSignParser.result(JSONObject("""{"error_code":0,"user_info":{"is_sign_in":"1","sign_bonus_point":0}}""")))
        listOf("""{"error_code":0}""", """{"error_code":0,"user_info":{"sign_bonus_point":0}}""").forEach {
            assertThrows(IOException::class.java) { ForumSignParser.result(JSONObject(it)) }
        }
        assertThrows(TiebaRejectedException::class.java) { ForumSignParser.result(JSONObject("""{"error_code":9,"error_msg":"失败","user_info":{"is_sign_in":1}}""")) }
    }

    @Test fun retryAndResumeNeverResendConfirmedOrUnfollowedTargets() {
        val previous = listOf(ForumSignEntry(forum(1), ForumSignStatus.Signed), ForumSignEntry(forum(2), ForumSignStatus.AlreadySigned),
            ForumSignEntry(forum(3), ForumSignStatus.Failed, "失败"), ForumSignEntry(forum(4)), ForumSignEntry(forum(5), ForumSignStatus.Failed))
        val current = (1..4).map(::forum) + forum(6)
        val retry = prepareForumSigns(current, previous, ForumSignMode.Failed)
        assertEquals(listOf("3"), retry.map { it.forum.id })
        assertEquals(ForumSignStatus.Waiting, retry.single().status)
        assertEquals(listOf("4"), prepareForumSigns(current, previous, ForumSignMode.Remaining).map { it.forum.id })
        assertEquals(listOf(ForumSignStatus.Signed, ForumSignStatus.AlreadySigned, ForumSignStatus.Waiting, ForumSignStatus.Waiting, ForumSignStatus.Waiting),
            prepareForumSigns(current, previous, ForumSignMode.All).map { it.status })
    }

    @Test fun serialBatchRetainsFailuresAndNeverAutomaticallyRetries() = runBlocking {
        val attempts = mutableListOf<String>()
        val result = runForumSignBatch((1..3).map { ForumSignEntry(forum(it)) }, { false }, sign = {
            attempts += it.id
            when (it.id) { "1" -> ForumSignResult.Signed; "2" -> throw IOException("网络失败"); else -> ForumSignResult.AlreadySigned }
        }, describe = { it.message.orEmpty() }, onProgress = {}, pause = {})
        assertEquals(listOf("1", "2", "3"), attempts)
        assertEquals(listOf(ForumSignStatus.Signed, ForumSignStatus.Failed, ForumSignStatus.AlreadySigned), result.entries.map { it.status })
        assertEquals("网络失败", result.entries[1].message)
    }

    @Test fun stoppingKeepsCurrentResultAndLeavesRestUnsent() = runBlocking {
        var stop = false
        val attempts = mutableListOf<String>()
        val result = runForumSignBatch((1..3).map { ForumSignEntry(forum(it)) }, { stop }, sign = {
            attempts += it.id; stop = true; ForumSignResult.Signed
        }, describe = { "失败" }, onProgress = {}, pause = {})
        assertTrue(result.stopped)
        assertEquals(listOf("1"), attempts)
        assertEquals(listOf(ForumSignStatus.Signed, ForumSignStatus.Waiting, ForumSignStatus.Waiting), result.entries.map { it.status })
    }

    @Test fun loginAndVerificationFailuresStopTheBatch() = runBlocking {
        listOf(LoginExpiredException(), TiebaRejectedException("need_vcode", "验证"), TiebaRejectedException("9", "操作频繁")).forEach { failure ->
            var attempts = 0
            val result = runForumSignBatch((1..2).map { ForumSignEntry(forum(it)) }, { false }, sign = { attempts++; throw failure },
                describe = { "失败" }, onProgress = {}, pause = {})
            assertEquals(1, attempts)
            assertSame(failure, result.error)
            assertEquals(ForumSignStatus.Waiting, result.entries[1].status)
        }
    }

    @Test fun cancellationIsNeverConvertedIntoASuccessOrRetry() {
        var attempts = 0
        assertThrows(CancellationException::class.java) { runBlocking {
            runForumSignBatch((1..2).map { ForumSignEntry(forum(it)) }, { false }, sign = { attempts++; throw CancellationException("cancel") },
                describe = { "失败" }, onProgress = {}, pause = {})
        } }
        assertEquals(1, attempts)
    }
}
