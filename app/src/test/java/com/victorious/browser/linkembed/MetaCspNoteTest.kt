package com.victorious.browser.linkembed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetaCspNoteTest {
    @Test
    fun parserExtractsMetaCsp() {
        val html = """
            <html><head>
            <meta http-equiv="Content-Security-Policy" content="default-src 'self'; frame-ancestors 'none'">
            <meta property="og:title" content="Hi">
            </head><body></body></html>
        """.trimIndent()
        val meta = HtmlMetadataParser.parse(html, "https://example.com/")
        assertEquals(listOf("default-src 'self'; frame-ancestors 'none'"), meta.metaCsp)
        assertEquals("Hi", meta.title)
    }

    @Test
    fun withMetaCspNotesDoesNotChangeVerdict() {
        val base = EmbedRecon.evaluate(
            "https://example.com/",
            "https://example.com/",
            200,
            mapOf("Content-Type" to listOf("text/html")),
        )
        assertEquals(EmbedRecon.Verdict.EMBEDDABLE, base.verdict)
        val noted = EmbedRecon.withMetaCspNotes(base, listOf("frame-ancestors 'none'"))
        assertEquals(EmbedRecon.Verdict.EMBEDDABLE, noted.verdict)
        assertTrue(noted.reasons.any { it.contains("meta http-equiv") && it.contains("ignore") })
    }

    @Test
    fun metaWithoutFrameAncestorsStillNoted() {
        val base = EmbedRecon.evaluate("https://e/", "https://e/", 200, emptyMap())
        val noted = EmbedRecon.withMetaCspNotes(base, listOf("default-src 'self'"))
        assertTrue(noted.reasons.any { it.contains("no frame-ancestors") })
    }
}
