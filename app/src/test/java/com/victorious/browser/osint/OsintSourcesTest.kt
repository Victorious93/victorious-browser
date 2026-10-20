package com.victorious.browser.osint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OsintSourcesTest {

    @Test
    fun classify_domain_ip_email_username_url() {
        assertEquals(OsintSources.TargetType.DOMAIN, OsintSources.classify("example.com"))
        assertEquals(OsintSources.TargetType.DOMAIN, OsintSources.classify("sub.target.co.uk"))
        assertEquals(OsintSources.TargetType.IP, OsintSources.classify("8.8.8.8"))
        assertEquals(OsintSources.TargetType.EMAIL, OsintSources.classify("alice@example.com"))
        assertEquals(OsintSources.TargetType.USERNAME, OsintSources.classify("victorious93"))
        assertEquals(OsintSources.TargetType.URL, OsintSources.classify("https://example.com/path"))
        assertEquals(OsintSources.TargetType.UNKNOWN, OsintSources.classify(""))
        assertEquals(OsintSources.TargetType.UNKNOWN, OsintSources.classify("!!!"))
    }

    @Test
    fun hostOf_stripsUrl() {
        assertEquals("example.com", OsintSources.hostOf("https://user@example.com:8443/a?x=1", OsintSources.TargetType.URL))
        assertEquals("example.com", OsintSources.hostOf("example.com", OsintSources.TargetType.DOMAIN))
    }

    @Test
    fun sourcesFor_domain_includesCoreSet() {
        val urls = OsintSources.sourcesFor("example.com").associate { it.source.id to it.url }
        assertTrue(urls.containsKey("crtsh"))
        assertTrue(urls["crtsh"]!!.contains("crt.sh"))
        assertTrue(urls.containsKey("dns_google"))
        assertTrue(urls.containsKey("wayback"))
        assertTrue(urls.containsKey("shodan"))
        assertTrue(urls.containsKey("censys"))
        assertTrue(urls.containsKey("google_dork"))
        assertTrue(urls.containsKey("github_dork"))
        assertTrue(urls.containsKey("whois"))
        assertTrue(urls.containsKey("virustotal"))
        assertTrue(urls.containsKey("reverse_ip"))
        // people-only sources should not apply to a domain
        assertFalse(urls.containsKey("whatsmyname"))
        assertFalse(urls.containsKey("epieos"))
    }

    @Test
    fun sourcesFor_ip_usesHostPages() {
        val urls = OsintSources.sourcesFor("1.2.3.4").associate { it.source.id to it.url }
        assertEquals("https://www.shodan.io/host/1.2.3.4", urls["shodan"])
        assertTrue(urls["censys"]!!.contains("/hosts/1.2.3.4"))
        assertTrue(urls["virustotal"]!!.contains("/ip-address/1.2.3.4"))
        assertFalse(urls.containsKey("crtsh")) // certs need a domain
    }

    @Test
    fun sourcesFor_username_and_email() {
        val user = OsintSources.sourcesFor("victorious93").associate { it.source.id to it.url }
        assertTrue(user.containsKey("whatsmyname"))
        assertTrue(user.containsKey("hibp"))
        assertTrue(user.containsKey("github_dork"))
        assertFalse(user.containsKey("crtsh"))

        val email = OsintSources.sourcesFor("alice@example.com").associate { it.source.id to it.url }
        assertTrue(email.containsKey("epieos"))
        assertTrue(email.containsKey("hibp"))
        assertTrue(email.containsKey("email_google_dork"))
        assertTrue(email.containsKey("github_dork"))
    }

    @Test
    fun sourcesFor_unknown_isEmpty() {
        assertTrue(OsintSources.sourcesFor("").isEmpty())
        assertTrue(OsintSources.sourcesFor("!!!").isEmpty())
    }

    @Test
    fun allSourcesHaveStableIds() {
        val ids = OsintSources.sources.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.containsAll(listOf(
            "crtsh", "dns_google", "dns_records", "reverse_ip", "wayback",
            "shodan", "censys", "google_dork", "github_dork", "whois",
            "virustotal", "whatsmyname", "epieos", "hibp", "email_google_dork",
        )))
    }
}
