package com.victorious.browser.browser.privacy

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerListUpdateTest {
    @After fun reset() = TrackerList.setExtra(emptyMap())

    @Test fun parsesValidAndRejectsBadEntries() {
        val json = """{"version":4,"advertising":["ads.example.net","Tracker.Example.org.","com","co.uk","bad host","*.x.com","-a.com"],
            "analytics":["stats.example.io"]}"""
        val p = TrackerListUpdate.parse(json).getOrThrow()
        assertEquals(4, p.version)
        assertEquals(setOf("ads.example.net", "tracker.example.org"), p.hosts[TrackerCategory.ADVERTISING])
        assertEquals(setOf("stats.example.io"), p.hosts[TrackerCategory.ANALYTICS])
        assertEquals(5, p.rejected)
        assertEquals(3, p.count)
    }

    @Test fun rejectsEmptyMalformedAndOversized() {
        assertTrue(TrackerListUpdate.parse("""{"advertising":[]}""").isFailure)
        assertTrue(TrackerListUpdate.parse("not json").isFailure)
        assertTrue(TrackerListUpdate.parse("""{"advertising":["com"]}""").isFailure)
        assertTrue(TrackerListUpdate.parse("x".repeat(TrackerListUpdate.MAX_BYTES + 1)).isFailure)
    }

    @Test fun onlyHttpsUrlsWithoutCredentials() {
        assertTrue(TrackerListUpdate.isAllowedUrl("https://raw.example.com/t.json"))
        assertFalse(TrackerListUpdate.isAllowedUrl("http://raw.example.com/t.json"))
        assertFalse(TrackerListUpdate.isAllowedUrl("https://user:pw@example.com/t.json"))
        assertFalse(TrackerListUpdate.isAllowedUrl("file:///sdcard/t.json"))
        assertFalse(TrackerListUpdate.isAllowedUrl("not a url"))
    }

    @Test fun extraHostsAreMergedNotReplacing() {
        assertNull(TrackerList.match("cdn.newads.example"))
        TrackerList.setExtra(mapOf(TrackerCategory.ADVERTISING to setOf("newads.example")))
        assertEquals(TrackerCategory.ADVERTISING, TrackerList.match("cdn.newads.example"))
        assertEquals(TrackerCategory.ADVERTISING, TrackerList.match("stats.g.doubleclick.net")) // bundled still works
        assertNull(TrackerList.match("notnewads.example"))
        assertNull(TrackerList.match("example"))
    }
}
