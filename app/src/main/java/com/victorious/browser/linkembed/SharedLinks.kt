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

    /**
     * What a share-in should do with the extracted URLs: nothing, preview the single
     * link directly, or let the user pick from several.
     */
    sealed class ShareAction {
        data object NoLinks : ShareAction()
        data class PreviewOne(val url: String) : ShareAction()
        data class Pick(val urls: List<String>) : ShareAction()
    }

    fun actionFor(urls: List<String>): ShareAction = when (urls.size) {
        0 -> ShareAction.NoLinks
        1 -> ShareAction.PreviewOne(urls.first())
        else -> ShareAction.Pick(urls)
    }

    /**
     * Compact two-part label for the link picker: host (without `www.`) and the rest
     * (path + query, ellipsized to [maxRest] chars). Falls back to the raw string.
     */
    fun displayLabel(url: String, maxRest: Int = 60): Pair<String, String> {
        val m = Regex("""(?i)^https?://([^/?#]+)(.*)$""").find(url) ?: return url to ""
        val host = m.groupValues[1].removePrefix("www.").removePrefix("WWW.")
        var rest = m.groupValues[2].takeIf { it != "/" }.orEmpty()
        if (rest.length > maxRest) rest = rest.take((maxRest - 1).coerceAtLeast(1)) + "…"
        return host to rest
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
