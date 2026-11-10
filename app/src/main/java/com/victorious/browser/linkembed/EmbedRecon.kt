package com.victorious.browser.linkembed

/**
 * Embeddability (framing) recon from HTTP response headers. Pure Kotlin.
 *
 * Mirrors how current browsers decide whether a response may render inside a
 * third-party `<iframe>`:
 *  - An enforced `Content-Security-Policy` `frame-ancestors` directive wins; when any
 *    enforced policy has one, `X-Frame-Options` is ignored (CSP3; HTML "X-Frame-Options"
 *    processing model). Every enforced policy must allow the embedder.
 *    `'none'` is only meaningful alone; listed with other sources it is ignored.
 *    `Content-Security-Policy-Report-Only` is reported but never enforced.
 *  - Otherwise `X-Frame-Options` per the HTML spec: DENY / SAMEORIGIN block a
 *    third-party embedder; more than one distinct value containing DENY, SAMEORIGIN
 *    or ALLOWALL blocks; `ALLOW-FROM` is obsolete and ignored by modern engines.
 *
 * IMPORTANT: a verdict is a technical signal about headers seen on ONE fetch from this
 * device. It is not legal permission to embed, says nothing about terms of service or
 * copyright, and does not guarantee playback (providers can still refuse by referrer,
 * cookies, region, JS frame-busting, or per-video settings).
 */
object EmbedRecon {
    enum class Verdict(val label: String) {
        EMBEDDABLE("Embeddable"),
        CONDITIONAL("Conditional"),
        NOT_EMBEDDABLE("Not embeddable"),
        UNKNOWN("Unknown"),
    }

    data class Report(
        val requestedUrl: String,
        val finalUrl: String?,
        val status: Int?,
        val contentType: String?,
        val redirects: List<String>,
        val xFrameOptions: String?,
        val frameAncestors: String?,
        val reportOnlyFrameAncestors: String?,
        val verdict: Verdict,
        val reasons: List<String>,
        val error: String? = null,
    )

    const val DISCLAIMER =
        "Technical signal only: based on response headers seen on one fetch from this device. " +
            "It is not legal permission to embed, and an \"embeddable\" verdict does not guarantee the " +
            "provider will allow playback (referrer, cookies, region and per-item settings can still block it)."

    private sealed class FaPolicy {
        object None : FaPolicy()
        object SelfOnly : FaPolicy()
        data class Any(val why: String) : FaPolicy()
        data class Listed(val sources: List<String>) : FaPolicy()
    }

    /** Headers as received: names case-insensitive, each list entry one header line. */
    fun evaluate(
        requestedUrl: String,
        finalUrl: String?,
        status: Int?,
        headers: Map<String, List<String>>,
        redirects: List<String> = emptyList(),
        error: String? = null,
    ): Report {
        fun all(name: String) = headers.entries.filter { it.key.equals(name, ignoreCase = true) }.flatMap { it.value }
        val contentType = all("Content-Type").firstOrNull()?.trim()
        val xfoRaw = all("X-Frame-Options").joinToString(", ").trim().takeIf { it.isNotEmpty() }
        val faDirectives = all("Content-Security-Policy").mapNotNull { frameAncestorsOf(it) }
        val roDirectives = all("Content-Security-Policy-Report-Only").mapNotNull { frameAncestorsOf(it) }
        val faRaw = faDirectives.joinToString(" | ").takeIf { faDirectives.isNotEmpty() }
        val roRaw = roDirectives.joinToString(" | ").takeIf { roDirectives.isNotEmpty() }
        val reasons = mutableListOf<String>()

        fun report(v: Verdict) = Report(
            requestedUrl, finalUrl, status, contentType, redirects, xfoRaw, faRaw, roRaw, v, reasons.toList(), error,
        )

        if (error != null || status == null) {
            reasons += "No usable response (${error ?: "no status"}), so framing headers could not be checked."
            return report(Verdict.UNKNOWN)
        }
        if (roRaw != null) reasons += "Report-Only CSP frame-ancestors ($roRaw) is not enforced by browsers."

        if (faDirectives.isNotEmpty()) {
            if (xfoRaw != null) reasons += "X-Frame-Options ($xfoRaw) is ignored because CSP frame-ancestors is present."
            val policies = faDirectives.map(::classify)
            val verdict = when {
                policies.any { it is FaPolicy.None } -> {
                    reasons += "CSP frame-ancestors 'none': no site may frame this page."
                    Verdict.NOT_EMBEDDABLE
                }
                policies.any { it is FaPolicy.SelfOnly } -> {
                    reasons += "CSP frame-ancestors 'self': only pages on the same origin may frame it."
                    Verdict.NOT_EMBEDDABLE
                }
                policies.all { it is FaPolicy.Any } -> {
                    reasons += policies.filterIsInstance<FaPolicy.Any>().map { it.why }.distinct().joinToString(" ")
                    Verdict.EMBEDDABLE
                }
                else -> {
                    val listed = policies.filterIsInstance<FaPolicy.Listed>().flatMap { it.sources }.distinct()
                    reasons += "CSP frame-ancestors allows only listed sources: ${listed.joinToString(" ")}."
                    Verdict.CONDITIONAL
                }
            }
            return finish(report(verdict), status, contentType, reasons)
        }

        if (xfoRaw != null) {
            val values = xfoRaw.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
            val strict = setOf("deny", "sameorigin", "allowall")
            val verdict = when {
                values.size > 1 && values.any { it in strict } -> {
                    reasons += "Conflicting X-Frame-Options values ($xfoRaw): browsers block framing."
                    Verdict.NOT_EMBEDDABLE
                }
                "deny" in values -> {
                    reasons += "X-Frame-Options: DENY — no site may frame this page."
                    Verdict.NOT_EMBEDDABLE
                }
                "sameorigin" in values -> {
                    reasons += "X-Frame-Options: SAMEORIGIN — only the same origin may frame it."
                    Verdict.NOT_EMBEDDABLE
                }
                values.any { it.startsWith("allow-from") } -> {
                    reasons += "X-Frame-Options ALLOW-FROM is obsolete: modern Chromium/Firefox ignore it, " +
                        "but the site signals it only wants the listed origin to frame it."
                    Verdict.CONDITIONAL
                }
                else -> {
                    reasons += "X-Frame-Options value \"$xfoRaw\" is not recognised and is ignored by browsers."
                    Verdict.EMBEDDABLE
                }
            }
            return finish(report(verdict), status, contentType, reasons)
        }

        reasons += "No CSP frame-ancestors and no X-Frame-Options header: no header-level framing restriction seen."
        return finish(report(Verdict.EMBEDDABLE), status, contentType, reasons)
    }

