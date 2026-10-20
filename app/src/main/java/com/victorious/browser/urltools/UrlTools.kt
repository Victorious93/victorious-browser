package com.victorious.browser.urltools

import java.net.URI
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

    /**
     * "URL changer": a URL broken into its editable components. [port] is null when
     * absent from the original URL (not the same as explicitly stating a scheme's
     * default port) — [buildFromParts] omits it in that case too, so a round trip
     * through [parseParts] → [buildFromParts] doesn't invent a port that wasn't there.
     */
    data class UrlParts(
        val scheme: String,
        val host: String,
        val port: Int?,
        val path: String,
        val query: String,
        val fragment: String,
    )

    /** Parses an absolute URL into [UrlParts]; null if it isn't a valid absolute URL (needs a scheme and host). */
    fun parseParts(url: String): UrlParts? = try {
        val uri = URI(url)
        if (uri.scheme == null || uri.host == null) {
            null
        } else {
            UrlParts(
                scheme = uri.scheme,
                host = uri.host,
                port = if (uri.port == -1) null else uri.port,
                path = uri.rawPath.orEmpty(),
                query = uri.rawQuery.orEmpty(),
                fragment = uri.rawFragment.orEmpty(),
            )
        }
    } catch (_: Exception) {
        null
    }

    /** Reassembles [UrlParts] back into a URL string. */
    fun buildFromParts(parts: UrlParts): String = buildString {
        append(parts.scheme).append("://").append(parts.host)
        parts.port?.let { append(':').append(it) }
        append(parts.path)
        if (parts.query.isNotBlank()) append('?').append(parts.query)
        if (parts.fragment.isNotBlank()) append('#').append(parts.fragment)
    }
}
