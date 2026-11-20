package com.victorious.browser.browser.privacy

import java.io.InputStream
import java.net.IDN

/**
 * Public Suffix List (https://publicsuffix.org, MPL-2.0; see NOTICE) matcher.
 * Pure Kotlin / Android-free so it can be unit-tested on the JVM.
 *
 * Implements the algorithm from https://github.com/publicsuffix/list/wiki/Format:
 * normal, wildcard (`*.ck`) and exception (`!www.ck`) rules, with the implicit
 * `*` default (an unlisted TLD is its own public suffix). Both the ICANN and PRIVATE
 * sections are used on purpose: for cookies / first-vs-third-party decisions,
 * `github.io` and `blogspot.com` ARE public suffixes.
 */
class PublicSuffixList private constructor(
    private val rules: Set<String>,
    private val wildcards: Set<String>, // stored without the leading "*."
    private val exceptions: Set<String>, // stored without the leading "!"
) {
    /** Number of labels in the public suffix of [host] (>= 1), host labels already normalised. */
    private fun suffixStart(labels: List<String>): Int {
        val n = labels.size
        fun suffix(i: Int) = labels.subList(i, n).joinToString(".")
        // Exception rules prevail over everything: public suffix = the exception minus its leftmost label.
        for (i in 0 until n) if (suffix(i) in exceptions) return i + 1
        // Otherwise the longest matching rule (smallest start index) wins.
        for (i in 0 until n) {
            if (suffix(i) in rules) return i
            if (i + 1 < n && suffix(i + 1) in wildcards) return i
        }
        return n - 1 // implicit "*" rule
    }

    private fun labelsOf(host: String): List<String>? {
        val h = normalize(host) ?: return null
        val labels = h.split('.')
        return if (labels.any { it.isEmpty() }) null else labels
    }

    /** The public suffix of [host] (e.g. "co.uk"), or null for blank / malformed input. */
    fun publicSuffix(host: String): String? {
        val labels = labelsOf(host) ?: return null
        return labels.subList(suffixStart(labels), labels.size).joinToString(".")
    }

    /** True when [host] is itself a public suffix (e.g. "com", "co.uk", "github.io"). */
    fun isPublicSuffix(host: String): Boolean {
        val labels = labelsOf(host) ?: return false
        return suffixStart(labels) == 0
    }

    /**
     * Registrable domain (eTLD+1), e.g. "a.b.example.co.uk" -> "example.co.uk".
     * Returns the normalised host unchanged when it is itself a public suffix
     * (there is nothing more specific to return) and null for malformed input.
     */
    fun registrableDomain(host: String): String? {
        val labels = labelsOf(host) ?: return null
        val s = suffixStart(labels)
        val from = if (s == 0) 0 else s - 1
        return labels.subList(from, labels.size).joinToString(".")
    }

    companion object {
        /** Lower-case, strip trailing dot, convert IDN to ASCII; null if unusable. */
        internal fun normalize(host: String): String? {
            var h = host.trim().lowercase().removeSuffix(".")
            if (h.isEmpty()) return null
            if (h.any { it.code > 127 }) {
                h = try { IDN.toASCII(h, IDN.ALLOW_UNASSIGNED).lowercase() } catch (_: Exception) { return null }
            }
            return h
        }

        fun parse(lines: Sequence<String>): PublicSuffixList {
            val rules = HashSet<String>(10_000)
            val wildcards = HashSet<String>(256)
            val exceptions = HashSet<String>(64)
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("//")) continue
                // A rule ends at the first whitespace.
                val token = line.split(' ', '\t').first()
                when {
                    token.startsWith("!") -> normalize(token.substring(1))?.let { exceptions += it }
                    token.startsWith("*.") -> normalize(token.substring(2))?.let { wildcards += it }
                    else -> normalize(token)?.let { rules += it }
                }
            }
            return PublicSuffixList(rules, wildcards, exceptions)
        }

        fun parse(stream: InputStream): PublicSuffixList =
            stream.bufferedReader(Charsets.UTF_8).use { parse(it.lineSequence()) }

        /**
         * Tiny built-in table (the multi-part suffixes the app used before the full list
         * was bundled). Only used when the asset can't be loaded, e.g. plain JVM tests.
         */
        val FALLBACK: PublicSuffixList = parse(
            sequenceOf(
                "co.uk", "org.uk", "ac.uk", "gov.uk", "com.au", "net.au", "org.au", "co.jp", "ne.jp",
                "co.nz", "com.br", "com.cn", "com.mx", "co.in", "co.za", "com.tr", "com.sg", "com.hk",
                "co.kr", "com.ar",
            ),
        )
    }
}

/**
 * Process-wide holder. The app registers an [opener] for the bundled asset at startup;
 * the list is parsed on first use (~tens of ms) and cached. Until an opener exists (or if
 * loading fails) lookups use [PublicSuffixList.FALLBACK] and nothing is cached, so a later
 * successful load still takes effect.
 */
object PublicSuffixes {
    @Volatile var opener: (() -> InputStream)? = null
    @Volatile private var loaded: PublicSuffixList? = null

    val current: PublicSuffixList
        get() {
            loaded?.let { return it }
            val open = opener ?: return PublicSuffixList.FALLBACK
            return synchronized(this) {
                loaded ?: try {
                    PublicSuffixList.parse(open()).also { loaded = it }
                } catch (_: Exception) {
                    PublicSuffixList.FALLBACK
                }
            }
        }

    /** Test hook / explicit install. */
    fun install(list: PublicSuffixList?) { loaded = list }
}
