package com.victorious.browser.help

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class ManualParserTest {

    private val sample = """
        # Victorious Browser — Manual

        ## About & Safety

        Authorized-testing use only. Scope gating applies.

        - Off by default.
        - External control off by default.

        ## Scanner

        What it does. Active scanner for XSS and SQLi.

        ### How to use

        Add an in-scope host and Run.

        ```
        fenced code line
        ```

        ## Fleet Bridge v1

        | op | kind |
        |---|---|
        | capabilities.get | read |
    """.trimIndent()

    @Test
    fun parse_extractsDocTitleAndSections() {
        val doc = ManualParser.parse(sample)
        assertEquals("Victorious Browser — Manual", doc.title)
        val titles = doc.sections.map { it.title }
        assertEquals(listOf("About & Safety", "Scanner", "Fleet Bridge v1"), titles)
    }

    @Test
    fun parse_capturesBulletsHeadingsCodeAndParagraphs() {
        val doc = ManualParser.parse(sample)
        val about = doc.sections.first { it.title == "About & Safety" }
        assertTrue(about.blocks.any { it is ManualBlock.Paragraph })
        assertEquals(2, about.blocks.count { it is ManualBlock.Bullet })

        val scanner = doc.sections.first { it.title == "Scanner" }
        assertTrue(scanner.blocks.any { it is ManualBlock.Heading && it.level == 3 && it.text == "How to use" })
        assertTrue(scanner.blocks.any { it is ManualBlock.Code && it.text.contains("fenced code line") })
    }

    @Test
    fun parse_rendersTableAsCodeBlockWithoutSeparatorRow() {
        val doc = ManualParser.parse(sample)
        val bridge = doc.sections.first { it.title == "Fleet Bridge v1" }
        val code = bridge.blocks.filterIsInstance<ManualBlock.Code>().firstOrNull()
        assertTrue(code != null)
        assertTrue(code!!.text.contains("capabilities.get"))
        assertFalse(code.text.contains("---"))
    }

    @Test
    fun anchorOf_isStableAndSlugLike() {
        assertEquals("about-safety", ManualParser.anchorOf("About & Safety"))
        assertEquals("fleet-bridge-v1-remote-control", ManualParser.anchorOf("Fleet Bridge v1 / Remote Control"))
    }

    @Test
    fun search_blankReturnsAll() {
        val doc = ManualParser.parse(sample)
        assertEquals(doc.sections.size, ManualSearch.filter(doc.sections, "   ").size)
    }

    @Test
    fun search_matchesTitleAndBodyCaseInsensitive() {
        val doc = ManualParser.parse(sample)
        val xss = ManualSearch.filter(doc.sections, "XSS")
        assertEquals(1, xss.size)
        assertEquals("Scanner", xss.first().title)

        val scope = ManualSearch.filter(doc.sections, "scope gating")
        assertEquals(1, scope.size)
        assertEquals("About & Safety", scope.first().title)
    }

    @Test
    fun search_allTokensMustMatch() {
        val doc = ManualParser.parse(sample)
        assertEquals(0, ManualSearch.filter(doc.sections, "scanner nonexistentword").size)
        assertEquals(1, ManualSearch.filter(doc.sections, "scanner sqli").size)
    }
}
