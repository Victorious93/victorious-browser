package com.victorious.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleLogTest {

    @Test
    fun appendCapsAndKeepsNewest() {
        var list = emptyList<ConsoleEntry>()
        repeat(5) { i ->
            list = ConsoleLog.append(list, ConsoleEntry("LOG", "m$i", "a.js", i, i.toLong()), max = 3)
        }
        assertEquals(listOf("m2", "m3", "m4"), list.map { it.message })
    }

    @Test
    fun formatAndFilter() {
        val e = ConsoleEntry("ERROR", "boom", "app.js", 12, 1)
        assertEquals("ERROR app.js:12  boom", ConsoleLog.format(e))
        val list = listOf(
            ConsoleEntry("LOG", "ok", "", 0, 1),
            e,
            ConsoleEntry("WARNING", "hmm", "x", 1, 2),
        )
        assertEquals(1, ConsoleLog.filter(list, "ERROR").size)
        assertEquals(3, ConsoleLog.filter(list, "ALL").size)
        assertEquals(1, ConsoleLog.errorCount(list))
    }

    @Test
    fun normalizeLevel() {
        assertEquals("WARNING", ConsoleLog.normalizeLevel("WARN"))
        assertEquals("ERROR", ConsoleLog.normalizeLevel("assert"))
        assertEquals("LOG", ConsoleLog.normalizeLevel(null))
        assertEquals("DEBUG", ConsoleLog.normalizeLevel("DEBUG"))
    }

    @Test
    fun formatWithoutLocation() {
        val e = ConsoleEntry("LOG", "hi", "", 0, 1)
        assertTrue(ConsoleLog.format(e).startsWith("LOG"))
        assertTrue(ConsoleLog.format(e).contains("hi"))
    }
}
