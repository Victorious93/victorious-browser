package com.victorious.browser.antiphish

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainAnalyzerTest {

    @Test
    fun isIpLiteral_v4_and_rejectHost() {
        assertTrue(DomainAnalyzer.isIpLiteral("192.168.1.1"))
        assertTrue(DomainAnalyzer.isIpLiteral("8.8.8.8"))
        assertFalse(DomainAnalyzer.isIpLiteral("example.com"))
        assertFalse(DomainAnalyzer.isIpLiteral("999.1.1.1"))
        assertFalse(DomainAnalyzer.isIpLiteral("1.2.3"))
    }

    @Test
    fun punycode_and_unicode() {
        // xn--e1awd7f = пример (Cyrillic "primer"); we only need ACE detection
        assertTrue(DomainAnalyzer.isPunycode("xn--e1awd7f.com"))
        assertFalse(DomainAnalyzer.isPunycode("example.com"))
        val uni = DomainAnalyzer.toUnicode("xn--e1awd7f.com")
        assertTrue(uni != "xn--e1awd7f.com" || DomainAnalyzer.hasNonAscii(uni) || uni.contains("."))
        assertTrue(DomainAnalyzer.hasNonAscii(uni) || uni.startsWith("xn--").not())
    }

    @Test
    fun mixedScript_cyrillicHomoglyph() {
        // Latin 'a' + Cyrillic 'а' (U+0430) in same label
        val mixed = "p\u0430ypal.com" // p + Cyrillic a + ypal
        assertTrue(DomainAnalyzer.hasMixedScriptLabel(mixed))
        assertFalse(DomainAnalyzer.hasMixedScriptLabel("paypal.com"))
    }

    @Test
    fun skeleton_foldsConfusables() {
        val skel = DomainAnalyzer.skeleton("p\u0430ypal.com") // Cyrillic а
        assertEquals(DomainAnalyzer.skeleton("paypal.com"), skel)
    }

    @Test
    fun registrableDomain_multiSuffix() {
        assertEquals("example.com", DomainAnalyzer.registrableDomain("www.example.com"))
        assertEquals("example.co.uk", DomainAnalyzer.registrableDomain("a.b.example.co.uk"))
        assertEquals("example.com", DomainAnalyzer.registrableDomain("example.com"))
    }

    @Test
    fun levenshtein_basic() {
        assertEquals(0, DomainAnalyzer.levenshtein("paypal", "paypal"))
        assertEquals(1, DomainAnalyzer.levenshtein("paypal", "paypa1"))
        assertEquals(2, DomainAnalyzer.levenshtein("google", "googel")) // transpose = 2 edits
        assertEquals(3, DomainAnalyzer.levenshtein("kitten", "sitting"))
    }

    @Test
    fun lookalike_typosquat_and_embed() {
        val known = listOf("paypal.com", "google.com", "acmepay.com")
        assertNull(DomainAnalyzer.findLookalike("paypal.com", known))
        assertNull(DomainAnalyzer.findLookalike("login.paypal.com", known))

        val typo = DomainAnalyzer.findLookalike("paypa1.com", known)
        assertNotNull(typo)
        assertEquals("paypal.com", typo!!.knownDomain)

        val embed = DomainAnalyzer.findLookalike("paypal-secure.top", known)
        assertNotNull(embed)
        assertEquals("paypal.com", embed!!.knownDomain)

        val skel = DomainAnalyzer.findLookalike("p\u0430ypal.com", known)
        assertNotNull(skel)
        assertTrue(skel!!.viaSkeleton)
    }

    @Test
    fun structuralFlags_deepAndHyphen() {
        val flags = DomainAnalyzer.structuralFlags("a-b-c-d-e.login.verify.secure.example.xyz")
        assertTrue(flags.any { it.contains("hyphen", ignoreCase = true) || it.contains("nested", ignoreCase = true) || it.contains("long", ignoreCase = true) })
    }

    @Test
    fun suspiciousTld_listed() {
        assertTrue("xyz" in DomainAnalyzer.SUSPICIOUS_TLDS)
        assertTrue("top" in DomainAnalyzer.SUSPICIOUS_TLDS)
        assertEquals("xyz", DomainAnalyzer.tld("foo.bar.xyz"))
    }
}
