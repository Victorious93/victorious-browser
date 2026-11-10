package com.victorious.browser.linkembed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedLinksTest {
    @Test fun extractsUrlsFromSharedText() {
        val text = "Check this out: https://example.com/a?b=1, and (https://en.wikipedia.org/wiki/Foo_(bar)). Also http://x.org."
        assertEquals(
            listOf("https://example.com/a?b=1", "https://en.wikipedia.org/wiki/Foo_(bar)", "http://x.org"),
            SharedLinks.extractUrls(text),
        )
    }

    @Test fun subjectFirstAndDeduped() {
        assertEquals(
            listOf("https://a.example/", "https://b.example/"),
            SharedLinks.extractUrls("https://b.example/ https://a.example/", subject = "https://a.example/"),
        )
    }

    @Test fun ignoresNonHttpSchemes() {
        assertTrue(SharedLinks.extractUrls("javascript:alert(1) file:///x content://y data:text/html,hi").isEmpty())
        assertTrue(SharedLinks.extractUrls(null).isEmpty())
    }

    @Test fun viewOnlyAcceptsHttp() {
        assertEquals("https://example.com/x", SharedLinks.fromView("https://example.com/x"))
        assertNull(SharedLinks.fromView("file:///sdcard/x.html"))
        assertNull(SharedLinks.fromView("javascript:alert(1)"))
        assertNull(SharedLinks.fromView(null))
    }

    @Test fun capsCount() {
        val many = (1..50).joinToString(" ") { "https://e$it.example/" }
        assertEquals(SharedLinks.MAX_URLS, SharedLinks.extractUrls(many).size)
    }
}
