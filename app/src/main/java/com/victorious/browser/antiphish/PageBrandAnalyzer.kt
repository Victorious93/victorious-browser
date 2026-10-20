package com.victorious.browser.antiphish

/**
 * Title / brand-keyword mismatch heuristics (text-only — no image OCR).
 * Pure Kotlin; unit-tested.
 */
object PageBrandAnalyzer {

    data class BrandFinding(
        val code: String,
        val detail: String,
        val weight: Int,
    )

    /**
     * Detect when a page title or body brand keywords claim a known brand whose
     * domain does not match the actual host.
     *
     * [brandKeywords] maps lowercase keyword → known-good domain (or brand id).
     * Default list comes from [KnownGoodBrands].
     */
    fun analyze(
        host: String,
        pageTitle: String?,
        bodyBrandHints: List<String> = emptyList(),
        brandKeywords: Map<String, String>,
        knownGoodDomains: Collection<String>,
    ): List<BrandFinding> {
        val h = DomainAnalyzer.normalizeHost(host)
        if (h.isEmpty()) return emptyList()
        val out = mutableListOf<BrandFinding>()
        val title = pageTitle.orEmpty().lowercase()
        val haystack = buildString {
            append(title)
            append(' ')
            bodyBrandHints.forEach { append(it.lowercase()); append(' ') }
        }
        if (haystack.isBlank()) return emptyList()

        // Already on a known-good domain → no brand mismatch.
        if (knownGoodDomains.any { DomainAnalyzer.isSameOrSubdomain(h, it) }) return emptyList()

        val claimed = mutableSetOf<String>()
        for ((keyword, brandDomain) in brandKeywords) {
            if (keyword.length < 3) continue
            // Word-ish match: keyword appears as a token in title/hints.
            if (!containsKeyword(haystack, keyword)) continue
            val brandHost = DomainAnalyzer.normalizeHost(brandDomain)
            if (DomainAnalyzer.isSameOrSubdomain(h, brandHost)) continue
            // Title claims brand but host is unrelated.
            if (claimed.add(brandHost)) {
                out += BrandFinding(
                    "brand_mismatch",
                    "Page claims \"$keyword\" but host is $h (expected $brandHost)",
                    weight = 30,
                )
            }
        }

        // Favicon/logo alt text treated the same as body hints (caller passes them in).
        return out
    }

    private fun containsKeyword(haystack: String, keyword: String): Boolean {
        val k = keyword.lowercase()
        // Require word boundary-ish: not mid-token of a longer unrelated word when short.
        val idx = haystack.indexOf(k)
        if (idx < 0) return false
        val before = if (idx == 0) ' ' else haystack[idx - 1]
        val after = if (idx + k.length >= haystack.length) ' ' else haystack[idx + k.length]
        return !before.isLetterOrDigit() && !after.isLetterOrDigit()
    }
}
