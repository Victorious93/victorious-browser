package com.victorious.browser.fuzz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzLogicTest {
    @Test fun hasMarker() {
        assertTrue(FuzzLogic.hasMarker("http://h/FUZZ"))
        assertFalse(FuzzLogic.hasMarker("http://h/"))
    }

    @Test fun expandWord_withExtensions() {
        assertEquals(listOf("admin", "admin.php", "admin.bak"),
            FuzzLogic.expandWord("admin", listOf("php", ".bak")))
        assertEquals(listOf("admin"), FuzzLogic.expandWord("admin", emptyList()))
        assertEquals(emptyList<String>(), FuzzLogic.expandWord("  ", listOf("php")))
    }

    @Test fun buildUrl_markerMode() {
        assertEquals("http://h/admin/x", FuzzLogic.buildUrl("http://h/FUZZ/x", "admin"))
    }

    @Test fun buildUrl_appendMode_slashHandling() {
        assertEquals("http://h/admin", FuzzLogic.buildUrl("http://h/", "/admin"))
        assertEquals("http://h/admin", FuzzLogic.buildUrl("http://h", "admin"))
        assertEquals("http://h/admin", FuzzLogic.buildUrl("http://h/", "admin"))
        assertEquals("http://h/admin", FuzzLogic.buildUrl("http://h", "/admin"))
    }

    @Test fun targets_dedupAndExpand() {
        val t = FuzzLogic.targets("http://h/", listOf("a", "a", "b"), listOf("php"))
        assertEquals(listOf("http://h/a", "http://h/a.php", "http://h/b", "http://h/b.php"), t)
    }

    @Test fun metaOf_counts() {
        val m = FuzzLogic.metaOf(200, "one two\nthree")
        assertEquals(200, m.status)
        assertEquals("one two\nthree".toByteArray().size, m.size)
        assertEquals(3, m.words)
        assertEquals(2, m.lines)
    }

    @Test fun metaOf_empty() {
        val m = FuzzLogic.metaOf(null, null)
        assertEquals(0, m.size); assertEquals(0, m.words); assertEquals(0, m.lines)
    }

    @Test fun parseIntCsv() {
        assertEquals(setOf(200, 301, 403), FuzzLogic.parseIntCsv("200, 301 ,403, junk"))
    }

    @Test fun accept_defaultCodes() {
        val spec = FuzzLogic.FilterSpec()
        assertTrue(FuzzLogic.accept(FuzzLogic.RespMeta(200, 10, 1, 1), spec))
        assertFalse(FuzzLogic.accept(FuzzLogic.RespMeta(404, 10, 1, 1), spec))
        assertFalse(FuzzLogic.accept(FuzzLogic.RespMeta(null, 10, 1, 1), spec))
    }

    @Test fun accept_matchAndFilterCodes() {
        val spec = FuzzLogic.FilterSpec(matchCodes = setOf(200), filterCodes = setOf(200))
        // filter wins over match
        assertFalse(FuzzLogic.accept(FuzzLogic.RespMeta(200, 10, 1, 1), spec))
    }

    @Test fun accept_sizeFilter() {
        val spec = FuzzLogic.FilterSpec(filterSizes = setOf(0))
        assertFalse(FuzzLogic.accept(FuzzLogic.RespMeta(200, 0, 0, 0), spec))
        assertTrue(FuzzLogic.accept(FuzzLogic.RespMeta(200, 5, 1, 1), spec))
    }

    @Test fun accept_matchSizesWordsLines() {
        val spec = FuzzLogic.FilterSpec(matchSizes = setOf(5), matchWords = setOf(1), matchLines = setOf(1))
        assertTrue(FuzzLogic.accept(FuzzLogic.RespMeta(200, 5, 1, 1), spec))
        assertFalse(FuzzLogic.accept(FuzzLogic.RespMeta(200, 6, 1, 1), spec))
    }

    @Test fun dedupInsert() {
        val h1 = FuzzLogic.Hit("/a", 200, 1, 1, 1)
        val h1b = FuzzLogic.Hit("/a", 301, 2, 2, 2)
        val h2 = FuzzLogic.Hit("/b", 200, 1, 1, 1)
        var list = FuzzLogic.dedupInsert(emptyList(), h1)
        list = FuzzLogic.dedupInsert(list, h1b)
        list = FuzzLogic.dedupInsert(list, h2)
        assertEquals(2, list.size)
        assertEquals(200, list[0].status)
    }
}
