package com.victorious.browser.export

import java.net.URI

/**
 * Pure policy for user-supplied endpoints that receive secrets or evidence (fleet export
 * token, Headscale API key, msfrpcd password). The WebView must stay cleartext-capable for
 * recon, but these app-originated calls should not send credentials over plain http across
 * an untrusted network. Cleartext is accepted only to loopback, private LAN, CGNAT
 * (Tailscale/Headscale 100.64.0.0/10), link-local, single-label and common LAN/mesh names.
 * No DNS lookups are made: a name is judged by its text only.
 */
object EndpointSecurity {
    private val PRIVATE_SUFFIXES = listOf(".local", ".lan", ".internal", ".home", ".home.arpa", ".test", ".ts.net")
    private val IPV4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

    fun isPrivateHost(rawHost: String?): Boolean {
        val h = rawHost?.trim()?.lowercase()?.removePrefix("[")?.removeSuffix("]")?.removeSuffix(".") ?: return false
        if (h.isEmpty()) return false
        if (h == "localhost" || h.endsWith(".localhost")) return true
        if (h == "::1") return true
        if (':' in h) { // IPv6 literal: loopback, unique-local fc00::/7, link-local fe80::/10
            return h.startsWith("fc") || h.startsWith("fd") || h.startsWith("fe8") || h.startsWith("fe9") ||
                h.startsWith("fea") || h.startsWith("feb")
        }
        IPV4.matchEntire(h)?.let { m ->
            val o = m.groupValues.drop(1).map { it.toInt() }
            if (o.any { it > 255 }) return false
            return o[0] == 127 || o[0] == 10 ||
                (o[0] == 172 && o[1] in 16..31) ||
                (o[0] == 192 && o[1] == 168) ||
                (o[0] == 169 && o[1] == 254) ||
                (o[0] == 100 && o[1] in 64..127)
        }
        if ('.' !in h) return true // single-label LAN name
        return PRIVATE_SUFFIXES.any { h.endsWith(it) }
    }

    /**
     * Null if [url] may be used to send credentials/evidence, otherwise a short reason.
     * https is always acceptable; http only to a private host.
     */
    fun cleartextProblem(url: String): String? {
        val u = try { URI(url.trim()) } catch (_: Exception) { return "Invalid URL" }
        return when (u.scheme?.lowercase()) {
            "https" -> null
            "http" -> if (isPrivateHost(u.host)) null
            else "Refusing to send credentials or evidence over plain http to a non-private host. Use https:// or a LAN/VPN address."
            else -> "Only http(s) URLs are supported"
        }
    }
}
