package com.victorious.browser.linkembed

import com.victorious.browser.linkembed.EmbedRecon.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbedViewerHtmlTest {
    private fun preview(url: String, headers: Map<String, List<String>> = emptyMap(), oembed: OEmbedData? = null, status: Int? = 200) =
        LinkPreview(
            url, url, null, oembed, EmbedRecon.evaluate(url, url, status, headers + ("Content-Type" to listOf("text/html"))),
            null, null, null, false, null, 0,
        )

    @Test fun providerIframeUsesOwnOriginSandbox() {
        val o = OEmbedData("video", html = "<iframe src=\"https://www.youtube.com/embed/abc\"></iframe>")
        val plan = EmbedViewerHtml.plan(preview("https://www.youtube.com/watch?v=abc", mapOf("X-Frame-Options" to listOf("SAMEORIGIN")), o))
        assertEquals(EmbedViewerHtml.Plan.FrameUrl("https://www.youtube.com/embed/abc", viaOEmbed = true), plan)
        val doc = EmbedViewerHtml.document(plan, "t")!!
        assertTrue(doc.contains("sandbox=\"allow-scripts allow-same-origin allow-presentation allow-popups\""))
        assertFalse(doc.contains("allow-top-navigation"))
        assertTrue(doc.contains("Content-Security-Policy"))
    }

    @Test fun arbitraryProviderHtmlIsOpaqueSrcdoc() {
        val evil = "<script>alert(document.cookie)</script>\"><img src=x onerror=alert(1)>"
        val plan = EmbedViewerHtml.plan(preview("https://x.example/", oembed = OEmbedData("rich", html = evil)))
        assertTrue(plan is EmbedViewerHtml.Plan.ProviderHtml)
        val doc = EmbedViewerHtml.document(plan, "t")!!
        assertTrue(doc.contains("sandbox=\"allow-scripts allow-popups\""))
        // Provider markup only exists escaped inside srcdoc, never as live markup in the wrapper.
        assertFalse(doc.contains("<script"))
        assertFalse(doc.contains("<img"))
        assertTrue(doc.contains("&lt;script&gt;"))
        assertFalse(Regex("srcdoc=\"[^\"]*allow-same-origin").containsMatchIn(doc))
    }

    @Test fun embeddablePageFramed() {
        val plan = EmbedViewerHtml.plan(preview("https://ok.example/"))
        assertEquals(EmbedViewerHtml.Plan.FrameUrl("https://ok.example/", viaOEmbed = false), plan)
    }

    @Test fun refusedConditionalUnknownAndHttpNotFramed() {
        listOf(
            preview("https://no.example/", mapOf("X-Frame-Options" to listOf("DENY"))),
            preview("https://cond.example/", mapOf("Content-Security-Policy" to listOf("frame-ancestors https://a.example"))),
            preview("https://unk.example/", status = 500),
            preview("http://plain.example/"),
        ).forEach { p ->
            val plan = EmbedViewerHtml.plan(p)
            assertTrue(plan.toString(), plan is EmbedViewerHtml.Plan.NotEmbeddable)
            assertNull(EmbedViewerHtml.document(plan, "t"))
        }
        assertEquals(Verdict.UNKNOWN, preview("https://unk.example/", status = 500).recon.verdict)
    }

    @Test fun wrapperEscapesTitleAndSrc() {
        val doc = EmbedViewerHtml.document(EmbedViewerHtml.Plan.FrameUrl("https://ok.example/?a=\"><b>", false), "\"><script>x</script>")!!
        assertFalse(doc.contains("<script"))
        assertFalse(doc.contains("<b>"))
    }

    @Test fun cspHasNoInlineScriptOrSameOriginEscape() {
        assertTrue(EmbedViewerHtml.CSP.startsWith("default-src 'none'"))
        assertFalse(EmbedViewerHtml.CSP.contains("unsafe-eval"))
        assertFalse(Regex("script-src[^;]*'unsafe-inline'").containsMatchIn(EmbedViewerHtml.CSP))
    }
}
