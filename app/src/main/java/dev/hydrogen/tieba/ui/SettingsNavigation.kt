package dev.hydrogen.tieba.ui

internal fun settingsDepth(page: String?): Int = when (page) {
    null -> 0
    "themes", "fonts", "filters", "about-updates" -> 2
    "guide" -> 3
    else -> 1
}

internal fun settingsDirection(from: String?, to: String?): Int =
    if (settingsDepth(to) >= settingsDepth(from)) 1 else -1
