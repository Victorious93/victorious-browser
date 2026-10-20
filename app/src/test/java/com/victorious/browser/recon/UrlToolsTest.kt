package com.victorious.browser.recon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlToolsTest {

    @Test
    fun parse_fullUrl() {
        val p = UrlTools.parse("https://user@example.com:8443/a/b?x=1&y=two%20words#frag")
        assertNull(p.error)
        assertEquals("https", p.scheme)
        assertEquals("user", p.userInfo)
        assertEquals("example.com", p.host)
        assertEquals(8443, p.port)
        assertEquals("/a/b", p.path)
        assertEquals("frag", p.fragment)
        assertEquals(2, p.params.size)
        assertEquals("1", UrlTools.getParam(p.params, "x").first())
        assertEquals("two words", UrlTools.getParam(p.params, "y").first())
    }

    @Test
    fun parse_noPort_isMinusOne() {
        val p = UrlTools.parse("http://host/path")
        assertEquals(-1, p.port)
        assertTrue(p.params.isEmpty())
    }

    @Test
    fun splitQuery_handlesKeyWithoutValue() {
        val params = UrlTools.splitQuery("a=1&flag&b=two%20words")
        assertEquals(3, params.size)
        assertEquals(UrlTools.Param("flag", ""), params[1])
        assertEquals("two words", params[2].value)
    }

    @Test
    fun buildQuery_encodesValues() {
        val q = UrlTools.buildQuery(listOf(UrlTools.Param("q", "a b"), UrlTools.Param("flag", "")))
        assertEquals("q=a%20b&flag", q)
    }

    @Test
    fun setParam_updatesExisting() {
        val base = listOf(UrlTools.Param("a", "1"), UrlTools.Param("b", "2"))
        val updated = UrlTools.setParam(base, "a", "9")
        assertEquals("9", UrlTools.getParam(updated, "a").first())
        assertEquals(2, updated.size)
    }

    @Test
    fun setParam_addsWhenAbsent() {
        val updated = UrlTools.setParam(emptyList(), "new", "v")
        assertEquals(listOf(UrlTools.Param("new", "v")), updated)
    }

    @Test
    fun addAndRemoveParam() {
        var params = UrlTools.addParam(emptyList(), "a", "1")
        params = UrlTools.addParam(params, "a", "2")
        assertEquals(2, params.size)
        params = UrlTools.removeParam(params, "a")
        assertTrue(params.isEmpty())
    }

    @Test
    fun build_roundTripsParse() {
        val url = "https://example.com/p?x=1&y=2"
        val p = UrlTools.parse(url)
        assertEquals(url, UrlTools.build(p))
    }

    @Test
    fun normalize_lowercasesAndDropsDefaultPort() {
        assertEquals("https://example.com/", UrlTools.normalize("HTTPS://Example.COM:443"))
        assertEquals("http://example.com/", UrlTools.normalize("HTTP://example.com:80/"))
    }

    @Test
    fun normalize_keepsNonDefaultPort() {
        assertEquals("https://example.com:8443/x", UrlTools.normalize("https://example.com:8443/x"))
    }

    @Test
    fun collapseDotSegments() {
        assertEquals("/a/c", UrlTools.collapseDotSegments("/a/b/../c"))
        assertEquals("/a/", UrlTools.collapseDotSegments("/a/b/../"))
        assertEquals("/", UrlTools.collapseDotSegments("/a/.."))
        assertEquals("/x/y", UrlTools.collapseDotSegments("/x/./y"))
    }

    @Test
    fun normalize_collapsesDotSegmentsInPath() {
        assertEquals("https://example.com/a/c", UrlTools.normalize("https://example.com/a/b/../c"))
    }
}
