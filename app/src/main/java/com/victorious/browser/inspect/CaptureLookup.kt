package com.victorious.browser.inspect

import java.net.URI

/**
 * Pick a captured exchange to seed Modify / Replay from the current page.
 * Prefers an exact URL match, then the same origin; never a different host.
 */
object CaptureLookup {

    fun latestForPage(exchanges: List<CapturedExchange>, pageUrl: String): CapturedExchange? {
        if (exchanges.isEmpty()) return null
        val url = pageUrl.trim()
        if (url.isEmpty()) return null

        exchanges.lastOrNull { it.url == url }?.let { return it }
        val noFrag = stripFragment(url)
        if (noFrag != url) {
            exchanges.lastOrNull { stripFragment(it.url) == noFrag }?.let { return it }
        }
        val origin = originOf(url) ?: return null
        return exchanges.lastOrNull { originOf(it.url) == origin }
    }

    internal fun originOf(url: String): String? {
        return try {
            val uri = URI(url.trim())
            val scheme = uri.scheme?.lowercase() ?: return null
            if (scheme != "http" && scheme != "https") return null
            val host = uri.host?.lowercase() ?: return null
            val port = uri.port
            val defaultPort =
                (scheme == "http" && port == 80) || (scheme == "https" && port == 443)
            if (port == -1 || defaultPort) "$scheme://$host" else "$scheme://$host:$port"
        } catch (_: Exception) {
            null
        }
    }

    internal fun stripFragment(url: String): String {
        val hash = url.indexOf('#')
        return if (hash >= 0) url.substring(0, hash) else url
    }
}
