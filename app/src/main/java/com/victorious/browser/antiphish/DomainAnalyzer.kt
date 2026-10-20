package com.victorious.browser.antiphish

import java.net.IDN

/**
 * Pure host/domain heuristics for the defensive anti-phishing analyzer.
 * No Android deps — fully unit-tested. Punycode decoding uses java.net.IDN.
 */
object DomainAnalyzer {

    /** Common multi-label public suffixes (small built-in subset; heuristic, not the full PSL). */
    private val MULTI_SUFFIXES = setOf(
        "co.uk", "org.uk", "ac.uk", "gov.uk", "com.au", "net.au", "org.au", "co.nz",
        "co.jp", "ne.jp", "com.br", "com.mx", "co.in", "co.za", "com.cn", "com.tr",
        "com.sg", "com.hk", "co.kr", "com.ar",
    )

    /** TLDs frequently abused for throwaway phishing hosts (heuristic weighting only). */
    val SUSPICIOUS_TLDS = setOf(
        "zip", "mov", "xyz", "top", "tk", "ml", "ga", "cf", "gq", "click", "country",
        "work", "loan", "rest", "cam", "icu", "buzz", "support", "kim", "men", "live",
    )

    /** Confusable characters → ASCII skeleton (subset of Unicode TR39 confusables). */
    private val CONFUSABLES: Map<Char, String> = mapOf(
        // Cyrillic
        'а' to "a", 'е' to "e", 'о' to "o", 'р' to "p", 'с' to "c", 'у' to "y", 'х' to "x",
        'і' to "i", 'ј' to "j", 'ѕ' to "s", 'һ' to "h", 'ԁ' to "d", 'ԛ' to "q", 'ԝ' to "w",
        'ӏ' to "l", 'в' to "b", 'к' to "k", 'м' to "m", 'н' to "h", 'т' to "t",
        // Greek
        'α' to "a", 'ο' to "o", 'ρ' to "p", 'ν' to "v", 'τ' to "t", 'ι' to "i", 'κ' to "k",
        'ε' to "e", 'χ' to "x", 'υ' to "u",
        // Latin lookalikes / accents
        'ı' to "i", 'ɡ' to "g", 'ɑ' to "a", 'à' to "a", 'á' to "a", 'â' to "a", 'ä' to "a",
        'å' to "a", 'è' to "e", 'é' to "e", 'ê' to "e", 'ë' to "e", 'ì' to "i", 'í' to "i",
        'î' to "i", 'ï' to "i", 'ò' to "o", 'ó' to "o", 'ô' to "o", 'ö' to "o", 'ø' to "o",
        'ù' to "u", 'ú' to "u", 'û' to "u", 'ü' to "u", 'ñ' to "n", 'ç' to "c", 'ý' to "y",
    )

    /** ASCII digit/letter swaps commonly used in typosquats. */
    private val ASCII_SKELETON: List<Pair<String, String>> = listOf(
        "rn" to "m", "vv" to "w", "0" to "o", "1" to "l", "3" to "e", "5" to "s", "|" to "l",
    )

    fun normalizeHost(host: String): String = host.trim().trimEnd('.').lowercase()

    fun isIpLiteral(host: String): Boolean {
        val h = normalizeHost(host).removePrefix("[").removeSuffix("]")
        if (h.contains(':') && h.all { it.isLetterOrDigit() || it == ':' || it == '.' }) {
            return h.count { it == ':' } >= 2
        }
        val parts = h.split('.')
        if (parts.size != 4) return false
        return parts.all { p -> p.isNotEmpty() && p.length <= 3 && p.all(Char::isDigit) && p.toInt() in 0..255 }
    }

    /** True when any label is ACE/punycode encoded ("xn--"). */
    fun isPunycode(host: String): Boolean =
        normalizeHost(host).split('.').any { it.startsWith("xn--") }

    /** Unicode form of a host (punycode decoded). Falls back to input on error. */
    fun toUnicode(host: String): String =
        runCatching { IDN.toUnicode(normalizeHost(host), IDN.ALLOW_UNASSIGNED) }.getOrElse { normalizeHost(host) }

    /** True when the decoded host contains non-ASCII characters. */
    fun hasNonAscii(host: String): Boolean = toUnicode(host).any { it.code > 0x7F }

    /** Mixed scripts inside a single label (e.g. Latin + Cyrillic) — classic homoglyph sign. */
    fun hasMixedScriptLabel(host: String): Boolean =
        toUnicode(host).split('.').any { label ->
            val scripts = label.filter { it.isLetter() }
                .map { Character.UnicodeScript.of(it.code) }
                .toSet()
            scripts.size > 1
        }

