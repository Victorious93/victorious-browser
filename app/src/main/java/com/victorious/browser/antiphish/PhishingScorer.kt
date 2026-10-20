package com.victorious.browser.antiphish

/**
 * Pure scoring engine: combines domain / form / brand findings into a risk score
 * and structured reasons. Fully unit-tested; no Android deps.
 */
object PhishingScorer {

    const val SCORE_MAX = 100
    /** Advisory banner threshold — score at or above this is considered "risky". */
    const val RISK_THRESHOLD = 40

    enum class RiskLevel { NONE, LOW, MEDIUM, HIGH, CRITICAL }

    data class Reason(
        val code: String,
        val detail: String,
        val weight: Int,
    )

    data class Result(
        val score: Int,
        val level: RiskLevel,
        val reasons: List<Reason>,
        val host: String,
    ) {
        val isRisky: Boolean get() = score >= RISK_THRESHOLD
    }

    /**
     * Score a page given its URL, HTTPS flag, optional title, forms, and brand hints.
     * [knownGood] and [brandKeywords] come from [AntiPhishSettings] (or defaults).
     */
    fun score(
        pageUrl: String,
        https: Boolean,
        pageTitle: String? = null,
        forms: List<PageFormSnapshot> = emptyList(),
        bodyBrandHints: List<String> = emptyList(),
        knownGood: Collection<String> = KnownGoodBrands.DEFAULT_DOMAINS,
        brandKeywords: Map<String, String> = KnownGoodBrands.DEFAULT_KEYWORDS,
    ): Result {
        val host = FormAnalyzer.hostOf(pageUrl).orEmpty()
        if (host.isEmpty()) {
            return Result(0, RiskLevel.NONE, emptyList(), "")
        }
        val reasons = mutableListOf<Reason>()

        // --- Domain checks ---
        if (DomainAnalyzer.isPunycode(host) || DomainAnalyzer.hasNonAscii(host)) {
            reasons += Reason(
                "punycode_idn",
                "Host uses punycode/IDN (${DomainAnalyzer.toUnicode(host)})",
                weight = 25,
            )
        }
        if (DomainAnalyzer.hasMixedScriptLabel(host)) {
            reasons += Reason(
                "mixed_script",
                "Host label mixes Unicode scripts (homoglyph risk)",
                weight = 35,
            )
        }
        DomainAnalyzer.findLookalike(host, knownGood)?.let { match ->
            val how = when {
                match.viaSkeleton -> "confusable skeleton match"
                match.distance > 0 -> "edit distance ${match.distance}"
                else -> "brand embedded in host"
            }
            reasons += Reason(
                "lookalike",
                "Lookalike of ${match.knownDomain} ($how)",
                weight = if (match.viaSkeleton || match.distance == 0) 40 else 30,
            )
        }
        val tld = DomainAnalyzer.tld(host)
        if (tld in DomainAnalyzer.SUSPICIOUS_TLDS) {
            reasons += Reason(
                "suspicious_tld",
                "Suspicious TLD .$tld",
                weight = 15,
            )
        }
        for (flag in DomainAnalyzer.structuralFlags(host)) {
            reasons += Reason("structural", flag, weight = 10)
        }

        // --- Form checks ---
        for (f in FormAnalyzer.analyze(pageUrl, https, forms)) {
            reasons += Reason(f.code, f.detail, f.weight)
        }

        // --- Brand / title checks ---
        for (b in PageBrandAnalyzer.analyze(host, pageTitle, bodyBrandHints, brandKeywords, knownGood)) {
            reasons += Reason(b.code, b.detail, b.weight)
        }

        // Cap overlapping codes: keep the highest-weight instance of each code,
        // but allow multiple distinct reason codes to accumulate.
        val score = reasons.sumOf { it.weight }.coerceIn(0, SCORE_MAX)
        val level = when {
            score >= 80 -> RiskLevel.CRITICAL
            score >= 60 -> RiskLevel.HIGH
            score >= RISK_THRESHOLD -> RiskLevel.MEDIUM
            score >= 20 -> RiskLevel.LOW
            else -> RiskLevel.NONE
        }
        return Result(score, level, reasons, host)
    }
}
