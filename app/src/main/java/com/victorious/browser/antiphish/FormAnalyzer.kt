package com.victorious.browser.antiphish

/**
 * Pure form heuristics for credential-phishing detection.
 * Operates on [PageFormSnapshot] — never touches live credentials.
 */
object FormAnalyzer {

    data class FormFinding(
        val code: String,
        val detail: String,
        val weight: Int,
    )

    /**
     * Analyze credential-bearing forms on [pageUrl] against the page's own host.
     * [https] is the page scheme (false → http / other insecure).
     */
    fun analyze(pageUrl: String, https: Boolean, forms: List<PageFormSnapshot>): List<FormFinding> {
        val pageHost = hostOf(pageUrl) ?: return emptyList()
        val pageReg = DomainAnalyzer.registrableDomain(pageHost)
        val out = mutableListOf<FormFinding>()

        if (!https && forms.any { it.hasPasswordField }) {
            out += FormFinding(
                "insecure_password",
                "Password field on non-HTTPS page ($pageHost)",
                weight = 40,
            )
        }

        if (DomainAnalyzer.isIpLiteral(pageHost) && forms.any { it.hasPasswordField || it.hasUsernameField }) {
            out += FormFinding(
                "ip_literal_creds",
                "Credential form hosted on IP-literal ($pageHost)",
                weight = 35,
            )
        }

        for ((idx, form) in forms.withIndex()) {
            if (!form.hasPasswordField && !form.hasUsernameField) continue
            val actionHost = form.actionHost?.let(DomainAnalyzer::normalizeHost)
            if (!actionHost.isNullOrBlank()) {
                val actionReg = DomainAnalyzer.registrableDomain(actionHost)
                if (actionReg.isNotBlank() && pageReg.isNotBlank() &&
                    !DomainAnalyzer.isSameOrSubdomain(actionHost, pageReg) &&
                    actionReg != pageReg
                ) {
                    out += FormFinding(
                        "cross_domain_action",
                        "Form #$idx posts credentials to different domain ($actionReg ≠ $pageReg)",
                        weight = 45,
                    )
                }
            }
            if (form.hasHiddenCredentialField) {
                out += FormFinding(
                    "hidden_creds",
                    "Form #$idx has hidden/off-screen credential fields",
                    weight = 25,
                )
            }
            if (form.passwordFieldCount > 1) {
                out += FormFinding(
                    "multi_password",
                    "Form #$idx has ${form.passwordFieldCount} password fields",
                    weight = 10,
                )
            }
        }
        return out
    }

    fun hostOf(url: String): String? {
        val raw = url.trim()
        if (raw.isEmpty()) return null
        // java.net.URI rejects non-ASCII hosts; try IDN-ASCII rewrite of the host segment.
        return runCatching {
            java.net.URI(raw).host?.takeIf { it.isNotBlank() }
        }.getOrNull()
            ?: runCatching {
                val rewritten = idnRewriteUrl(raw) ?: return@runCatching null
                java.net.URI(rewritten).host?.takeIf { it.isNotBlank() }
            }.getOrNull()
            ?: runCatching {
                // Fallback: scrape host-ish segment between scheme and path.
                val noScheme = raw.substringAfter("://", missingDelimiterValue = raw)
                val hostPort = noScheme.substringBefore('/').substringBefore('?').substringBefore('#')
                val host = hostPort.substringAfter('@').substringBefore(':').trim()
                host.takeIf { it.isNotBlank() }?.let { DomainAnalyzer.normalizeHost(it) }
            }.getOrNull()
    }

    /** Rewrite the host portion of a URL to ACE/punycode so URI can parse it. */
    internal fun idnRewriteUrl(url: String): String? {
        val schemeIdx = url.indexOf("://")
        if (schemeIdx < 0) return null
        val after = url.substring(schemeIdx + 3)
        val slash = after.indexOfAny(charArrayOf('/', '?', '#'))
        val authority = if (slash < 0) after else after.substring(0, slash)
        val rest = if (slash < 0) "" else after.substring(slash)
        val at = authority.lastIndexOf('@')
        val userinfo = if (at >= 0) authority.substring(0, at + 1) else ""
        val hostPort = if (at >= 0) authority.substring(at + 1) else authority
        if (hostPort.startsWith("[")) return null // IPv6 literal — leave alone
        val colon = hostPort.lastIndexOf(':')
        val host = if (colon >= 0) hostPort.substring(0, colon) else hostPort
        val port = if (colon >= 0) hostPort.substring(colon) else ""
        if (host.isBlank()) return null
        val ascii = runCatching {
            java.net.IDN.toASCII(host, java.net.IDN.ALLOW_UNASSIGNED)
        }.getOrNull() ?: return null
        return url.substring(0, schemeIdx + 3) + userinfo + ascii + port + rest
    }
}

/**
 * Snapshot of a form as observed from the page DOM (via JS bridge).
 * Never includes field values — only presence / layout flags.
 */
data class PageFormSnapshot(
    val actionUrl: String? = null,
    val actionHost: String? = null,
    val hasPasswordField: Boolean = false,
    val hasUsernameField: Boolean = false,
    val hasHiddenCredentialField: Boolean = false,
    val passwordFieldCount: Int = 0,
)
