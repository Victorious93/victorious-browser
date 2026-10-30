package com.victorious.browser.browser

/**
 * Per-tab WebView console / JS-error ring buffer. Pure so [ConsoleLogTest] covers
 * the cap, formatting and level filter without Android's ConsoleMessage.
 */
data class ConsoleEntry(
    val level: String,
    val message: String,
    val source: String,
    val line: Int,
    val atMillis: Long,
)

object ConsoleLog {
    const val MAX_ENTRIES = 200

    val LEVELS = listOf("TIP", "LOG", "DEBUG", "INFO", "WARNING", "ERROR")

    fun append(
        list: List<ConsoleEntry>,
        entry: ConsoleEntry,
        max: Int = MAX_ENTRIES,
    ): List<ConsoleEntry> = (list + entry).takeLast(max.coerceAtLeast(1))

    fun format(entry: ConsoleEntry): String {
        val loc = when {
            entry.source.isBlank() && entry.line <= 0 -> ""
            entry.line > 0 -> " ${entry.source}:${entry.line}"
            else -> " ${entry.source}"
        }
        return "${entry.level}$loc  ${entry.message}"
    }

    fun filter(list: List<ConsoleEntry>, level: String?): List<ConsoleEntry> {
        val want = level?.trim()?.uppercase().orEmpty()
        if (want.isEmpty() || want == "ALL") return list
        return list.filter { it.level.equals(want, ignoreCase = true) }
    }

    fun errorCount(list: List<ConsoleEntry>): Int =
        list.count { it.level.equals("ERROR", ignoreCase = true) }

    /** Map android.webkit.ConsoleMessage.MessageLevel name (or a free string) to a stable label. */
    fun normalizeLevel(raw: String?): String {
        val u = raw?.trim()?.uppercase().orEmpty()
        return when (u) {
            "TIP" -> "TIP"
            "LOG" -> "LOG"
            "DEBUG" -> "DEBUG"
            "INFO" -> "INFO"
            "WARNING", "WARN" -> "WARNING"
            "ERROR", "ASSERT" -> "ERROR"
            else -> if (u.isEmpty()) "LOG" else u
        }
    }
}
