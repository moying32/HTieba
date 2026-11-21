package dev.hydrogen.tieba.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsNavigationTest {
    @Test fun everySettingsBranchReversesOnReturn() {
        val paths = listOf(
            listOf(null, "appearance", "themes", "guide"),
            listOf(null, "size", "fonts"),
            listOf(null, "media", "filters"),
            listOf(null, "interaction"), listOf(null, "account"), listOf(null, "about", "about-updates"),
            listOf(null, "updates"),
        )
        paths.forEach { path -> path.zipWithNext().forEach { (parent, child) ->
            assertEquals(1, settingsDirection(parent, child))
            assertEquals(-1, settingsDirection(child, parent))
        } }
    }
}
