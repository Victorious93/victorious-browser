package com.victorious.browser.browser

/** A small, honest transport hint for the browser chrome. */
data class TlsHint(val label: String, val warn: Boolean)

/**
 * Derive the transport chip from what WebView actually reported. Pure + unit-tested.
 *  - http://            → "HTTP · plain" (warn) — the page is not encrypted.
 *  - https:// + cert    → "TLS · <issuer CN>" ("TLS·proxy" variant when the inspection
 *                          proxy is on, since WebView then sees the proxy's leaf cert).
 *  - https:// no cert   → null (not loaded yet / unknown — show nothing rather than guess).
 *  - anything else      → null.
 * Deliberately no lock icon: WebView only hands us the certificate, not a verdict.
 */
internal fun tlsHint(url: String, issuerCn: String?, proxyOn: Boolean): TlsHint? {
    val l = url.trim().lowercase()
    return when {
        l.startsWith("http://") -> TlsHint("HTTP · plain", warn = true)
        l.startsWith("https://") && issuerCn != null -> {
            val cn = issuerCn.ifBlank { "unknown issuer" }.take(28)
            TlsHint(if (proxyOn) "TLS·proxy · $cn" else "TLS · $cn", warn = false)
        }
        else -> null
    }
}
