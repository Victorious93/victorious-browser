package com.victorious.browser.fuzz

/**
 * Pure parsing / normalization for wordlists. Unit-tested in [WordlistParserTest].
 * SecLists-style lists are plain text, one entry per line; we strip comments
 * (lines beginning with #) and blanks, and optionally dedup while preserving order.
 */
object WordlistParser {

    /** Parse raw wordlist text into usable entries. */
    fun lines(raw: String, dedup: Boolean = true): List<String> {
        val out = if (dedup) LinkedHashSet<String>() else ArrayList<String>()
        for (line in raw.split("\n")) {
            val t = line.trim()
            if (t.isEmpty()) continue
            if (t.startsWith("#")) continue
            when (out) {
                is LinkedHashSet<String> -> out.add(t)
                is ArrayList<String> -> out.add(t)
            }
        }
        return out.toList()
    }

    /** First [max] entries, for a preview pane. */
    fun preview(raw: String, max: Int = 50): List<String> = lines(raw).take(max)

    /** Entry count after parsing. */
    fun count(raw: String): Int = lines(raw).size

    /** Sanitize a user-chosen wordlist name into a safe filename stem. */
    fun safeName(name: String): String {
        val cleaned = name.trim().lowercase()
            .replace(Regex("[^a-z0-9._-]"), "-")
            .replace(Regex("-+"), "-")
            .trim('-', '.')
        return cleaned.ifEmpty { "wordlist" }.take(64)
    }
}
