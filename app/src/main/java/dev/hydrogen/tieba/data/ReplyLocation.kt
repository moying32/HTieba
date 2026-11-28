package dev.hydrogen.tieba.data

/** A pid response is a locator, never a replacement for the thread's full page. */
internal object ReplyLocation {
    fun resolveParent(parent: PostItem, located: List<PostItem>): PostItem {
        val exact = located.firstOrNull { it.id == parent.id && it.floor > 0 }
            ?: throw java.io.IOException("暂时无法确认这条回复的原楼层，请稍后重试")
        return parent.copy(floor = exact.floor)
    }
    fun parent(posts: List<PostItem>, pid: String): PostItem? = posts.firstOrNull { it.id == pid }
        ?: posts.firstOrNull { p -> p.previewReplies.any { it.id == pid } }

    fun matchingPage(first: ThreadPage, suggested: ThreadPage, pid: String): ThreadPage =
        if (pid.isNotBlank() && parent(suggested.posts, pid) == null && parent(first.posts, pid) != null) first
        else suggested

    // A subreply lookup can return only the surrounding floor. Use candidates from that
    // response, but require the floor endpoint to confirm the exact reply ID.
    suspend fun confirmNested(locator: ThreadPage, page: ThreadPage, pid: String,
                              load: suspend (String, Int) -> FloorPage): Pair<PostItem, FloorPage>? {
        if (locator.thread.id != page.thread.id) return null
        val shown = page.posts.associateBy { it.id }
        val candidates = locator.posts.mapNotNull { located -> shown[located.id] }
            .filter { it.subReplies > 0 && it.id != pid }
            .take(3)
        for (parent in candidates) {
            val collected = linkedMapOf<String, PostItem>()
            for (number in 1..3) {
                val result = load(parent.id, number)
                result.posts.forEach { collected.putIfAbsent(it.id, it) }
                if (pid in collected) return parent to result.copy(posts = collected.values.toList())
                if (!result.hasMore || result.posts.isEmpty()) break
            }
        }
        return null
    }

    fun page(locator: ThreadPage, pid: String): Int {
        val floor = parent(locator.posts, pid)?.floor ?: locator.posts.firstOrNull { it.floor > 1 }?.floor ?: 0
        return if (locator.page > 1) locator.page else if (floor > 0) (floor - 1) / 20 + 1 else 1
    }

    fun withRoot(first: ThreadPage, target: ThreadPage): ThreadPage = target.copy(thread = first.thread,
        posts = (first.posts.filter { it.floor == 1 } + target.posts).distinctBy { it.id })
}
