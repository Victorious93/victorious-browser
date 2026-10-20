package com.victorious.browser.evidence

import com.victorious.browser.export.SecretStripper

/**
 * Secret-stripping for request/response evidence. Reuses the fleet-export
 * [SecretStripper] deny-lists so NO cookies, passwords, proxy creds, tokens or
 * cert material are ever written into a saved evidence case.
 *
 * Pure logic — unit-tested. Produces a compact, human-readable text block that
 * the evidence store persists as the item's [EvidenceItem.data].
 */
object EvidenceSanitizer {

    /**
     * Build a secret-stripped text summary of one HTTP exchange suitable for
     * saving as REQUEST evidence. Strips sensitive headers and query params; the
     * body is intentionally omitted (it can carry secrets we cannot name-match).
     */
    fun sanitizedExchange(
        method: String,
        url: String,
        requestHeaders: Map<String, String>,
        responseHeaders: Map<String, String>,
        statusCode: Int?,
    ): String {
        val cleanUrl = SecretStripper.stripUrlQuery(url)
        val reqH = SecretStripper.stripHeaders(requestHeaders)
        val resH = SecretStripper.stripHeaders(responseHeaders)
        val sb = StringBuilder()
        sb.append("# Request\n")
        sb.append(method).append(' ').append(cleanUrl).append('\n')
        for ((k, v) in reqH) sb.append(k).append(": ").append(v).append('\n')
        sb.append("\n# Response\n")
        sb.append("status: ").append(statusCode?.toString() ?: "-").append('\n')
        for ((k, v) in resH) sb.append(k).append(": ").append(v).append('\n')
        sb.append("\n(secrets stripped: cookies/auth/proxy-creds/tokens/certs removed; body omitted)")
        return sb.toString()
    }
}
