package com.victorious.browser.osint.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailChecksTest {
    @Test fun syntax() {
        assertTrue(EmailChecks.syntax("Alice.Smith+tag@Example.co.uk").valid)
        assertEquals("example.co.uk", EmailChecks.syntax("a@Example.co.uk").domain)
        assertFalse(EmailChecks.syntax("no-at-sign").valid)
        assertFalse(EmailChecks.syntax("a..b@example.com").valid)
        assertFalse(EmailChecks.syntax("a@localhost").valid)
        assertFalse(EmailChecks.syntax("a b@example.com").valid)
    }

    @Test fun gravatarHashesUseNormalizedAddress() {
        // Gravatar docs example address.
        assertEquals(EmailChecks.gravatarSha256("myemailaddress@example.com"), EmailChecks.gravatarSha256("  MyEmailAddress@example.com "))
        assertEquals("0bc83cb571cd1c50ba6f3e8a78ef1346", EmailChecks.gravatarMd5("MyEmailAddress@example.com "))
        assertEquals(64, EmailChecks.gravatarSha256("a@b.co").length)
        assertTrue(EmailChecks.gravatarCheckUrl("a@b.co").endsWith("?d=404&s=80"))
    }

    @Test fun spf() {
        val s = EmailChecks.parseSpf(listOf("google-site-verification=x", "v=spf1 include:_spf.google.com ~all"))
        assertEquals("~all", s.all)
        assertEquals(listOf("_spf.google.com"), s.includes)
        assertEquals("SPF soft-fail (~all)", s.summary)
        assertEquals("No SPF record", EmailChecks.parseSpf(emptyList()).summary)
        assertTrue(EmailChecks.parseSpf(listOf("v=spf1 -all", "v=spf1 ~all")).multiple)
        assertEquals("+all", EmailChecks.parseSpf(listOf("v=spf1 all")).all)
    }

    @Test fun dmarc() {
        val d = EmailChecks.parseDmarc(listOf("v=DMARC1; p=reject; rua=mailto:d@example.com; pct=100"))
        assertEquals("reject", d.policy)
        assertEquals("mailto:d@example.com", d.rua)
        assertEquals(100, d.pct)
        assertNull(EmailChecks.parseDmarc(listOf("v=spf1 -all")).record)
    }

    @Test fun mx() {
        val mx = EmailChecks.parseMx(listOf("20 alt1.aspmx.l.google.com.", "10 aspmx.l.google.com.", "garbage"))
        assertEquals(listOf(10, 20), mx.map { it.priority })
        assertEquals("aspmx.l.google.com", mx[0].host)
        assertEquals("Google Workspace / Gmail", EmailChecks.mailProvider(mx))
        assertNull(EmailChecks.mailProvider(emptyList()))
    }

    @Test fun disposable() {
        assertTrue(DisposableDomains.isDisposable("mailinator.com"))
        assertTrue(DisposableDomains.isDisposable("sub.Mailinator.com."))
        assertFalse(DisposableDomains.isDisposable("gmail.com"))
        assertTrue(DisposableDomains.isDisposable("x.example", setOf("x.example")))
        assertEquals(setOf("a.com", "b.org"), DisposableDomains.parseList("# list\na.com\n\nB.org # c\nnodot\n"))
    }

    @Test fun pivots() {
        val p = EmailChecks.pivots("A@Example.com")
        assertTrue(p.all { it.url.startsWith("https://") })
        assertTrue(p.any { it.url.contains("%22a%40example.com%22") })
    }
}
