package dev.hydrogen.tieba.data

/** Login completion requires a server-confirmed session, never a URL or cookie alone. */
internal object LoginCompletion {
    fun isLoginUrl(url: String): Boolean = runCatching {
        val uri = java.net.URI(url)
        uri.scheme == "https" && uri.host in setOf("wappass.baidu.com", "passport.baidu.com") &&
            uri.path.orEmpty().startsWith("/passport")
    }.getOrDefault(false)

    fun canComplete(candidate: String, current: String, loggedIn: Boolean, uid: String): Boolean =
        candidate.isNotBlank() && candidate == current && loggedIn && uid.toLongOrNull()?.let { it > 0 } == true
}
