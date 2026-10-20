package com.victorious.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class NightModeTest {
    @Test
    fun prefersAlgorithmicWhenAvailable() {
        assertEquals(NightModeStrategy.ALGORITHMIC, chooseNightModeStrategy(true, true))
        assertEquals(NightModeStrategy.ALGORITHMIC, chooseNightModeStrategy(true, false))
    }

    @Test
    fun fallsBackToForceDark() {
        assertEquals(NightModeStrategy.FORCE_DARK, chooseNightModeStrategy(false, true))
    }

    @Test
    fun reportsUnsupportedWhenNeitherAvailable() {
        assertEquals(NightModeStrategy.UNSUPPORTED, chooseNightModeStrategy(false, false))
    }
}
