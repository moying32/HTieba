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
            when (it.id) { "1" -> ForumSignResult.Signed; "2" -> throw TiebaRejectedException("9", "测试拒绝"); else -> ForumSignResult.AlreadySigned }
        }, onProgress = {}, pause = {})
        assertEquals(listOf("1", "2", "3"), attempts)
        assertEquals(listOf(ForumSignStatus.Signed, ForumSignStatus.Failed, ForumSignStatus.AlreadySigned), result.entries.map { it.status })
        assertTrue(result.entries[1].message.contains("贴吧错误码 9"))
    }

    @Test fun stoppingKeepsCurrentResultAndLeavesRestUnsent() = runBlocking {
        var stop = false
        val attempts = mutableListOf<String>()
        val result = runForumSignBatch((1..3).map { ForumSignEntry(forum(it)) }, { stop }, sign = {
            attempts += it.id; stop = true; ForumSignResult.Signed
        }, onProgress = {}, pause = {})
        assertTrue(result.stopped)
        assertEquals(listOf("1"), attempts)
        assertEquals(listOf(ForumSignStatus.Signed, ForumSignStatus.Waiting, ForumSignStatus.Waiting), result.entries.map { it.status })
    }

    @Test fun loginAndVerificationFailuresStopTheBatch() = runBlocking {
        listOf(LoginExpiredException(), TiebaRejectedException("need_vcode", "验证"), TiebaRejectedException("9", "操作频繁")).forEach { failure ->
            var attempts = 0
            val result = runForumSignBatch((1..2).map { ForumSignEntry(forum(it)) }, { false }, sign = { attempts++; throw failure },
                onProgress = {}, pause = {})
            assertEquals(1, attempts)
            if (failure is LoginExpiredException) assertSame(failure, result.error)
            else assertTrue(result.error?.message.orEmpty().contains("单吧签到"))
            assertEquals(ForumSignStatus.Waiting, result.entries[1].status)
        }
    }

    @Test fun cancellationIsNeverConvertedIntoASuccessOrRetry() {
        var attempts = 0
        assertThrows(CancellationException::class.java) { runBlocking {
            runForumSignBatch((1..2).map { ForumSignEntry(forum(it)) }, { false }, sign = { attempts++; throw CancellationException("cancel") },
                onProgress = {}, pause = {})
        } }
        assertEquals(1, attempts)
    }

    @Test fun statusParsingDoesNotTreatMissingOrUnrecognizedValuesAsUnsigned() {
        val listed = ForumSignParser.followed(JSONObject("""{
            "has_more":0,"forum_list":[{"id":"1","name":"甲","is_sign":1},
            {"id":"2","name":"乙","is_sign":"0"},{"id":"3","name":"丙"},{"id":"4","name":"丁","is_sign":"unknown"}]
        }""")).forums
        assertEquals(listOf(true, false, null, null), listed.map { it.signedToday })
        val guide = ForumSignParser.guideStatuses(JSONObject("""{"like_forum":[
            {"forum_id":3,"is_sign":true},{"forum_id":4,"is_sign":false},{"forum_id":5,"is_sign":null}]}"""))
        assertEquals(mapOf("3" to true, "4" to false), guide)
        val previouslyFailed = listOf(ForumSignEntry(listed.first(), ForumSignStatus.Failed))
        assertEquals(ForumSignStatus.AlreadySigned, prepareForumSigns(listed, previouslyFailed, ForumSignMode.Failed).single().status)
    }

    @Test fun onlyExplicitlyUnsignedForumsAreRetriedAfterPartialBatchSuccess() = runBlocking {
        val forums = (1..4).map { forum(it).copy(signedToday = it == 1) }
        val calls = mutableListOf<String>()
        val result = runOfficialSign(prepareForumSigns(forums, emptyList(), ForumSignMode.All),
            sign = { calls += "batch" }, readBack = {
                calls += "readback"
                ForumSignReadback(mergeForumSignStatuses(forums, mapOf("2" to true, "3" to false), verifyUnsigned = true))
            })
        assertNull(result.error)
        val final = runForumSignBatch(requireKnownUnsigned(result.entries), { false }, sign = {
            calls += it.id
            ForumSignResult.Signed
        }, onProgress = {}, pause = {})
        assertEquals(listOf("batch", "readback", "3"), calls)
        assertEquals(listOf(ForumSignStatus.AlreadySigned, ForumSignStatus.Signed, ForumSignStatus.Signed, ForumSignStatus.Failed),
            final.entries.map { it.status })
    }

    @Test fun closedBatchWindowAllowsConfirmedFallbackButTimeoutDoesNot() = runBlocking {
        val unsigned = forum(1).copy(signedToday = false)
        val confirmed = forum(2).copy(signedToday = true)
        val entries = listOf(ForumSignEntry(unsigned), ForumSignEntry(confirmed.copy(signedToday = false)))
        val unavailable = runOfficialSign(entries, { throw TiebaRejectedException("7", "当前时间不支持批量签到") }, { ForumSignReadback(listOf(unsigned, confirmed)) })
        assertNull(unavailable.error)
        assertEquals(listOf(ForumSignStatus.Waiting, ForumSignStatus.AlreadySigned), unavailable.entries.map { it.status })
        val timeout = runOfficialSign(entries, { throw java.net.SocketTimeoutException() }, { ForumSignReadback(listOf(unsigned, confirmed)) })
        assertTrue(timeout.error?.message.orEmpty().contains("请求超时"))
        assertEquals(ForumSignStatus.AlreadySigned, timeout.entries.last().status)
    }

    @Test fun failedReadbackAndSecurityChallengeNeverPermitFallback() = runBlocking {
        val entries = listOf(ForumSignEntry(forum(1).copy(signedToday = false)))
        val noReadback = runOfficialSign(entries, {}, { throw IOException("网络不可用") })
        assertNotNull(noReadback.error)
        val challenge = TiebaRejectedException("need_vcode", "需要安全验证")
        val blocked = runOfficialSign(entries, { throw challenge }, { error("验证后不再访问接口") })
        assertTrue(blocked.error?.message.orEmpty().contains("need_vcode"))
    }

    @Test fun genericRejectionKeepsConfirmedResultsAndShowsOnlySafeCode() = runBlocking {
        val forums = (1..2).map { forum(it).copy(signedToday = false) }
        val result = runOfficialSign(forums.map { ForumSignEntry(it) }, {
            throw TiebaRejectedException("19", "服务端返回的额外内容")
        }, { ForumSignReadback(listOf(forums.first().copy(signedToday = true), forums.last())) })
        assertEquals(listOf(ForumSignStatus.AlreadySigned, ForumSignStatus.Waiting), result.entries.map { it.status })
        assertEquals("批量签到：贴吧拒绝了请求（贴吧错误码 19）", result.error?.message)
    }

    @Test fun readbackFailureKeepsConfirmedEntriesAndStopsFallback() = runBlocking {
        val forums = (1..2).map { forum(it).copy(signedToday = false) }
        val paged = listOf(forums.first().copy(signedToday = true), forums.last())
        val result = runOfficialSign(forums.map { ForumSignEntry(it) }, {}, {
            ForumSignReadback(mergeForumSignStatuses(paged, emptyMap(), verifyUnsigned = true), IOException("核对签到状态：请求过于频繁"))
        })
        assertEquals(ForumSignStatus.Signed, result.entries.first().status)
        assertNull(result.entries.last().forum.signedToday)
        assertTrue(result.error?.message.orEmpty().contains("频繁"))
    }

    @Test fun resumeUsesFreshStatusesAndSkipsAnythingUnconfirmed() = runBlocking {
        val staleForums = (1..4).map { forum(it).copy(signedToday = false) }
        val current = mergeForumSignStatuses(staleForums, mapOf("1" to true, "2" to false, "4" to false), verifyUnsigned = true)
        val previous = staleForums.map { ForumSignEntry(it) }.toMutableList().apply {
            this[3] = this[3].copy(status = ForumSignStatus.Signed)
        }
        val eligible = requireKnownUnsigned(prepareForumSigns(current, previous, ForumSignMode.Remaining))
        val attempts = mutableListOf<String>()
        val result = runForumSignBatch(eligible, { false }, sign = {
            attempts += it.id
            ForumSignResult.Signed
        }, onProgress = {}, pause = {})
        assertEquals(listOf("2"), attempts)
        assertEquals(listOf(ForumSignStatus.AlreadySigned, ForumSignStatus.Signed, ForumSignStatus.Failed), result.entries.map { it.status })
    }

    @Test fun ambiguousSingleWritePausesRemainingQueue() = runBlocking {
        val attempts = mutableListOf<String>()
        val entries = (1..3).map { ForumSignEntry(forum(it).copy(signedToday = false)) }
        val result = runForumSignBatch(entries, { false }, sign = {
            attempts += it.id
            if (it.id == "2") throw java.net.SocketTimeoutException()
            ForumSignResult.Signed
        }, onProgress = {}, pause = {})
        assertEquals(listOf("1", "2"), attempts)
        assertEquals(listOf(ForumSignStatus.Signed, ForumSignStatus.Failed, ForumSignStatus.Waiting), result.entries.map { it.status })
        assertTrue(result.error?.message.orEmpty().contains("请求超时"))
    }

    @Test fun httpRateLimitDoesNotTriggerAnotherRequest() = runBlocking {
        val result = runOfficialSign(listOf(ForumSignEntry(forum(1))), { throw TiebaHttpException(429) }, {
            error("Unexpected read after rate limit")
        })
        assertTrue(result.error?.message.orEmpty().contains("HTTP 429"))
    }
}
