package com.victorious.browser.urltools

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Pure URL utilities: percent-encode/decode and query-string parsing/building.
 * No network, no Android dependency — kept separate from [LinkShortener], which is
 * the one piece of this package that makes an outbound call.
 */
object UrlTools {
    data class QueryParam(val key: String, val value: String)

    fun encode(raw: String): String = URLEncoder.encode(raw, "UTF-8")

    /** Returns [raw] unchanged if it isn't valid percent-encoding, rather than throwing. */
    fun decode(raw: String): String = try {
        URLDecoder.decode(raw, "UTF-8")
    } catch (_: Exception) {
        raw
    }

    /** Parses the query string of a URL (or a bare query string) into ordered key/value pairs. */
    fun parseQueryParams(urlOrQuery: String): List<QueryParam> {
        val query = when {
            '?' in urlOrQuery -> urlOrQuery.substringAfter('?')
            "://" in urlOrQuery -> "" // a full URL with no query string at all
            else -> urlOrQuery // a bare query string, e.g. "a=1&b=2"
        }.substringBefore('#')
        if (query.isBlank()) return emptyList()
        return query.split('&').mapNotNull { pair ->
            if (pair.isBlank()) return@mapNotNull null
            val eq = pair.indexOf('=')
            if (eq < 0) {
                QueryParam(decode(pair), "")
            } else {
                QueryParam(decode(pair.substring(0, eq)), decode(pair.substring(eq + 1)))
            }
        }
    }

    /** Rebuilds [baseUrl] (query string and fragment stripped) with [params] appended. */
    fun buildUrl(baseUrl: String, params: List<QueryParam>): String {
        val base = baseUrl.substringBefore('?').substringBefore('#')
        if (params.isEmpty()) return base
        val query = params.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }
        return "$base?$query"
    }
}
