package com.victorious.browser.liteapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiteAppLinksTest {
    private val id = "3f2b8c1e-9a4d-4e57-8b0a-1c2d3e4f5a6b"

    @Test
    fun roundTripsPanelId() {
        assertEquals(id, LiteAppLinks.parsePanelId(LiteAppLinks.dataString(id)))
    }

    @Test
    fun rejectsForeignSchemesAndUrls() {
        assertNull(LiteAppLinks.parsePanelId(null))
        assertNull(LiteAppLinks.parsePanelId("https://evil.example/panel/$id"))
        assertNull(LiteAppLinks.parsePanelId("victorious-lite://panel/"))
        assertNull(LiteAppLinks.parsePanelId("victorious-lite://other/$id"))
    }

    @Test
    fun rejectsPathTraversalAndExtraSegments() {
        assertNull(LiteAppLinks.parsePanelId("victorious-lite://panel/$id/extra"))
        assertNull(LiteAppLinks.parsePanelId("victorious-lite://panel/../$id"))
        assertNull(LiteAppLinks.parsePanelId("victorious-lite://panel/${"a".repeat(65)}"))
    }
}
