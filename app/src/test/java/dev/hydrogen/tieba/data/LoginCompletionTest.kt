package dev.hydrogen.tieba.data

import org.junit.Assert.*
import org.junit.Test

class LoginCompletionTest {
    @Test fun onlyOfficialLoginPagesAreEligibleForAutomaticClose() {
        assertTrue(LoginCompletion.isLoginUrl("https://wappass.baidu.com/passport/?login&tpl=tb"))
        listOf("https://tieba.baidu.com/p/123", "https://tieba.baidu.com/im/pcmsg/",
            "https://wappass.baidu.com.evil.test/passport/", "http://wappass.baidu.com/passport/",
            "https://evil@wappass.baidu.com.evil.test/passport/", "bad").forEach {
            assertFalse(it, LoginCompletion.isLoginUrl(it))
        }
    }

    @Test fun cookiesAloneOrLateAccountResponseCannotCloseLogin() {
        assertFalse(LoginCompletion.canComplete("", "", true, "42"))
        assertFalse(LoginCompletion.canComplete("candidate", "changed", true, "42"))
        assertFalse(LoginCompletion.canComplete("candidate", "candidate", false, "42"))
        assertFalse(LoginCompletion.canComplete("candidate", "candidate", true, "0"))
        assertTrue(LoginCompletion.canComplete("candidate", "candidate", true, "42"))
    }
}
