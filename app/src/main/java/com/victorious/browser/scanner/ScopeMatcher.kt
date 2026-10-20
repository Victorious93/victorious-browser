package com.victorious.browser.scanner

import java.net.URI

/**
 * Scope allowlist matching for the active toolset.
 *
 * All intrusive scanner actions are gated behind [inScope]: a request is only
 * ever replayed with payloads when its host matches a host the user explicitly
 * added to [ScannerConfig.scopeHosts]. Pure logic, no Android types, so it is
 * fully unit-tested in [ScopeMatcherTest].
 *
 * Entry forms accepted in the allowlist:
 *  - exact host: `example.com` (matches `example.com` only)
 *  - wildcard:   `*.example.com` (matches any subdomain, and the apex)
 * Ports are ignored for matching. Matching is case-insensitive.
 */
object ScopeMatcher {

    /** Lowercase host without port, or null if it cannot be parsed. */
    fun hostOf(url: String): String? {
        return try {
            val h = URI(url).host ?: return null
            h.lowercase()
        } catch (_: Exception) {
            null
        }
    }

    /** Normalize a user-entered scope entry: trim, lowercase, strip scheme/port/path. */
    fun normalizeEntry(raw: String): String {
        var s = raw.trim().lowercase()
        if (s.isEmpty()) return s
        // Allow users to paste a full URL as a scope entry.
        if (s.contains("://")) {
            s = hostOf(s) ?: s.substringAfter("://")
        }
        s = s.substringBefore('/')
        // Strip a trailing port, but keep a leading "*." wildcard marker.
        s = s.substringBefore(':')
        return s
    }

    /** True when [host] matches the single [entry] (already normalized or not). */
    fun hostMatchesEntry(host: String, entry: String): Boolean {
        val h = host.trim().lowercase()
        val e = normalizeEntry(entry)
        if (h.isEmpty() || e.isEmpty()) return false
        if (e.startsWith("*.")) {
            val base = e.substring(2)
            return h == base || h.endsWith(".$base")
        }
        return h == e
    }

    /** True when [host] matches any allowlist entry. */
    fun hostInScope(host: String?, allow: Collection<String>): Boolean {
        if (host == null) return false
        return allow.any { hostMatchesEntry(host, it) }
    }

    /** True when the URL's host is in scope. The main gate for intrusive actions. */
    fun inScope(url: String, allow: Collection<String>): Boolean =
        hostInScope(hostOf(url), allow)
}