    /** Downgrades header-based verdicts when the response itself is not a normal page. */
    private fun finish(r: Report, status: Int, contentType: String?, reasons: MutableList<String>): Report {
        if (status !in 200..299) {
            if (r.verdict == Verdict.NOT_EMBEDDABLE) {
                reasons += "Note: final HTTP status was $status; the real page may send different headers."
                return r.copy(reasons = reasons.toList())
            }
            reasons += "Final HTTP status $status is not a success, so the headers may not reflect the real page."
            return r.copy(verdict = Verdict.UNKNOWN, reasons = reasons.toList())
        }
        val ct = contentType?.lowercase().orEmpty()
        if (r.verdict == Verdict.EMBEDDABLE && ct.isNotEmpty() && !ct.startsWith("text/html") && !ct.startsWith("application/xhtml")) {
            reasons += "Content-Type is \"$contentType\", not an HTML page; framing may still render it but this is not a page embed."
        }
        return r.copy(reasons = reasons.toList())
    }

    /** The `frame-ancestors` directive value from one CSP header line (multiple policies may be comma-joined). */
    fun frameAncestorsOf(cspHeader: String): String? {
        // A single header line may carry several comma-separated policies; each is enforced.
        val found = cspHeader.split(',').mapNotNull { policy ->
            policy.split(';').map { it.trim() }.firstOrNull {
                it.length >= 15 && it.substring(0, 15).equals("frame-ancestors", ignoreCase = true) &&
                    (it.length == 15 || it[15].isWhitespace())
            }?.substring(15)?.trim()?.replace(Regex("\\s+"), " ")
        }
        return if (found.isEmpty()) null else found.joinToString(" | ") { it.ifEmpty { "'none'" } }
    }

    private fun classify(directive: String): FaPolicy {
        // A combined "a | b" string means several enforced policies; the strictest wins.
        val parts = directive.split(" | ")
        if (parts.size > 1) {
            val each = parts.map(::classify)
            return each.firstOrNull { it is FaPolicy.None }
                ?: each.firstOrNull { it is FaPolicy.SelfOnly }
                ?: if (each.all { it is FaPolicy.Any }) each.first()
                else FaPolicy.Listed(each.filterIsInstance<FaPolicy.Listed>().flatMap { it.sources })
        }
        val sources = directive.split(' ').map { it.trim() }.filter { it.isNotEmpty() }
        val lower = sources.map { it.lowercase() }
        if (lower.isEmpty() || lower == listOf("'none'")) return FaPolicy.None
        val effective = sources.filterNot { it.equals("'none'", ignoreCase = true) } // ignored when not alone
        val effLower = effective.map { it.lowercase() }
        if (effLower.isEmpty()) return FaPolicy.None
        if ("*" in effLower) return FaPolicy.Any("CSP frame-ancestors * allows any http(s) origin.")
        if (effLower.all { it == "'self'" }) return FaPolicy.SelfOnly
        // A bare scheme source lets any origin of that scheme frame it ("http:" also matches https).
        if ("http:" in effLower) return FaPolicy.Any("CSP frame-ancestors http: allows any http(s) origin.")
        if ("https:" in effLower) return FaPolicy.Any("CSP frame-ancestors https: allows any https origin (not plain-http embedders).")
        return FaPolicy.Listed(effective)
    }
}
