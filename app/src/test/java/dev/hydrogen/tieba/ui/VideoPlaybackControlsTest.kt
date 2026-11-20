package dev.hydrogen.tieba.ui

import org.junit.Assert.*
import org.junit.Test

class VideoPlaybackControlsTest {
    @Test fun eitherEdgeBoostsButTheCenterRemainsAnOrdinaryPictureTap() {
        for(width in listOf(240f,390f,844f)) {
            for(fraction in listOf(0f,.1f,.35f,.65f,.9f,1f))
                assertTrue(VideoControlsPolicy.isHoldEdge(width*fraction,width))
            for(fraction in listOf(.36f,.5f,.64f))
                assertFalse(VideoControlsPolicy.isHoldEdge(width*fraction,width))
        }
        assertFalse(VideoControlsPolicy.isHoldEdge(-1f,390f))
        assertFalse(VideoControlsPolicy.isHoldEdge(391f,390f))
        assertFalse(VideoControlsPolicy.isHoldEdge(0f,0f))
    }

    @Test fun releasingRestoresEverySelectedRateIncludingAnExistingDoubleSpeed() {
        for(original in listOf(.5f,1f,1.25f,1.5f,2f)) {
            val press=VideoHoldSpeed()
            assertEquals(2f,press.begin(true,original)!!,0f)
            assertTrue(press.active)
            assertEquals(original,press.finish()!!,0f)
            assertFalse(press.active)
        }
    }

    @Test fun cancellationAndLifecycleCleanupAreIdempotentAndAllowAnotherPress() {
        val press=VideoHoldSpeed()
        press.begin(true,1.5f)
        assertEquals(1.5f,press.finish()!!,0f)
        assertNull(press.finish())
        assertNull(press.finish())
        press.begin(true,.5f)
        assertEquals(.5f,press.finish()!!,0f)
    }

    @Test fun pausedVideoCannotBeStartedByAnAccidentalHold() {
        val press=VideoHoldSpeed()
        assertNull(press.begin(false,1.25f))
        assertFalse(press.active)
        assertNull(press.finish())
    }

    @Test fun repeatedLongPressCallbackCannotReplaceTheOriginalSpeedSnapshot() {
        val press=VideoHoldSpeed()
        press.begin(true,1.25f)
        assertNull(press.begin(true,2f))
        assertEquals(1.25f,press.finish()!!,0f)
    }

    @Test fun floatingControlsStayAvailableWhileInteractingOrUnableToPlay() {
        fun hides(playing:Boolean=true,busy:Boolean=false,seeking:Boolean=false,menu:Boolean=false,
            exploration:Boolean=false,holding:Boolean=false,failed:Boolean=false,focus:Boolean=false) =
            VideoControlsPolicy.canAutoHide(playing,busy,seeking,menu,exploration,holding,failed,focus)
        assertTrue(hides())
        assertFalse(hides(playing=false))
        assertFalse(hides(busy=true))
        assertFalse(hides(seeking=true))
        assertFalse(hides(menu=true))
        assertFalse(hides(exploration=true))
        assertFalse(hides(holding=true))
        assertFalse(hides(failed=true))
        assertFalse(hides(focus=true))
    }
}
