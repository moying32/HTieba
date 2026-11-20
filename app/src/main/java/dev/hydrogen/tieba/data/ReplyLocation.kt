package dev.hydrogen.tieba.data

/** A pid response is a locator, never a replacement for the thread's full page. */
internal object ReplyLocation {
    fun parent(posts: List<PostItem>, pid: String): PostItem? = posts.firstOrNull { it.id == pid }
        ?: posts.firstOrNull { p -> p.previewReplies.any { it.id == pid } }

    fun page(locator: ThreadPage, pid: String): Int {
        val floor = parent(locator.posts, pid)?.floor ?: locator.posts.firstOrNull { it.floor > 1 }?.floor ?: 0
        return if (locator.page > 1) locator.page else if (floor > 0) (floor - 1) / 20 + 1 else 1
    }

    fun withRoot(first: ThreadPage, target: ThreadPage): ThreadPage = target.copy(thread = first.thread,
        posts = (first.posts.filter { it.floor == 1 } + target.posts).distinctBy { it.id })
}
