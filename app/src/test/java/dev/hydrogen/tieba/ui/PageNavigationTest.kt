package dev.hydrogen.tieba.ui

import org.junit.Assert.*
import org.junit.Test

class PageNavigationTest {
    @Test fun returnUsesOperationInsteadOfPageTypeDepth() {
        listOf(
            "forum:原神" to "thread:123",
            "panel:2" to "panel:1",
            "chat:private:12" to "thread:123",
            "thread:456" to "thread:123",
            "tab:Profile" to "tab:Feed",
        ).forEach { (from, to) ->
            assertEquals(-1, pageDirection(from, to, PageNavigation(1, from, to, PageAction.Back)))
            assertEquals(1, pageDirection(from, to, PageNavigation(2, from, to, PageAction.Open)))
        }
    }

    @Test fun aStaleBackOperationCannotReverseTheNextOpen() {
        val back = PageNavigation(2, "panel:2", "thread:123", PageAction.Back)
        assertEquals(1, pageDirection("thread:123", "panel:3", back))
        assertEquals(1, pageDirection("panel:2", "forum:原神", back))
    }

    @Test fun predictiveCommitMatchesOnlyItsOwnRoutePair() {
        val gesture = PageNavigation(3, "forum:原神", "thread:123", PageAction.GestureBack)
        assertTrue(gesture.isPredictiveBack("forum:原神", "thread:123"))
        assertFalse(gesture.isPredictiveBack("thread:123", "forum:原神"))
        assertEquals(-1, pageDirection(gesture.from, gesture.to, gesture))
        assertFalse(gesture.copy(action = PageAction.Back).isPredictiveBack(gesture.from, gesture.to))
        assertFalse(gesture.copy(action = PageAction.Open).isPredictiveBack(gesture.from, gesture.to))
    }

    @Test fun gestureFeedbackFollowsEitherEdgeAndClampsProgress() {
        assertEquals(.6f, backGestureOffset(.6f, true), 0f)
        assertEquals(-.6f, backGestureOffset(.6f, false), 0f)
        assertEquals(0f, backGestureOffset(-1f, true), 0f)
        assertEquals(1f, backGestureOffset(2f, true), 0f)
        assertEquals(-1f, backGestureOffset(2f, false), 0f)
    }

    @Test fun settingsReturnTargetsTheActualParent() {
        listOf(null, "appearance", "size", "media", "interaction", "account", "about", "updates").forEach {
            assertNull(settingsParent(it))
        }
        mapOf("guide" to "themes", "themes" to "appearance", "fonts" to "size",
            "filters" to "media", "about-updates" to "about").forEach { (child, parent) ->
            assertEquals(parent, settingsParent(child))
            assertEquals(-1, settingsDirection(child, settingsParent(child)))
        }
    }
}
