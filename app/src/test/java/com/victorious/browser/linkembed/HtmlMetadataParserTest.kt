package com.victorious.browser.linkembed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlMetadataParserTest {
    private val page = "https://example.com/posts/1"

    @Test fun openGraphWins() {
        val html = """
            <html><head>
            <title>HTML title</title>
            <meta name="twitter:title" content="Twitter title">
            <meta property="og:title" content="OG &amp; title">
            <meta property="og:description" content="OG desc">
            <meta name="description" content="Plain desc">
            <meta property="og:site_name" content="Example Site">
            <meta property="og:image" content="/img/card.png">
            <meta property="og:url" content="https://example.com/canonical">
            <meta property="og:type" content="article">
            </head><body></body></html>
        """.trimIndent()
        val m = HtmlMetadataParser.parse(html, page)
        assertEquals("OG & title", m.title)
        assertEquals("og", m.titleSource)
        assertEquals("OG desc", m.description)
        assertEquals("Example Site", m.siteName)
        assertEquals("https://example.com/img/card.png", m.imageUrl)
        assertEquals("https://example.com/canonical", m.canonicalUrl)
        assertEquals("article", m.type)
    }

    @Test fun twitterCardFallback() {
        val html = """<head><title>T</title><meta name='twitter:title' content='TW'><meta name=twitter:description content=TWD>
            <meta name="twitter:image" content="https://cdn.example.com/t.jpg"><meta name="twitter:card" content="summary_large_image"></head>"""
        val m = HtmlMetadataParser.parse(html, page)
        assertEquals("TW", m.title)
        assertEquals("twitter", m.titleSource)
        assertEquals("TWD", m.description)
        assertEquals("https://cdn.example.com/t.jpg", m.imageUrl)
        assertEquals("summary_large_image", m.type)
    }

    @Test fun titleAndFaviconFallbacks() {
        val html = "<HTML><HEAD><TITLE>\n  Plain   &lt;Title&gt; &#8211; &#x1F600;\n</TITLE><meta name=\"description\" content=\"D\"></HEAD></HTML>"
        val m = HtmlMetadataParser.parse(html, page)
        assertEquals("Plain <Title> \u2013 \uD83D\uDE00", m.title)
        assertEquals("title", m.titleSource)
        assertEquals("D", m.description)
        assertEquals("https://example.com/favicon.ico", m.faviconUrl)
        assertNull(m.imageUrl)
    }

    @Test fun noTitleAtAll() {
        val m = HtmlMetadataParser.parse("<p>hello</p>", page)
        assertNull(m.title)
        assertEquals("url", m.titleSource)
    }

    @Test fun linkIconAndOEmbedDiscoveryAndBase() {
        val html = """<head><base href="https://static.example.net/root/">
            <link rel="shortcut icon" href="fav.png">
            <link rel="alternate" type="application/json+oembed" href="https://example.com/oembed?url=x&amp;format=json">
            <link rel="alternate" type="text/xml+oembed" href="https://example.com/oembed.xml">
            <meta property="og:image" content="cards/a.png"></head>"""
        val m = HtmlMetadataParser.parse(html, page)
        assertEquals("https://static.example.net/root/fav.png", m.faviconUrl)
        assertEquals("https://example.com/oembed?url=x&format=json", m.oembedUrl)
        assertEquals("https://static.example.net/root/cards/a.png", m.imageUrl)
    }

    @Test fun ignoresMarkupInsideScriptsAndComments() {
        val html = """<head>
            <script>var s = '<meta property="og:title" content="EVIL">';</script>
            <!-- <meta property="og:title" content="COMMENTED"> -->
            <meta property="og:title" content="Real"></head>"""
        assertEquals("Real", HtmlMetadataParser.parse(html, page).title)
    }

    @Test fun unsafeUrlsInMetadataAreDropped() {
        val html = """<meta property="og:image" content="javascript:alert(1)">
            <meta name="twitter:image" content="data:image/png;base64,AAAA">
            <link rel="alternate" type="application/json+oembed" href="file:///etc/passwd">"""
        val m = HtmlMetadataParser.parse(html, page)
        assertNull(m.imageUrl)
        assertNull(m.oembedUrl)
    }

    @Test fun capsLongFieldsAndStripsControls() {
        val long = "x".repeat(5000)
        val m = HtmlMetadataParser.parse("<meta property=\"og:title\" content=\"$long\"><meta property=\"og:description\" content=\"a\u0000b\"> ", page)
        assertEquals(HtmlMetadataParser.MAX_TITLE, m.title!!.length)
        assertTrue(m.title!!.endsWith("\u2026"))
        assertEquals("ab", m.description)
    }

    @Test fun unterminatedTagDoesNotCrash() {
        val m = HtmlMetadataParser.parse("<meta property=\"og:title\" content=\"unterminated", page)
        assertNull(m.title)
    }
}
