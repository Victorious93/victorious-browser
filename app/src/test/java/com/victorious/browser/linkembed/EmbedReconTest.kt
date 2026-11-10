package com.victorious.browser.linkembed

import com.victorious.browser.linkembed.EmbedRecon.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbedReconTest {
    private fun v(vararg headers: Pair<String, String>, status: Int? = 200, error: String? = null): EmbedRecon.Report {
        val map = headers.groupBy({ it.first }, { it.second }) + ("Content-Type" to listOf("text/html"))
        return EmbedRecon.evaluate("https://t.example/", "https://t.example/", status, map, error = error)
    }

    @Test fun noHeadersIsEmbeddable() = assertEquals(Verdict.EMBEDDABLE, v().verdict)

    @Test fun xfoDeny() = assertEquals(Verdict.NOT_EMBEDDABLE, v("X-Frame-Options" to "DENY").verdict)
    @Test fun xfoSameOrigin() = assertEquals(Verdict.NOT_EMBEDDABLE, v("x-frame-options" to "sameorigin").verdict)
    @Test fun xfoAllowFromIsConditional() {
        val r = v("X-Frame-Options" to "ALLOW-FROM https://partner.example")
        assertEquals(Verdict.CONDITIONAL, r.verdict)
        assertTrue(r.reasons.any { it.contains("obsolete") })
    }
    @Test fun xfoConflictingBlocks() = assertEquals(Verdict.NOT_EMBEDDABLE, v("X-Frame-Options" to "DENY, SAMEORIGIN").verdict)
    @Test fun xfoTwoHeaderLinesConflicting() =
        assertEquals(Verdict.NOT_EMBEDDABLE, v("X-Frame-Options" to "SAMEORIGIN", "X-Frame-Options" to "ALLOWALL").verdict)
    @Test fun xfoGarbageIgnored() = assertEquals(Verdict.EMBEDDABLE, v("X-Frame-Options" to "bogus").verdict)

    @Test fun cspNone() = assertEquals(Verdict.NOT_EMBEDDABLE, v("Content-Security-Policy" to "default-src 'self'; frame-ancestors 'none'").verdict)
    @Test fun cspEmptyDirectiveIsNone() = assertEquals(Verdict.NOT_EMBEDDABLE, v("Content-Security-Policy" to "frame-ancestors;").verdict)
    @Test fun cspSelf() = assertEquals(Verdict.NOT_EMBEDDABLE, v("Content-Security-Policy" to "frame-ancestors 'self'").verdict)
    @Test fun cspStar() = assertEquals(Verdict.EMBEDDABLE, v("Content-Security-Policy" to "frame-ancestors *").verdict)
    @Test fun cspHttpsScheme() = assertEquals(Verdict.EMBEDDABLE, v("Content-Security-Policy" to "frame-ancestors https:").verdict)
    @Test fun cspListedOrigins() {
        val r = v("Content-Security-Policy" to "frame-ancestors 'self' https://partner.example https://*.trusted.example")
        assertEquals(Verdict.CONDITIONAL, r.verdict)
        assertTrue(r.reasons.any { it.contains("https://partner.example") })
    }
    @Test fun noneWithOtherSourcesIsIgnored() =
        assertEquals(Verdict.CONDITIONAL, v("Content-Security-Policy" to "frame-ancestors 'none' https://a.example").verdict)

    @Test fun cspOverridesXfo() {
        val r = v("X-Frame-Options" to "DENY", "Content-Security-Policy" to "frame-ancestors *")
        assertEquals(Verdict.EMBEDDABLE, r.verdict)
        assertTrue(r.reasons.any { it.contains("ignored") })
    }

    @Test fun cspWithoutFrameAncestorsFallsBackToXfo() =
        assertEquals(Verdict.NOT_EMBEDDABLE, v("Content-Security-Policy" to "default-src 'self'", "X-Frame-Options" to "SAMEORIGIN").verdict)

    @Test fun multiplePoliciesStrictestWins() {
        assertEquals(
            Verdict.NOT_EMBEDDABLE,
            v("Content-Security-Policy" to "frame-ancestors *", "Content-Security-Policy" to "frame-ancestors 'none'").verdict,
        )
        assertEquals(Verdict.NOT_EMBEDDABLE, v("Content-Security-Policy" to "frame-ancestors *, frame-ancestors 'self'").verdict)
        assertEquals(
            Verdict.CONDITIONAL,
            v("Content-Security-Policy" to "frame-ancestors *", "Content-Security-Policy" to "frame-ancestors https://a.example").verdict,
        )
    }

    @Test fun reportOnlyIsNotEnforced() {
        val r = v("Content-Security-Policy-Report-Only" to "frame-ancestors 'none'")
        assertEquals(Verdict.EMBEDDABLE, r.verdict)
        assertEquals("'none'", r.reportOnlyFrameAncestors)
        assertNull(r.frameAncestors)
    }

    @Test fun directiveNamePrefixNotConfused() =
        assertEquals(Verdict.EMBEDDABLE, v("Content-Security-Policy" to "frame-ancestors-x 'none'; frame-src 'none'").verdict)

    @Test fun fetchErrorIsUnknown() = assertEquals(Verdict.UNKNOWN, v(status = null, error = "timeout").verdict)

    @Test fun errorStatusDowngradesPositiveVerdictToUnknown() {
        assertEquals(Verdict.UNKNOWN, v(status = 404).verdict)
        assertEquals(Verdict.UNKNOWN, v("Content-Security-Policy" to "frame-ancestors *", status = 500).verdict)
        // A refusal is still reported as such (with a note), even on an error page.
        assertEquals(Verdict.NOT_EMBEDDABLE, v("X-Frame-Options" to "DENY", status = 403).verdict)
    }

    @Test fun reportFieldsPopulated() {
        val r = v("X-Frame-Options" to "DENY", "Content-Security-Policy" to "script-src 'self'; frame-ancestors   https://a.example")
        assertEquals("DENY", r.xFrameOptions)
        assertEquals("https://a.example", r.frameAncestors)
        assertEquals("text/html", r.contentType)
        assertEquals(200, r.status)
    }

    @Test fun disclaimerIsHonest() {
        assertTrue(EmbedRecon.DISCLAIMER.contains("not legal permission"))
        assertTrue(EmbedRecon.DISCLAIMER.contains("does not guarantee"))
    }
}
