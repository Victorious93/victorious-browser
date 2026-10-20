package com.victorious.browser.fuzz

import org.junit.Assert.assertEquals
import org.junit.Test

class WordlistParserTest {
    @Test fun lines_stripsCommentsBlanksDedup() {
        val raw = "# comment\nadmin\n\n  login \nadmin\n#another\nconfig"
        assertEquals(listOf("admin", "login", "config"), WordlistParser.lines(raw))
    }

    @Test fun lines_noDedup() {
        assertEquals(listOf("a", "a"), WordlistParser.lines("a\na", dedup = false))
    }

    @Test fun preview_limits() {
        val raw = (1..100).joinToString("\n") { "w$it" }
        assertEquals(5, WordlistParser.preview(raw, 5).size)
    }

    @Test fun count() {
        assertEquals(2, WordlistParser.count("a\n#c\nb\n"))
    }

    @Test fun safeName() {
        assertEquals("my-list", WordlistParser.safeName("My List!!"))
        assertEquals("wordlist", WordlistParser.safeName("   "))
        assertEquals("a.b-c", WordlistParser.safeName("a.b c"))
    }
}
