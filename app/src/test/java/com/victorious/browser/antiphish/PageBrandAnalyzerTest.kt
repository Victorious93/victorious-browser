package com.victorious.browser.antiphish

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageBrandAnalyzerTest {

    private val keywords = mapOf("paypal" to "paypal.com", "northbank" to "northbank.com")
    private val known = listOf("paypal.com", "northbank.com")

    @Test
    fun brandMismatch_titleClaimsBrandOnWrongHost() {
        val findings = PageBrandAnalyzer.analyze(
            host = "secure-login.top",
            pageTitle = "PayPal — Sign in",
            bodyBrandHints = listOf("PayPal logo"),
            brandKeywords = keywords,
            knownGoodDomains = known,
        )
        assertTrue(findings.any { it.code == "brand_mismatch" })
    }

    @Test
    fun noMismatch_onRealDomain() {
        val findings = PageBrandAnalyzer.analyze(
            host = "www.paypal.com",
            pageTitle = "PayPal — Sign in",
            brandKeywords = keywords,
            knownGoodDomains = known,
        )
        assertTrue(findings.isEmpty())
    }

    @Test
    fun noMismatch_unrelatedTitle() {
        val findings = PageBrandAnalyzer.analyze(
            host = "example.com",
            pageTitle = "Welcome to Example",
            brandKeywords = keywords,
            knownGoodDomains = known,
        )
        assertFalse(findings.any { it.code == "brand_mismatch" })
    }
}
