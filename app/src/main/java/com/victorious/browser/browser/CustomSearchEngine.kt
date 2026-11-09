package com.victorious.browser.browser

import java.net.URLEncoder

/**
 * Victorious Browser — user-defined ("custom") address-bar search engine.
 *
 * Pure, Android-free logic for validating a search template and building a query
 * URL from it. A template is either:
 *   - a placeholder template containing `%s`, e.g. `https://searx.be/search?q=%s&safe=1`
 *     (the query is URL-encoded and substituted for every `%s`), or
 *   - a bare prefix with no `%s`, e.g. `https://searx.be/search?q=`
 *     (the encoded query is appended — the same model the built-in engines use).
 *
 * Everything here is unit-tested.
 */
object CustomSearchEngine {

    const val PLACEHOLDER = "%s"

    /**
     * A template is usable if it is an absolute http(s) URL with a non-empty host.
     * (The query-placement rule — substitute `%s` or append — is handled by [buildUrl].)
     */
    fun isValid(template: String): Boolean {
        val t = template.trim()
        val scheme = when {
            t.startsWith("https://", ignoreCase = true) -> "https://"
            t.startsWith("http://", ignoreCase = true) -> "http://"
            else -> return false
        }
        val afterScheme = t.substring(scheme.length)
        // Need at least one host character before any path/query/fragment separator.
        val host = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        return host.isNotBlank()
    }

    /**
     * Build a query URL for [query] using [template]. Returns null when [template]
     * is not [isValid]. The query is URL-encoded; `%s` is substituted everywhere it
     * appears, otherwise the encoded query is appended to the template.
     */
    fun buildUrl(template: String, query: String): String? {
        if (!isValid(template)) return null
        val t = template.trim()
        val enc = URLEncoder.encode(query, "UTF-8")
        return if (t.contains(PLACEHOLDER)) t.replace(PLACEHOLDER, enc) else t + enc
    }
}
