package com.starfall.gsadrive.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationGesturesTest {
    private fun swipe(start: Float, distance: Float) = navigationSwipe(start, distance, 24f, 48f)

    @Test fun leftEdgeSwipeOpensDrawer() {
        assertEquals(NavigationSwipe.OPEN_DRAWER, swipe(0f, 100f))
        assertEquals(NavigationSwipe.OPEN_DRAWER, swipe(24f, 100f))
    }

    @Test fun rightSwipeAwayFromEdgeSelectsPreviousTab() {
        assertEquals(NavigationSwipe.PREVIOUS_TAB, swipe(25f, 100f))
        assertEquals(NavigationSwipe.PREVIOUS_TAB, swipe(200f, 100f))
    }

    @Test fun leftSwipeSelectsNextTab() {
        assertEquals(NavigationSwipe.NEXT_TAB, swipe(200f, -100f))
    }

    @Test fun edgeSwipeDoesNotSwitchTabs() {
        assertNull(swipe(12f, -100f))
    }

    @Test fun shortGesturesDoNothing() {
        assertNull(swipe(12f, 20f))
        assertNull(swipe(200f, 20f))
        assertNull(swipe(200f, -20f))
    }
}
