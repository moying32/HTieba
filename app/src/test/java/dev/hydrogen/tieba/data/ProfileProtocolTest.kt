package dev.hydrogen.tieba.data

import dev.hydrogen.tieba.data.ChatProto.b
import dev.hydrogen.tieba.data.ChatProto.n
import dev.hydrogen.tieba.data.ChatProto.s
import dev.hydrogen.tieba.data.ChatProto.bytes
import dev.hydrogen.tieba.data.ChatProto.number
import org.junit.Assert.*
import org.junit.Test

class ProfileProtocolTest {
    private fun response(id: Long) = ChatProto.encode(b(2, ChatProto.encode(b(1, ChatProto.encode(
        n(2, id), s(3, "用户名"), s(4, "昵称"), s(5, "tb.1.target"), n(30, 5), s(34, "简介"))))))

    @Test fun profileReadsMappedFieldsAndLeavesMissingCountsUnknown() {
        val result = ProfileProtocol.profile(response(42), "42")
        assertEquals("42", result.person.id)
        assertEquals("昵称", result.person.name)
        assertEquals("tb.1.target", result.portrait)
        assertEquals(5L, result.fans)
        assertNull(result.threads)
        assertEquals("简介", result.introduction)
    }

    @Test fun targetMismatchAndServerErrorNeverEnableManagement() {
        assertTrue(runCatching { ProfileProtocol.profile(response(43), "42") }.isFailure)
        assertTrue(runCatching { ProfileProtocol.profile(byteArrayOf(), "42") }.isFailure)
        val rejected = ChatProto.encode(b(1, ChatProto.encode(n(1, 220012), s(2, "暂不可用"))))
        val error = runCatching { ProfileProtocol.profile(rejected, "42") }.exceptionOrNull()
        assertEquals("220012", (error as TiebaRejectedException).code)
    }

    @Test fun typedRequestKeepsFullWidthUidAndCommonVersion() {
        val uid = "9007199254740993"
        val data = ChatProto.parse(ChatProto.parse(ProfileProtocol.request(uid, "test-only")).bytes(1))
        assertEquals(uid.toLong(), data.number(1))
        assertEquals(2L, ChatProto.parse(data.bytes(9)).number(1))
        listOf("0", "-1", "bad").forEach { assertTrue(runCatching { ProfileProtocol.request(it, "") }.isFailure) }
    }
}
