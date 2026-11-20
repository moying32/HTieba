package dev.hydrogen.tieba.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.Assert.*
import org.junit.Test

class BrowsingHeaderTest {
    @Test fun smallDragAndImmediateReversalHaveNoThresholdOrDeadZone() {
        val state = BrowsingHeaderState().apply { updateHeight(100f) }
        assertEquals(-12f, state.scrollBy(-12f), .001f)
        assertEquals(-12f, state.offset, .001f)
        assertEquals(3f, state.scrollBy(3f), .001f)
        assertEquals(-9f, state.offset, .001f)
    }

    @Test fun scrollDistanceIsSharedWithListAndPullToRefreshOnlyAtBounds() {
        val state = BrowsingHeaderState().apply { updateHeight(100f) }
        val up = -140f
        assertEquals(-40f, up - state.scrollBy(up), .001f)
        assertEquals(0f, state.scrollBy(-20f), .001f)
        val down = 160f
        assertEquals(60f, down - state.scrollBy(down), .001f)
        assertEquals(0f, state.offset, .001f)
    }

    @Test fun flingAndTouchBothTrackVerticalDistanceAndLeaveHorizontalScrollUntouched() {
        val state = BrowsingHeaderState().apply { updateHeight(100f) }
        assertEquals(Offset(0f, -18f), state.connection.onPreScroll(Offset(20f, -18f), NestedScrollSource.UserInput))
        assertEquals(Offset(0f, -7f), state.connection.onPreScroll(Offset(0f, -7f), NestedScrollSource.SideEffect))
        assertEquals(-25f, state.offset, .001f)
        assertEquals(Offset.Zero, state.connection.onPreScroll(Offset(20f, 0f), NestedScrollSource.UserInput))
    }

    @Test fun disabledAndUnmeasuredHeadersLeaveScrollingAlone() {
        val disabled = BrowsingHeaderState(false).apply { updateHeight(100f) }
        assertEquals(0f, disabled.scrollBy(-200f), .001f)
        assertEquals(0f, disabled.offset, .001f)
        assertEquals(0f, BrowsingHeaderState().scrollBy(-200f), .001f)
    }

    @Test fun fontAndWindowChangesKeepTheOffsetWithinTheNewHeaderHeight() {
        val state = BrowsingHeaderState().apply { updateHeight(100f); scrollBy(-100f) }
        state.updateHeight(60f)
        assertEquals(-60f, state.offset, .001f)
        state.updateHeight(160f)
        assertEquals(-60f, state.offset, .001f)
        assertEquals(-100f, state.scrollBy(-200f), .001f)
    }
}
