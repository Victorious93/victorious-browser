package com.victorious.browser.linkembed

import com.victorious.browser.linkembed.EmbedRecon.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbedSnippetsTest {
    @Test fun markdownEscapesTitleAndUrl() {
        val s = EmbedSnippets.build("https://example.com/a_(b) c", "Evil](javascript:alert(1)) [x]\n*bold*", Verdict.EMBEDDABLE)!!
        assertEquals(
            "[Evil\\]\\(javascript:alert\\(1\\)\\) \\[x\\] \\*bold\\*](https://example.com/a_%28b%29%20c)",
            s.markdown,
        )
        // Exactly one unescaped "](" — the real link boundary.
        assertEquals(1, Regex("(?<!\\\\)\\]\\(").findAll(s.markdown).count())
    }

    @Test fun htmlLinkEscapesAttributesAndText() {
        val s = EmbedSnippets.build("https://example.com/?q=\"><script>", "<img src=x onerror=alert(1)> & 'q'", Verdict.UNKNOWN)!!
        assertEquals(
            "<a href=\"https://example.com/?q=&quot;&gt;&lt;script&gt;\" rel=\"noopener noreferrer\" target=\"_blank\">" +
                "&lt;img src=x onerror=alert(1)&gt; &amp; &#39;q&#39;</a>",
            s.htmlLink,
        )
        assertFalse(s.htmlLink.contains("<script"))
        assertFalse(s.htmlLink.contains("<img"))
    }

    @Test fun iframeOnlyWhenEmbeddable() {
        val ok = EmbedSnippets.build("https://example.com/v", "T", Verdict.EMBEDDABLE)!!
        assertNotNull(ok.iframe)
        assertTrue(ok.iframe!!.startsWith("<iframe src=\"https://example.com/v\""))
        assertTrue(ok.iframe!!.contains("sandbox=\""))
        assertNull(ok.iframeOmittedReason)
        for (v in listOf(Verdict.NOT_EMBEDDABLE, Verdict.CONDITIONAL, Verdict.UNKNOWN, null)) {
            val s = EmbedSnippets.build("https://example.com/v", "T", v)!!
            assertNull(s.iframe)
            assertNotNull(s.iframeOmittedReason)
            assertTrue(s.iframeOrFallback.startsWith("<!-- iframe omitted"))
            assertFalse(s.iframeOrFallback.contains("<iframe"))
        }
    }

    @Test fun iframeTitleCannotBreakAttribute() {
        val s = EmbedSnippets.build("https://example.com/v", "\" onload=\"alert(1)", Verdict.EMBEDDABLE)!!
        assertTrue(s.iframe!!.contains("title=\"&quot; onload=&quot;alert(1)\""))
    }

    @Test fun unsafeUrlYieldsNothing() {
        assertNull(EmbedSnippets.build("javascript:alert(1)", "x", Verdict.EMBEDDABLE))
        assertNull(EmbedSnippets.build("data:text/html,<b>", "x", Verdict.EMBEDDABLE))
    }

    @Test fun blankTitleFallsBackToUrl() {
        val s = EmbedSnippets.build("https://example.com/", "   ", Verdict.UNKNOWN)!!
        assertEquals("[https://example.com/](https://example.com/)", s.markdown)
    }

    @Test fun oembedHtmlCarriedVerbatim() {
        val html = "<blockquote class=\"x\">t</blockquote>"
        assertEquals(html, EmbedSnippets.build("https://example.com/", "t", Verdict.UNKNOWN, html)!!.oembedHtml)
        assertNull(EmbedSnippets.build("https://example.com/", "t", Verdict.UNKNOWN, " ")!!.oembedHtml)
    }
}
