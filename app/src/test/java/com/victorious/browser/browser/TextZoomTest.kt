package com.victorious.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the pure page text-zoom logic ([TextZoom]). Victorious Framework. */
class TextZoomTest {

    @Test fun levelsAreSortedAscendingAndContainDefault() {
        assertEquals(TextZoom.LEVELS.sorted(), TextZoom.LEVELS)
        assertTrue(TextZoom.DEFAULT in TextZoom.LEVELS)
        assertEquals(TextZoom.LEVELS.first(), TextZoom.MIN)
        assertEquals(TextZoom.LEVELS.last(), TextZoom.MAX)
    }

    @Test fun snapKeepsValidLevelsUnchanged() {
        for (level in TextZoom.LEVELS) assertEquals(level, TextZoom.snap(level))
    }

    @Test fun snapClampsOutOfRange() {
        assertEquals(TextZoom.MIN, TextZoom.snap(0))
        assertEquals(TextZoom.MIN, TextZoom.snap(-500))
        assertEquals(TextZoom.MAX, TextZoom.snap(10_000))
    }

    @Test fun snapRoundsToNearestLevel() {
        // 100 and 110 are adjacent levels.
        assertEquals(100, TextZoom.snap(102))
        assertEquals(110, TextZoom.snap(108))
    }

    @Test fun snapTiePrefersLowerLevel() {
        // Exactly between 100 and 110 -> lower (100).
        assertEquals(100, TextZoom.snap(105))
        // Between 150 and 175 midpoint is 162.5; 163 rounds up to 175, 162 down to 150.
        assertEquals(175, TextZoom.snap(163))
        assertEquals(150, TextZoom.snap(162))
    }

    @Test fun stepUpAdvancesOneLevelAndStopsAtMax() {
        assertEquals(110, TextZoom.stepUp(100))
        assertEquals(125, TextZoom.stepUp(110))
        assertEquals(TextZoom.MAX, TextZoom.stepUp(TextZoom.MAX))
        // From an off-ladder value it snaps first, then steps.
        assertEquals(110, TextZoom.stepUp(103))
    }

    @Test fun stepDownRetreatsOneLevelAndStopsAtMin() {
        assertEquals(100, TextZoom.stepDown(110))
        assertEquals(90, TextZoom.stepDown(100))
        assertEquals(TextZoom.MIN, TextZoom.stepDown(TextZoom.MIN))
        assertEquals(100, TextZoom.stepDown(108))
    }

    @Test fun roundTripStepUpThenDownReturnsToStart() {
        var z = 100
        z = TextZoom.stepUp(z)
        z = TextZoom.stepDown(z)
        assertEquals(100, z)
    }

    @Test fun modifiedAndBoundsFlags() {
        assertFalse(TextZoom.isModified(100))
        assertFalse(TextZoom.isModified(102)) // snaps to 100
        assertTrue(TextZoom.isModified(125))

        assertFalse(TextZoom.canIncrease(TextZoom.MAX))
        assertTrue(TextZoom.canIncrease(TextZoom.MIN))
        assertFalse(TextZoom.canDecrease(TextZoom.MIN))
        assertTrue(TextZoom.canDecrease(TextZoom.MAX))
    }

    @Test fun labelFormatsSnappedPercent() {
        assertEquals("100%", TextZoom.label(100))
        assertEquals("100%", TextZoom.label(103))
        assertEquals("200%", TextZoom.label(9999))
    }
}
