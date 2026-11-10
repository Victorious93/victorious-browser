package com.victorious.browser.linkembed

/**
 * Extracts link targets from inbound Android intents without touching `Intent`
 * itself (so it is unit-testable): ACTION_SEND `text/plain` bodies and ACTION_VIEW
 * data strings. Only http(s) URLs that pass [UrlSafety] are returned.
 */
object SharedLinks {
    // Liberal match, then trimmed of trailing punctuation that is almost never part of a URL.
    private val URL_RE = Regex("""(?i)\bhttps?://[^\s<>"'\u00A0]+""")
    private const val TRAILING = ".,;:!?)]}'\"»”’>"
    const val MAX_URLS = 20

    /** All distinct safe http(s) URLs found in shared text (subject first, then body), max [MAX_URLS]. */
    fun extractUrls(text: String?, subject: String? = null): List<String> {
        val out = LinkedHashSet<String>()
        for (chunk in listOfNotNull(subject, text)) {
            for (m in URL_RE.findAll(chunk)) {
                val candidate = trimTrailing(m.value)
                UrlSafety.sanitize(candidate)?.let { out += it }
                if (out.size >= MAX_URLS) return out.toList()
            }
        }
        return out.toList()
    }

    /** ACTION_VIEW: the data URI only if it is a safe http(s) URL. */
    fun fromView(dataString: String?): String? = UrlSafety.sanitize(dataString)

    private fun trimTrailing(s: String): String {
        var end = s.length
        while (end > 0 && s[end - 1] in TRAILING) {
            // Keep a closing paren that balances one inside the URL (e.g. Wikipedia links).
            if (s[end - 1] == ')' && s.substring(0, end).count { it == '(' } >= s.substring(0, end).count { it == ')' }) break
            end--
        }
        return s.substring(0, end)
    }
}
