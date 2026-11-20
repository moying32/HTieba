package dev.hydrogen.tieba.ui

import org.junit.Assert.*
import org.junit.Test

class NavigationPhysicsTest {
    @Test fun dragNeverTriggersThePublishAction() {
        for (step in -100..500) for (speed in listOf(-15f, 0f, 15f))
            assertNotEquals(HomeTab.Compose, NavigationPhysics.destination(step / 100f, speed))
    }

    @Test fun endpointsAndReversedFlicksSettleToReachableTabs() {
        assertEquals(HomeTab.Feed, NavigationPhysics.destination(-1f, -15f))
        assertEquals(HomeTab.Profile, NavigationPhysics.destination(5f, 15f))
        assertEquals(HomeTab.Browse, NavigationPhysics.destination(2f, -10f))
        assertEquals(HomeTab.Messages, NavigationPhysics.destination(2f, 10f))
    }

    @Test fun edgeResistancePreservesDirectTrackingInsideTheBar() {
        assertEquals(2.25f, NavigationPhysics.resist(2.25f), .001f)
        assertTrue(NavigationPhysics.resist(-1f) in -1f..0f)
        assertTrue(NavigationPhysics.resist(5f) in 4f..5f)
    }
}
