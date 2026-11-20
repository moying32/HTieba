package dev.hydrogen.tieba.data

import android.webkit.CookieManager

object BaiduSession {
    private const val TIEBA_URL = "https://tieba.baidu.com/"

    fun cookies(): String = CookieManager.getInstance().getCookie(TIEBA_URL).orEmpty()

    fun cookie(name: String): String = cookies().split(';')
        .asSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("$name=") }
        ?.substringAfter('=')
        .orEmpty()

    fun hasLoginCookie(): Boolean = cookie("BDUSS").isNotBlank()

    fun clear(onComplete: () -> Unit = {}) {
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
            onComplete()
        }
    }
}
