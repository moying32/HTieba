package dev.hydrogen.tieba.ui

internal enum class PageAction { Open, Back, GestureBack, Tab }

/** The actual navigation operation, independent of the kind of either page. */
internal data class PageNavigation(
    val sequence: Long = 0,
    val from: String = "",
    val to: String = "",
    val action: PageAction = PageAction.Open,
) {
    fun matches(from: String, to: String) = this.from == from && this.to == to
    fun isBack(from: String, to: String) = matches(from, to) &&
        action in setOf(PageAction.Back, PageAction.GestureBack)
    fun isInstant(to: String) = this.to == to && action == PageAction.GestureBack
}

internal fun pageDirection(from: String, to: String, navigation: PageNavigation): Int =
    if (navigation.isBack(from, to)) -1 else 1

internal fun backGestureOffset(progress: Float, fromLeft: Boolean): Float =
    progress.coerceIn(0f, 1f) * if (fromLeft) 1f else -1f
