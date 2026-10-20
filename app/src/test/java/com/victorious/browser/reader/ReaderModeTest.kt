package com.victorious.browser.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderModeTest {
    @Test
    fun extractionScriptInlinesLibrarySourceAndCallsReadability() {
        val script = ReaderMode.buildExtractionScript("/* lib */")
        assertTrue(script.contains("/* lib */"))
        assertTrue(script.contains("new Readability(docClone).parse()"))
    }

    @Test
    fun parsesDoubleEncodedArticleJson() {
        // Simulates evaluateJavascript's callback: the script's JSON.stringify output,
        // itself JSON-encoded (quoted + escaped) by WebView's callback marshalling.
        val articleJson = """{"title":"A title","byline":"By someone","content":"<p>Body</p>"}"""
        val callbackValue = org.json.JSONObject().put("v", articleJson).toString().removePrefix("{\"v\":").removeSuffix("}")

        val article = ReaderMode.parseExtractionResult(callbackValue)
        assertEquals("A title", article?.title)
        assertEquals("By someone", article?.byline)
        assertEquals("<p>Body</p>", article?.contentHtml)
    }

    @Test
    fun treatsNullAndBlankAsNoArticle() {
        assertNull(ReaderMode.parseExtractionResult(null))
        assertNull(ReaderMode.parseExtractionResult(""))
        assertNull(ReaderMode.parseExtractionResult("null"))
    }

    @Test
    fun treatsMalformedCallbackValueAsNoArticle() {
        assertNull(ReaderMode.parseExtractionResult("{not json"))
    }

    @Test
    fun renderedDocumentEscapesTitleAndKeepsContentHtmlRaw() {
        val article = ReaderMode.Article(
            title = "<script>bad</script>",
            byline = "",
            contentHtml = "<p>safe content, trusted from Readability's own sanitization</p>",
        )
        val html = ReaderMode.renderDocument(article, dark = false)
        assertTrue(html.contains("&lt;script&gt;bad&lt;/script&gt;"))
        assertTrue(html.contains("<p>safe content, trusted from Readability's own sanitization</p>"))
    }

    @Test
    fun sanitizeStripsScriptStyleIframeAndEventHandlers() {
        val dirty = """
            <p onclick="evil()">Text</p>
            <script>alert(1)</script>
            <style>body{display:none}</style>
            <iframe src="https://evil.example"></iframe>
            <a href="javascript:alert(2)">link</a>
        """.trimIndent()
        val clean = ReaderMode.sanitizeContentHtml(dirty)
        assertTrue(!clean.contains("onclick"))
        assertTrue(!clean.contains("<script"))
        assertTrue(!clean.contains("<style"))
        assertTrue(!clean.contains("<iframe"))
        assertTrue(!clean.contains("javascript:"))
        assertTrue(clean.contains("<p >Text</p>") || clean.contains("<p>Text</p>"))
    }

    @Test
    fun sanitizeLeavesOrdinaryMarkupAlone() {
        val safe = "<p>Hello <b>world</b></p><img src=\"https://example.com/x.png\">"
        assertEquals(safe, ReaderMode.sanitizeContentHtml(safe))
    }

    @Test
    fun renderedDocumentOmitsBylineParagraphWhenBlank() {
        val article = ReaderMode.Article(title = "T", byline = "", contentHtml = "<p/>")
        val html = ReaderMode.renderDocument(article, dark = false)
        assertTrue(!html.contains("class=\"byline\""))
    }

    @Test
    fun renderedDocumentUsesDarkPaletteWhenRequested() {
        val article = ReaderMode.Article(title = "T", byline = "", contentHtml = "<p/>")
        val dark = ReaderMode.renderDocument(article, dark = true)
        val light = ReaderMode.renderDocument(article, dark = false)
        assertTrue(dark.contains("#121212"))
        assertTrue(light.contains("#ffffff"))
    }
}
