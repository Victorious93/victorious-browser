package com.victorious.browser.antiphish

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhishingScorerTest {

    private val known = listOf("paypal.com", "google.com", "northbank.com", "acmepay.com")
    private val keywords = mapOf(
        "paypal" to "paypal.com",
        "google" to "google.com",
        "northbank" to "northbank.com",
    )

    @Test
    fun cleanHttpsBrand_lowScore() {
        val r = PhishingScorer.score(
            pageUrl = "https://www.paypal.com/signin",
            https = true,
            pageTitle = "PayPal",
            forms = listOf(PageFormSnapshot(hasPasswordField = true, actionHost = "www.paypal.com")),
            knownGood = known,
            brandKeywords = keywords,
        )
        assertTrue(r.score < PhishingScorer.RISK_THRESHOLD)
        assertFalse(r.isRisky)
    }

    @Test
    fun lookalikePlusCrossDomain_isRisky() {
        val r = PhishingScorer.score(
            pageUrl = "https://paypal-secure.top/login",
            https = true,
            pageTitle = "PayPal Login",
            forms = listOf(
                PageFormSnapshot(
                    hasPasswordField = true,
                    hasUsernameField = true,
                    actionHost = "harvest.xyz",
                    actionUrl = "https://harvest.xyz/c",
                ),
            ),
            knownGood = known,
            brandKeywords = keywords,
        )
        assertTrue(r.isRisky)
        assertTrue(r.score >= PhishingScorer.RISK_THRESHOLD)
        assertTrue(r.reasons.any { it.code == "lookalike" })
        assertTrue(r.reasons.any { it.code == "cross_domain_action" })
        assertTrue(r.reasons.any { it.code == "suspicious_tld" || it.code == "brand_mismatch" })
    }

    @Test
    fun httpPasswordOnIp_isRisky() {
        val r = PhishingScorer.score(
            pageUrl = "http://203.0.113.50/login",
            https = false,
            forms = listOf(PageFormSnapshot(hasPasswordField = true, hasUsernameField = true)),
            knownGood = known,
            brandKeywords = keywords,
        )
        assertTrue(r.isRisky)
        assertTrue(r.reasons.any { it.code == "insecure_password" })
        assertTrue(r.reasons.any { it.code == "ip_literal_creds" })
    }

    @Test
    fun emptyHost_zero() {
        val r = PhishingScorer.score(pageUrl = "not-a-url", https = true)
        assertEquals(0, r.score)
        assertEquals(PhishingScorer.RiskLevel.NONE, r.level)
    }

    @Test
    fun scoreCappedAt100() {
        val r = PhishingScorer.score(
            pageUrl = "http://paypal-secure-login-verify.xyz/x",
            https = false,
            pageTitle = "PayPal Google NorthBank urgent verify",
            forms = listOf(
                PageFormSnapshot(
                    hasPasswordField = true,
                    hasUsernameField = true,
                    hasHiddenCredentialField = true,
                    passwordFieldCount = 3,
                    actionHost = "evil.top",
                ),
            ),
            bodyBrandHints = listOf("PayPal", "Google"),
            knownGood = known,
            brandKeywords = keywords,
        )
        assertTrue(r.score <= PhishingScorer.SCORE_MAX)
        assertTrue("score was ${r.score} reasons=${r.reasons}", r.isRisky)
        assertTrue(r.level != PhishingScorer.RiskLevel.NONE)
    }

    @Test
    fun hostOnly_stripsSecrets() {
        assertEquals("evil.example", AntiPhishReportLog.hostOnly("https://evil.example/login?token=SECRET&pwd=x"))
        assertEquals("", AntiPhishReportLog.hostOnly(""))
    }

    @Test
    fun reportJson_roundTrip() {
        val entries = listOf(
            AntiPhishReportLog.ReportEntry(
                timestampMs = 1L,
                host = "evil.top",
                score = 70,
                level = "HIGH",
                reasons = listOf("lookalike: x"),
            ),
        )
        val json = AntiPhishReportLog.toJson(entries)
        val back = AntiPhishReportLog.fromJson(json)
        assertEquals(1, back.size)
        assertEquals("evil.top", back[0].host)
        assertEquals(70, back[0].score)
        assertFalse(json.contains("password", ignoreCase = true))
        assertFalse(json.contains("SECRET"))
    }
}
