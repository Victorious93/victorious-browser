package com.victorious.browser.browser

import com.victorious.browser.AppDestination
import com.victorious.browser.toolDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolsCatalogTest {
    @Test
    fun everyCatalogIdMapsToANonBrowserDestination() {
        for (id in ToolsCatalog.allIds) {
            val dest = toolDestination(id)
            assertTrue(
                "id=$id mapped to Browser (missing from toolDestination?)",
                dest !is AppDestination.Browser,
            )
            assertNotNull(ToolsCatalog.find(id))
        }
    }

    @Test
    fun quickIdsAreASubsetOfAllIds() {
        assertTrue(ToolsCatalog.quickIds.isNotEmpty())
        assertTrue(ToolsCatalog.allIds.containsAll(ToolsCatalog.quickIds))
    }

    @Test
    fun unknownIdFallsBackToBrowser() {
        assertEquals(AppDestination.Browser, toolDestination("nope"))
    }

    @Test
    fun newTabUrlHelper() {
        assertTrue(isNewTabUrl(NEW_TAB_URL))
        assertTrue(isNewTabUrl(""))
        assertTrue(isNewTabUrl(null))
        assertTrue(isNewTabUrl("victorious://newtab"))
        assertFalse(isNewTabUrl("https://example.com"))
        assertFalse(isNewTabUrl("about:blank"))
    }
}
