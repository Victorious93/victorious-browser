package com.victorious.browser.browser

/**
 * Pure routing for non-http(s) navigations ("open in another app" links).
 *
 * The WebView only ever hands us a URL string; deciding what to DO with it is
 * pure Kotlin so every branch is unit-tested ([ExternalSchemeTest]) without
 * Android. The actual [android.content.Intent] launch / sanitising lives in the
 * Android glue (see TabbedBrowserScreen), which calls [route] first.
 *
 * Security posture:
 *  - http/https never reach here (handled inside the WebView) → [Decision.InApp].
 *  - javascript:, file:, content:, data:, about: from a *page* are refused outright
 *    ([Decision.Blocked]) — these are classic WebView escape / exfil vectors.
 *  - A short allowlist of everyday schemes (mailto, tel, sms, geo, market, intent,
 *    and store/app-link helpers) is eligible to open another app ([Decision.External]).
 *  - Everything else (unknown custom schemes like myapp://) requires an explicit
 *    "Open in app?" confirmation ([Decision.Confirm]); it is never launched silently.
 */
object ExternalScheme {

    sealed interface Decision {
        /** http/https (or protocol-relative) — load inside the WebView, don't leave. */
        data object InApp : Decision

        /** Dangerous scheme coming from web content — refuse. [reason] is user-facing. */
        data class Blocked(val scheme: String, val reason: String) : Decision

        /** A known everyday scheme that may open another app directly. */
        data class External(val scheme: String) : Decision

        /** Unknown custom scheme — ask the user before launching anything. */
        data class Confirm(val scheme: String) : Decision
    }

    /** Schemes we refuse to act on when they come from page content. */
    val BLOCKED_SCHEMES = setOf("javascript", "file", "content", "data", "about", "blob")

    /** Everyday schemes allowed to launch another app from a link tap. */
    val EXTERNAL_SCHEMES =
        setOf("mailto", "tel", "sms", "smsto", "mms", "mmsto", "geo", "market", "intent", "tg", "whatsapp")

    /** Lowercased scheme of [url] (before the first ':'), or null when there isn't one. */
    fun schemeOf(url: String): String? {
        val trimmed = url.trim()
        val colon = trimmed.indexOf(':')
        if (colon <= 0) return null
        val scheme = trimmed.substring(0, colon)
        // A scheme is ALPHA *( ALPHA / DIGIT / "+" / "-" / "." ) per RFC 3986.
        if (!scheme.first().isLetter()) return null
        if (!scheme.all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }) return null
        return scheme.lowercase()
    }

    fun route(url: String): Decision {
        val scheme = schemeOf(url) ?: return Decision.InApp // relative / no scheme → stay in WebView
        return when {
            scheme == "http" || scheme == "https" -> Decision.InApp
            scheme in BLOCKED_SCHEMES -> Decision.Blocked(
                scheme,
                "Blocked a $scheme: link (pages aren't allowed to open these).",
            )
            scheme in EXTERNAL_SCHEMES -> Decision.External(scheme)
            else -> Decision.Confirm(scheme)
        }
    }
}

/**
 * Pure helpers for `intent:` URIs. The real parse is [android.content.Intent.parseUri]
 * (Android-only); the pieces we can reason about as plain text — the fallback URL and
 * whether a parsed target is safe to keep — are extracted here so they're unit-tested.
 */
object IntentUri {
    /**
     * The `S.browser_fallback_url` extra embedded in an `intent:` URI fragment, if present
     * and itself an http(s) URL. Chrome's documented escape hatch when no app handles the
     * intent. Returns null when absent or not http(s).
     */
    fun browserFallbackUrl(intentUri: String): String? {
        val hash = intentUri.indexOf('#')
        if (hash < 0) return null
        val fragment = intentUri.substring(hash + 1)
        val key = "S.browser_fallback_url="
        for (part in fragment.split(';')) {
            if (part.startsWith(key)) {
                val raw = part.substring(key.length)
                val decoded = runCatching { java.net.URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
                val lower = decoded.lowercase()
                return if (lower.startsWith("http://") || lower.startsWith("https://")) decoded else null
            }
        }
        return null
    }

    /**
     * True when a resolved target URL (after Intent.parseUri gave us its data/scheme) is
     * one we must never hand back to the WebView or relaunch as a VIEW — the same escape
     * vectors [ExternalScheme.BLOCKED_SCHEMES] covers.
     */
    fun isUnsafeTarget(targetUrl: String?): Boolean {
        val scheme = targetUrl?.let { ExternalScheme.schemeOf(it) } ?: return false
        return scheme in ExternalScheme.BLOCKED_SCHEMES
    }
}
