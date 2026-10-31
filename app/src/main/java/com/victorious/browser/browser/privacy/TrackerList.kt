package com.victorious.browser.browser.privacy

/**
 * Bundled 3rd-party tracker / ad host-suffix list for "Tracker & ad loading protection".
 *
 * Hand-curated by the Victorious project from publicly known vendor domains (no
 * third-party list is copied — notably NOT DuckDuckGo's Tracker Radar, which is
 * CC BY-NC-SA). Licensed with the app; see NOTICE. Deliberately modest: it covers
 * the most common ad / analytics / social / session-replay networks, not the long tail.
 *
 * Matching is host-suffix, applied only to THIRD-PARTY requests (request base domain
 * differs from the page's), so visiting e.g. facebook.com directly still works.
 */
enum class TrackerCategory(val label: String) {
    ADVERTISING("Advertising"),
    ANALYTICS("Analytics"),
    SOCIAL("Social network"),
    SESSION_REPLAY("Session replay / fingerprinting"),
    CRYPTOMINING("Cryptomining"),
}

object TrackerList {
    val HOSTS: Map<TrackerCategory, Set<String>> = mapOf(
        TrackerCategory.ADVERTISING to setOf(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
            "2mdn.net", "adnxs.com", "adsrvr.org", "rubiconproject.com", "pubmatic.com", "openx.net",
            "criteo.com", "criteo.net", "taboola.com", "outbrain.com", "amazon-adsystem.com",
            "media.net", "casalemedia.com", "indexww.com", "smartadserver.com", "advertising.com",
            "yieldmo.com", "sharethrough.com", "33across.com", "triplelift.com", "teads.tv",
            "moatads.com", "adform.net", "bidswitch.net", "contextweb.com", "lijit.com",
            "popads.net", "propellerads.com", "zedo.com", "revcontent.com", "mgid.com",
            "adroll.com", "quantserve.com", "bluekai.com", "demdex.net", "krxd.net",
            "rlcdn.com", "agkn.com", "mathtag.com", "turn.com", "tapad.com", "everesttech.net",
            "serving-sys.com", "flashtalking.com", "ads.yahoo.com", "ads-twitter.com", "ads.linkedin.com",
        ),
        TrackerCategory.ANALYTICS to setOf(
            "google-analytics.com", "googletagmanager.com", "analytics.google.com",
            "scorecardresearch.com", "comscore.com", "chartbeat.com", "chartbeat.net",
            "mixpanel.com", "segment.io", "segment.com", "amplitude.com", "heap.io", "heapanalytics.com",
            "omtrdc.net", "2o7.net", "newrelic.com", "nr-data.net", "parsely.com",
            "kissmetrics.com", "statcounter.com", "quantcount.com", "branch.io", "app-measurement.com",
            "adjust.com", "appsflyer.com", "kochava.com", "bat.bing.com", "clarity.ms",
        ),
        TrackerCategory.SOCIAL to setOf(
            "connect.facebook.net", "pixel.facebook.com", "analytics.twitter.com", "static.ads-twitter.com",
            "platform.twitter.com", "px.ads.linkedin.com", "snap.licdn.com", "analytics.tiktok.com",
            "ct.pinterest.com", "sc-static.net", "tr.snapchat.com", "addthis.com", "sharethis.com",
        ),
        TrackerCategory.SESSION_REPLAY to setOf(
            "hotjar.com", "hotjar.io", "fullstory.com", "mouseflow.com", "crazyegg.com",
            "luckyorange.com", "luckyorange.net", "smartlook.com", "inspectlet.com", "logrocket.com",
            "lr-ingest.io", "quantummetric.com", "contentsquare.net", "glassboxdigital.io",
            "fingerprintjs.com", "fpjs.io", "iovation.com", "threatmetrix.com",
        ),
        TrackerCategory.CRYPTOMINING to setOf(
            "coinhive.com", "coin-hive.com", "cryptoloot.pro", "jsecoin.com", "minero.cc", "webminepool.com",
        ),
    )

    /** Category of [host] if it (or a parent domain) is listed. */
    fun match(host: String?): TrackerCategory? {
        if (host.isNullOrBlank()) return null
        val h = host.lowercase().removeSuffix(".")
        for ((cat, hosts) in HOSTS) {
            if (hosts.any { HostUtil.matchesSuffix(h, it) }) return cat
        }
        return null
    }

    /**
     * Should a request to [requestHost] from page [pageHost] be blocked? Only 3rd-party
     * matches, and never when the page's site is on the [allowlist] (per-site exception).
     */
    fun shouldBlock(requestHost: String?, pageHost: String?, allowlist: Set<String>): TrackerCategory? {
        if (requestHost.isNullOrBlank() || pageHost.isNullOrBlank()) return null
        if (!HostUtil.isThirdParty(requestHost, pageHost)) return null
        if (allowlist.any { HostUtil.matchesSuffix(pageHost.lowercase(), it) }) return null
        return match(requestHost)
    }
}