    /** Confusable-folded skeleton of the decoded host (Unicode + ASCII swaps). */
    fun skeleton(host: String): String {
        val uni = toUnicode(host)
        val sb = StringBuilder()
        for (c in uni) sb.append(CONFUSABLES[c] ?: c.toString())
        var s = sb.toString()
        for ((from, to) in ASCII_SKELETON) s = s.replace(from, to)
        return s
    }

    /** Registrable domain (eTLD+1) using a small built-in suffix table. */
    fun registrableDomain(host: String): String {
        val h = normalizeHost(host)
        if (h.isEmpty() || isIpLiteral(h)) return h
        val labels = h.split('.').filter { it.isNotEmpty() }
        if (labels.size <= 2) return labels.joinToString(".")
        val lastTwo = labels.takeLast(2).joinToString(".")
        return if (lastTwo in MULTI_SUFFIXES) labels.takeLast(3).joinToString(".") else lastTwo
    }

    fun tld(host: String): String = normalizeHost(host).substringAfterLast('.', "")

    /** Registrable domain minus its public suffix, e.g. "accounts.example.co.uk" → "example". */
    fun registrableLabel(host: String): String {
        val reg = registrableDomain(host)
        return reg.substringBefore('.')
    }

    /** Classic Levenshtein edit distance. */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    data class LookalikeMatch(val knownDomain: String, val distance: Int, val viaSkeleton: Boolean)

    /** True when [host] is [known] or a subdomain of it. */
    fun isSameOrSubdomain(host: String, known: String): Boolean {
        val h = normalizeHost(host); val k = normalizeHost(known)
        return h == k || h.endsWith(".$k")
    }

    /**
     * Find a known-good domain that [host] imitates without actually belonging to it.
     * Matches by: confusable skeleton equality, small edit distance on the registrable
     * label, or the brand label embedded in a different registrable domain
     * (e.g. "example-login.top", "example.com.verify-account.net").
     */
    fun findLookalike(host: String, knownGood: Collection<String>): LookalikeMatch? {
        val h = normalizeHost(host)
        if (h.isEmpty() || isIpLiteral(h)) return null
        if (knownGood.any { isSameOrSubdomain(h, it) }) return null
        val hostReg = registrableDomain(h)
        val hostLabel = registrableLabel(h)
        val hostSkel = skeleton(hostReg)
        var best: LookalikeMatch? = null
        for (raw in knownGood) {
            val known = normalizeHost(raw)
            if (known.isEmpty()) continue
            val knownReg = registrableDomain(known)
            val knownLabel = registrableLabel(known)
            if (knownLabel.length < 3) continue
            val cand: LookalikeMatch? = when {
                hostSkel == skeleton(knownReg) || skeleton(hostLabel) == knownLabel ->
                    LookalikeMatch(known, 0, viaSkeleton = true)
                else -> {
                    val d = levenshtein(hostLabel, knownLabel)
                    val maxD = if (knownLabel.length >= 8) 2 else 1
                    when {
                        d in 1..maxD -> LookalikeMatch(known, d, viaSkeleton = false)
                        embedsBrand(h, knownLabel, knownReg) -> LookalikeMatch(known, 0, viaSkeleton = false)
                        else -> null
                    }
                }
            }
            if (cand != null && (best == null || cand.distance < best.distance)) best = cand
        }
        return best
    }

    private fun embedsBrand(host: String, brandLabel: String, knownReg: String): Boolean {
        if (brandLabel.length < 4) return false
        val tokens = host.split('.', '-', '_')
        return tokens.contains(brandLabel) || host.contains("$knownReg.")
    }

    /** Structural oddities typical of throwaway phishing hosts. Returns human reasons. */
    fun structuralFlags(host: String): List<String> {
        val h = normalizeHost(host)
        if (h.isEmpty() || isIpLiteral(h)) return emptyList()
        val out = mutableListOf<String>()
        val labels = h.split('.')
        if (labels.size >= 5) out += "Deeply nested subdomains (${labels.size} labels)"
        if (h.count { it == '-' } >= 3) out += "Many hyphens in host"
        if (h.length > 50) out += "Unusually long host (${h.length} chars)"
        val reg = registrableLabel(h)
        if (reg.length >= 6 && reg.count(Char::isDigit) * 2 >= reg.length) out += "Mostly-numeric domain label"
        return out
    }
}
