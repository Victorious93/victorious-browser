package com.victorious.browser.inspect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureLookupTest {

    private fun ex(id: Long, url: String) = CapturedExchange(
        id = id,
        method = "GET",
        url = url,
        host = CaptureLookup.originOf(url)?.substringAfter("://")?.substringBefore(":") ?: "",
        scheme = "https",
        requestHeaders = emptyList(),
        requestStartedAt = id,
    )

    @Test
    fun prefersExactUrlThenOrigin() {
        val list = listOf(
            ex(1, "https://a.example/one"),
            ex(2, "https://b.example/x"),
            ex(3, "https://a.example/two"),
            ex(4, "https://a.example/one"),
        )
        assertEquals(4L, CaptureLookup.latestForPage(list, "https://a.example/one")!!.id)
        // No exact URL — fall back to the latest same-origin capture (id 4).
        assertEquals(4L, CaptureLookup.latestForPage(list, "https://a.example/other")!!.id)
        assertNull(CaptureLookup.latestForPage(list, "https://c.example/"))
        assertNull(CaptureLookup.latestForPage(emptyList(), "https://a.example/"))
        assertNull(CaptureLookup.latestForPage(list, ""))
    }

    @Test
    fun ignoresFragmentWhenMatching() {
        val list = listOf(ex(1, "https://a.example/p"))
        assertEquals(1L, CaptureLookup.latestForPage(list, "https://a.example/p#frag")!!.id)
    }
}
