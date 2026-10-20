package com.victorious.browser.fuzz

/**
 * Pure logic for the directory / endpoint brute-forcer (ffuf-style).
 *
 * No Android or network types live here so every branch is unit-tested in
 * [com.victorious.browser.fuzz.FuzzLogicTest]. The runner
 * ([DirBruteRunner]) owns the networking and scope gate; this file owns
 * FUZZ substitution, word/extension expansion, response metrics, filter
 * matching and result dedup.
 */
object FuzzLogic {

    const val MARKER = "FUZZ"

    /** True when [base] contains the FUZZ marker (marker mode vs append mode). */
    fun hasMarker(base: String): Boolean = base.contains(MARKER)

    /**
     * Expand a single wordlist entry into the concrete words to try, given the
     * extension list. The bare word is always included; each non-blank extension
     * produces `word + ext`. A leading dot on the extension is optional and
     * normalized so both "php" and ".php" yield "word.php".
     */
    fun expandWord(word: String, extensions: List<String>): List<String> {
        val w = word.trim()
        if (w.isEmpty()) return emptyList()
        val out = LinkedHashSet<String>()
        out.add(w)
        for (raw in extensions) {
            val e = raw.trim()
            if (e.isEmpty()) continue
            val ext = if (e.startsWith(".")) e else ".$e"
            out.add(w + ext)
        }
        return out.toList()
    }

    /**
     * Build the concrete target URL for [word].
     *
     * Marker mode (base contains FUZZ): every occurrence of FUZZ is replaced.
     * Append mode: [word] is appended to [base], collapsing a doubled slash at
     * the join so "http://h/" + "/admin" and "http://h" + "admin" both behave.
     */
    fun buildUrl(base: String, word: String): String {
        if (hasMarker(base)) return base.replace(MARKER, word)
        return when {
            base.endsWith("/") && word.startsWith("/") -> base + word.removePrefix("/")
            !base.endsWith("/") && !word.startsWith("/") -> "$base/$word"
            else -> base + word
        }
    }

    /**
     * Full, de-duplicated list of target URLs for a wordlist. Order is preserved
     * (first occurrence wins). Blank words are skipped.
     */
    fun targets(base: String, words: List<String>, extensions: List<String>): List<String> {
        val seen = LinkedHashSet<String>()
        for (word in words) {
            for (candidate in expandWord(word, extensions)) {
                seen.add(buildUrl(base, candidate))
            }
        }
        return seen.toList()
    }

    /** Metrics ffuf shows per response: byte size, word count, line count. */
    data class RespMeta(val status: Int?, val size: Int, val words: Int, val lines: Int)

    fun metaOf(status: Int?, body: String?): RespMeta {
        val b = body ?: ""
        val size = b.toByteArray(Charsets.UTF_8).size
        val words = if (b.isBlank()) 0 else b.trim().split(Regex("\\s+")).size
        val lines = if (b.isEmpty()) 0 else b.split("\n").size
        return RespMeta(status, size, words, lines)
    }

    /** Parse a CSV of ints ("200, 301 ,403") into a set, ignoring junk. */
    fun parseIntCsv(s: String): Set<Int> =
        s.split(',', ' ', '\n')
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet()

    /** ffuf default "matched" status codes when no explicit match set is given. */
    val DEFAULT_MATCH_CODES: Set<Int> = setOf(200, 204, 301, 302, 307, 401, 403, 405, 500)

    /**
     * ffuf-style include/exclude filters. Empty match* sets mean "no positive
     * constraint on that dimension" (except codes, which fall back to the
     * default match set). Any filter* membership excludes the result.
     */
    data class FilterSpec(
        val matchCodes: Set<Int> = emptySet(),
        val filterCodes: Set<Int> = emptySet(),
        val matchSizes: Set<Int> = emptySet(),
        val filterSizes: Set<Int> = emptySet(),
        val matchWords: Set<Int> = emptySet(),
        val filterWords: Set<Int> = emptySet(),
        val matchLines: Set<Int> = emptySet(),
        val filterLines: Set<Int> = emptySet(),
    )

    /** True when [meta] passes [spec] and should be shown as a hit. */
    fun accept(meta: RespMeta, spec: FilterSpec): Boolean {
        val code = meta.status
        // Status: explicit match set wins, else the default match set.
        val codeSet = if (spec.matchCodes.isNotEmpty()) spec.matchCodes else DEFAULT_MATCH_CODES
        if (code == null || code !in codeSet) return false
        if (code in spec.filterCodes) return false

        if (spec.matchSizes.isNotEmpty() && meta.size !in spec.matchSizes) return false
        if (meta.size in spec.filterSizes) return false

        if (spec.matchWords.isNotEmpty() && meta.words !in spec.matchWords) return false
        if (meta.words in spec.filterWords) return false

        if (spec.matchLines.isNotEmpty() && meta.lines !in spec.matchLines) return false
        if (meta.lines in spec.filterLines) return false

        return true
    }

    /** A row in the live results table. */
    data class Hit(
        val path: String,
        val status: Int?,
        val size: Int,
        val words: Int,
        val lines: Int,
    )

    /**
     * Fold a new candidate hit into an existing (ordered) result list, de-duping
     * by path. Returns the new list (same instance if nothing changed). Used so
     * the UI can keep a stable, dedup'd table as results stream in.
     */
    fun dedupInsert(existing: List<Hit>, hit: Hit): List<Hit> {
        if (existing.any { it.path == hit.path }) return existing
        return existing + hit
    }
}
