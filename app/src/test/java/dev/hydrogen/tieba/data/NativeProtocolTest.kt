package dev.hydrogen.tieba.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class NativeProtocolTest {
    @Test fun agreeUsesOfficialObjectTypeAndOmitsPlaceholderPostId() {
        val thread = NativeProtocol.agreeFields("11187491253", "", false, false, "135")
        assertEquals("3", thread["obj_type"])
        assertFalse(thread.containsKey("content_type"))
        assertFalse(thread.containsKey("post_id"))
        assertEquals("135", thread["forum_id"])
        assertEquals("1", NativeProtocol.agreeFields("1", "2", true, false, "")["op_type"])
        assertEquals("1", NativeProtocol.agreeFields("1", "2", false, false, "")["obj_type"])
        assertEquals("2", NativeProtocol.agreeFields("1", "2", false, true, "")["obj_type"])
    }
    @Test fun galaxyIdentityMatchesIndependentProtocolVector() {
        assertEquals("06C7F37D41256F25FABA97B885DB6EFB|VAPUDW7TA", ClientIdentity.fromSeed("6723280942424242"))
        val id = ClientIdentity.cuid("01234567-89ab-cdef-0123-456789abcdef")
        assertTrue(id.matches(Regex("[A-F0-9]{32}\\|V[A-Z2-7]{8}")))
        assertEquals(id, ClientIdentity.cuid("01234567-89ab-cdef-0123-456789abcdef"))
        assertNotEquals(id, ClientIdentity.cuid("11234567-89ab-cdef-0123-456789abcdef"))
    }
    @Test fun signatureUsesUnescapedUtf8BeforeFormEncoding() {
        assertEquals("6F32A221E6F06FB7528BE78F4E8D2E82", NativeProtocol.sign(linkedMapOf(
            "tid" to "123", "content" to "中文 & = +\n", "_client_type" to "2")))
    }

    @Test fun antiTokenTakesPrecedenceOverUnrelatedRootToken() {
        assertEquals("anti-token", NativeProtocol.tbs(JSONObject("""{"tbs":"root-token","anti":{"tbs":"anti-token"}}""")))
        assertEquals("", NativeProtocol.tbs(JSONObject("""{"tbs":null}""")))
    }

    @Test fun acceptsBothLegacyAndNestedPostIdentifiers() {
        for (response in listOf("""{"pid":"154061074535"}""", """{"post":{"id":154061074535}}""",
            """{"data":{"post":{"id":"154061074535"}}}""")) {
            assertEquals("154061074535", NativeProtocol.commentId(JSONObject(response)))
        }
        for (response in listOf("{}", """{"pid":0}""", """{"pid":"null"}""", """{"error_code":0}"""))
            assertEquals("", NativeProtocol.commentId(JSONObject(response)))
    }

    @Test fun unknownErrorsRetainTheirCodeAndNeverCountAsSuccess() {
        val error = runCatching { NativeProtocol.ensureSuccess(JSONObject("""{"error_code":3,"error_msg":"未知错误"}""")) }.exceptionOrNull()
        assertTrue(error is TiebaRejectedException)
        assertTrue(error!!.message!!.contains("3"))
        assertTrue(error.message!!.contains("未知错误"))
        assertTrue(runCatching { NativeProtocol.ensureSuccess(JSONObject()) }.exceptionOrNull() is IOException)
        assertTrue(runCatching { NativeProtocol.ensureSuccess(JSONObject("""{"error_code":0,"error":{"errno":123,"errmsg":"拒绝"}}""")) }.exceptionOrNull() is TiebaRejectedException)
        NativeProtocol.ensureSuccess(JSONObject("""{"error_code":0}"""))
    }
}
