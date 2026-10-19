package com.victorious.browser.inspect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContentBlockerTest {
    @Test
    fun blocksExactAndSubdomainMatchesInEnabledCategory() {
        val enabled = setOf(BlockCategory.TRACKERS)
        assertEquals(BlockCategory.TRACKERS, ContentBlocker.isBlocked("google-analytics.com", enabled))
        assertEquals(BlockCategory.TRACKERS, ContentBlocker.isBlocked("www.google-analytics.com", enabled))
    }

    @Test
    fun doesNotMatchUnrelatedHosts() {
        assertNull(ContentBlocker.isBlocked("example.com", setOf(BlockCategory.TRACKERS)))
        assertNull(ContentBlocker.isBlocked("notgoogle-analytics.com", setOf(BlockCategory.TRACKERS)))
    }

    @Test
    fun onlyMatchesWithinEnabledCategories() {
        assertNull(ContentBlocker.isBlocked("coinhive.com", setOf(BlockCategory.TRACKERS)))
        assertEquals(BlockCategory.COIN_MINERS, ContentBlocker.isBlocked("coinhive.com", setOf(BlockCategory.COIN_MINERS)))
    }

    @Test
    fun emptyCategoriesBlockNothing() {
        assertNull(ContentBlocker.isBlocked("doubleclick.net", emptySet()))
    }

    @Test
    fun nullOrBlankHostIsNeverBlocked() {
        assertNull(ContentBlocker.isBlocked(null, setOf(BlockCategory.ADWARE_MALWARE)))
        assertNull(ContentBlocker.isBlocked("", setOf(BlockCategory.ADWARE_MALWARE)))
    }
}
