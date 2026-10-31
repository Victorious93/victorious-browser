package com.victorious.browser.browser.privacy

import com.victorious.browser.browser.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FireproofTest {
    @Test fun normalizesAndMatchesSubdomains() {
        val s = FireproofLogic.add(emptySet(), "https://www.Example.com/path")
        assertEquals(setOf("example.com"), s)
        assertTrue(FireproofLogic.isFireproof("example.com", s))
        assertTrue(FireproofLogic.isFireproof("mail.example.com", s))
        assertFalse(FireproofLogic.isFireproof("notexample.com", s))
        assertFalse(FireproofLogic.isFireproof(null, s))
    }
    @Test fun removeWorks() = assertTrue(FireproofLogic.remove(setOf("a.com"), "A.com").isEmpty())
    @Test fun blankIgnored() = assertTrue(FireproofLogic.add(emptySet(), "  ").isEmpty())
}

class TrackingParamsTest {
    @Test fun stripsKnownKeepsOthers() {
        assertEquals("https://a.com/p?id=7#x", TrackingParams.strip("https://a.com/p?utm_source=x&id=7&fbclid=abc&gclid=1#x"))
    }
    @Test fun allTrackingDropsQuery() = assertEquals("https://a.com/p", TrackingParams.strip("https://a.com/p?utm_medium=m"))
    @Test fun untouchedWithoutTracking() {
        val u = "https://a.com/p?q=hello%20world&page=2"
        assertEquals(u, TrackingParams.strip(u))
    }
    @Test fun noQuery() = assertEquals("https://a.com/", TrackingParams.strip("https://a.com/"))
}

class GpcTest {
    @Test fun headerOnlyWhenEnabled() {
        assertEquals(mapOf("Sec-GPC" to "1"), Gpc.headers(true))
        assertTrue(Gpc.headers(false).isEmpty())
    }
    @Test fun scriptSetsFlag() = assertTrue(Gpc.script().contains("globalPrivacyControl"))
}

class HttpsUpgradeTest {
    @Test fun upgradesPublicHttp() = assertEquals("https://example.com/a?b=1", HttpsUpgrade.upgrade("http://example.com/a?b=1"))
    @Test fun leavesHttps() = assertNull(HttpsUpgrade.upgrade("https://example.com/"))
    @Test fun skipsLocalTargets() {
        listOf(
            "http://127.0.0.1/", "http://192.168.1.10/", "http://localhost:8080/", "http://router/",
            "http://printer.local/", "http://box.lan/", "http://example.com:8080/", "http://[::1]/",
        ).forEach { assertNull(it, HttpsUpgrade.upgrade(it)) }
    }
    @Test fun skipsFailedHosts() = assertNull(HttpsUpgrade.upgrade("http://old.example/", setOf("old.example")))
}

class FirePlanTest {
    private val fp = setOf("keep.com")
    @Test fun everywhereBurnsAllButFireproof() {
        val opts = FireOptions(FireScope.EVERYWHERE)
        assertEquals(listOf("https://keep.com"), FirePlan.originsToBurn(listOf("https://a.com", "https://keep.com"), fp, opts).let { b ->
            listOf("https://a.com", "https://keep.com") - b.toSet()
        })
        assertEquals(listOf("https://www.keep.com/x"), FirePlan.historyToKeep(listOf("https://a.com/", "https://www.keep.com/x"), fp, opts))
    }
    @Test fun keepFlags() {
        val opts = FireOptions(FireScope.EVERYWHERE, keepHistory = true, keepCookies = true)
        assertTrue(FirePlan.originsToBurn(listOf("https://a.com"), fp, opts).isEmpty())
        assertEquals(2, FirePlan.historyToKeep(listOf("https://a.com/", "https://b.com/"), fp, opts).size)
        assertFalse(FirePlan.burnSiteData("a.com", fp, opts))
    }
    @Test fun thisTabOnlyTouchesThatSite() {
        val opts = FireOptions(FireScope.THIS_TAB)
        assertEquals(listOf("https://a.com"), FirePlan.originsToBurn(listOf("https://a.com", "https://b.com"), fp, opts, "a.com"))
        assertEquals(listOf("https://b.com/"), FirePlan.historyToKeep(listOf("https://a.com/", "https://b.com/"), fp, opts, "a.com"))
    }
    @Test fun fireproofNeverBurned() = assertFalse(FirePlan.burnSiteData("sub.keep.com", fp, FireOptions()))
}

class TrackerListTest {
    @Test fun matchesSubdomains() {
        assertEquals(TrackerCategory.ADVERTISING, TrackerList.match("stats.g.doubleclick.net"))
        assertEquals(TrackerCategory.ANALYTICS, TrackerList.match("www.google-analytics.com"))
        assertNull(TrackerList.match("notdoubleclick.net"))
        assertNull(TrackerList.match("example.com"))
    }
    @Test fun thirdPartyOnlyAndAllowlist() {
        assertEquals(TrackerCategory.ADVERTISING, TrackerList.shouldBlock("ad.doubleclick.net", "news.com", emptySet()))
        assertNull(TrackerList.shouldBlock("ad.doubleclick.net", "www.doubleclick.net", emptySet()))
        assertNull(TrackerList.shouldBlock("ad.doubleclick.net", "news.com", setOf("news.com")))
    }
}

class ReferrerAndNavTest {
    @Test fun referrer() {
        assertEquals("https://a.com/x?y", ReferrerTrim.trim("https://a.com/x?y", "https://www.a.com/z"))
        assertEquals("https://a.com/", ReferrerTrim.trim("https://a.com/secret?t=1", "https://b.com/"))
        assertNull(ReferrerTrim.trim("https://a.com/", "http://b.com/"))
        assertNull(ReferrerTrim.trim(null, "https://b.com/"))
    }
    @Test fun navRewriteCombines() {
        val r = PrivacyNav.rewrite("http://example.com/?utm_source=x", stripTracking = true, forceHttps = true)
        assertEquals("https://example.com/", r.url)
        assertTrue(r.strippedParams && r.httpsUpgraded)
        val off = PrivacyNav.rewrite("http://example.com/?utm_source=x", false, false)
        assertEquals("http://example.com/?utm_source=x", off.url)
    }
    @Test fun bangs() {
        assertEquals("https://www.google.com/search?q=cats+dogs", Bangs.resolve("!g cats dogs"))
        assertEquals("https://en.wikipedia.org/w/index.php?search=Kotlin", Bangs.resolve("Kotlin !w"))
        assertNull(Bangs.resolve("!nope x"))
        assertNull(Bangs.resolve("plain query"))
    }
    @Test fun ddgIsDefaultSearch() {
        assertEquals(SearchEngine.DUCKDUCKGO, SearchEngine.DEFAULT)
        assertEquals(SearchEngine.DUCKDUCKGO, SearchEngine.fromName(null))
        assertEquals(SearchEngine.DUCKDUCKGO, SearchEngine.entries.first())
    }
}
