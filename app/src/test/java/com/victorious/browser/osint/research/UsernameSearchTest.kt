package com.victorious.browser.osint.research

import com.victorious.browser.osint.research.UsernameSearch.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UsernameSearchTest {
    private val json = """{
      "${'$'}schema": "data.schema.json",
      "Code": {"url":"https://code.example/{}","urlMain":"https://code.example/","errorType":"status_code","regexCheck":"^[a-z]+$"},
      "Forum": {"url":"https://forum.example/u/{}","errorType":"message","errorMsg":["No such user","Gone"]},
      "Redir": {"url":"https://r.example/{}","urlProbe":"https://api.r.example/{}","errorType":"response_url"},
      "Weird": {"url":"https://w.example/{}","errorType":"something_else"},
      "Broken": {"url":"ftp://x/{}","errorType":"status_code"},
      "NoSlot": {"url":"https://n.example/","errorType":"status_code"}
    }"""
    private val sites = UsernameSearch.parseSites(json)
    private fun site(n: String) = sites.first { it.name == n }

    @Test fun parseDropsBadEntries() {
        assertEquals(listOf("Code", "Forum", "Redir", "Weird"), sites.map { it.name })
        assertEquals(listOf("No such user", "Gone"), site("Forum").errorMessages)
        assertEquals("https://api.r.example/bob", site("Redir").probeUrl("bob"))
        assertEquals("https://r.example/bob", site("Redir").profileUrl("bob"))
        assertEquals("https://forum.example/u/", site("Forum").urlMain)
    }

    @Test fun regexCheck() {
        assertTrue(site("Code").accepts("bob"))
        assertFalse(site("Code").accepts("Bob_1"))
        assertTrue(site("Forum").accepts("anything"))
    }

    @Test fun statusCode() {
        assertEquals(Verdict.FOUND, UsernameSearch.evaluate(site("Code"), 200, "<html>", false, null).first)
        assertEquals(Verdict.NOT_FOUND, UsernameSearch.evaluate(site("Code"), 404, "", false, null).first)
        assertEquals(Verdict.UNKNOWN, UsernameSearch.evaluate(site("Code"), 302, "", false, null).first)
    }

    @Test fun message() {
        assertEquals(Verdict.NOT_FOUND, UsernameSearch.evaluate(site("Forum"), 200, "Sorry: No such user here", false, null).first)
        assertEquals(Verdict.FOUND, UsernameSearch.evaluate(site("Forum"), 200, "Profile of bob", false, null).first)
    }

    @Test fun responseUrl() {
        assertEquals(Verdict.NOT_FOUND, UsernameSearch.evaluate(site("Redir"), 200, "", true, null).first)
        assertEquals(Verdict.FOUND, UsernameSearch.evaluate(site("Redir"), 200, "", false, null).first)
    }

    @Test fun unclearSignalsAreUnknown() {
        val s = site("Code")
        assertEquals(Verdict.UNKNOWN, UsernameSearch.evaluate(s, 429, "", false, null).first)
        assertEquals(Verdict.UNKNOWN, UsernameSearch.evaluate(s, 403, "", false, null).first)
        assertEquals(Verdict.UNKNOWN, UsernameSearch.evaluate(s, 503, "", false, null).first)
        assertEquals(Verdict.UNKNOWN, UsernameSearch.evaluate(s, null, "", false, "Timed out").first)
        assertEquals(Verdict.UNKNOWN, UsernameSearch.evaluate(s, 200, "<title>Just a moment...</title>", false, null).first)
        assertEquals(Verdict.UNKNOWN, UsernameSearch.evaluate(site("Weird"), 200, "", false, null).first)
    }

    @Test fun searchableAndSummary() {
        assertTrue(UsernameSearch.isSearchable("john.doe-99"))
        assertFalse(UsernameSearch.isSearchable("john doe"))
        assertFalse(UsernameSearch.isSearchable("../etc")) // slash is rejected
        val checks = listOf(
            UsernameSearch.Check(site("Code"), Verdict.FOUND, "u", ""),
            UsernameSearch.Check(site("Forum"), Verdict.UNKNOWN, "u", ""),
        )
        val sum = UsernameSearch.summarize(checks)
        assertEquals(1, sum[Verdict.FOUND]); assertEquals(1, sum[Verdict.UNKNOWN]); assertEquals(0, sum[Verdict.NOT_FOUND])
    }

    @Test fun bundledAssetParsesAndIsHttps() {
        val f = listOf("src/main/assets/osint/username_sites.json", "app/src/main/assets/osint/username_sites.json").map(::File).first { it.exists() }
        val bundled = UsernameSearch.parseSites(f.readText())
        assertTrue(bundled.size >= 20)
        bundled.forEach {
            assertTrue(it.name, it.urlTemplate.startsWith("https://"))
            assertTrue(it.name, it.errorType != UsernameSearch.ErrorType.UNSUPPORTED)
        }
    }
}
