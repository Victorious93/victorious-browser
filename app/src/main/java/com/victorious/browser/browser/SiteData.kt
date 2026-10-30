package com.victorious.browser.browser

import java.net.URI

/**
 * Origin helpers and the JS used to wipe web storage for the *current* origin.
 * Pure Kotlin so [SiteDataTest] can cover the URL edge cases without Android.
 *
 * Clearing cookies / WebStorage origins is Android-side (CookieManager / WebStorage)
 * and lives next to the Compose sheet that calls those APIs.
 */
object SiteData {
    /**
     * RFC 6454 origin (`scheme://host[:port]`), omitting default ports.
     * Null when [url] is not a real http(s) page.
     */
    fun originOf(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null
        return try {
            val uri = URI(trimmed)
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

    /** JS: clear localStorage + sessionStorage on the page's own origin. */
    fun clearStorageScript(): String = """
        (function() {
            var err = [];
            try { localStorage.clear(); } catch (e) { err.push("localStorage: " + String(e)); }
            try { sessionStorage.clear(); } catch (e) { err.push("sessionStorage: " + String(e)); }
            return JSON.stringify({
                origin: location.origin || "",
                ok: err.length === 0,
                error: err.length ? err.join("; ") : null
            });
        })();
    """.trimIndent()
}
