package com.victorious.browser.scanner

import com.victorious.browser.inspect.CapturedExchange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SitemapBuilderTest {
    private fun ex(id: Long, method: String, url: String, host: String, status: Int? = 200) =
        CapturedExchange(
            id = id, method = method, url = url, host = host, scheme = "https",
            requestHeaders = emptyList(), requestStartedAt = 0, statusCode = status,
        )

    @Test fun normalizeUrl_dropsFragmentDefaultPort() {
        assertEquals("https://example.com/a?x=1", SitemapBuilder.normalizeUrl("https://example.com:443/a?x=1#frag"))
        assertEquals("http://example.com:8080/", SitemapBuilder.normalizeUrl("http://example.com:8080/"))
    }
    @Test fun dedupKey_sameMethodUrl() {
        assertEquals(
            SitemapBuilder.dedupKey("get", "https://x.com/a#f"),
            SitemapBuilder.dedupKey("GET", "https://x.com/a"),
        )
    }
    @Test fun extractLinks_resolvesAndFilters() {
        val html = """<a href="/foo">x</a><a href='https://other.com/y'>y</a>
            <a href="javascript:void(0)">j</a><img src="img/a.png"><a href="#top">t</a>"""
        val links = SitemapBuilder.extractLinks(html, "https://example.com/dir/page")
        assertTrue(links.contains("https://example.com/foo"))
        assertTrue(links.contains("https://other.com/y"))
        assertTrue(links.contains("https://example.com/dir/img/a.png"))
        assertFalse(links.any { it.startsWith("javascript") })
        assertFalse(links.any { it.endsWith("#top") })
    }
    @Test fun endpointsFromHistory_dedupAndScope() {
        val list = listOf(
            ex(1, "GET", "https://example.com/a", "example.com"),
            ex(2, "GET", "https://example.com/a#x", "example.com"),
            ex(3, "GET", "https://other.com/b", "other.com"),
        )
        val all = SitemapBuilder.endpointsFromHistory(list)
        assertEquals(2, all.size)
        val scoped = SitemapBuilder.endpointsFromHistory(list, listOf("example.com"))
        assertEquals(1, scoped.size)
        assertEquals("example.com", scoped[0].host)
    }
    @Test fun buildTree_groupsByHost() {
        val eps = SitemapBuilder.endpointsFromHistory(
            listOf(
                ex(1, "GET", "https://example.com/a/b", "example.com"),
                ex(2, "POST", "https://example.com/a/c", "example.com"),
            ),
        )
        val tree = SitemapBuilder.buildTree(eps)
        assertEquals(1, tree.size)
        assertEquals("example.com", tree[0].label)
    }
    @Test fun mergeEndpoints_historyWins() {
        val h = SitemapBuilder.Endpoint("GET", "https://e.com/a", "e.com", "/a", 200, false)
        val c = SitemapBuilder.Endpoint("GET", "https://e.com/a", "e.com", "/a", 500, true)
        val merged = SitemapBuilder.mergeEndpoints(listOf(h), listOf(c))
        assertEquals(1, merged.size)
        assertFalse(merged[0].fromCrawl)
    }
}
