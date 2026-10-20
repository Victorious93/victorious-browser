package com.victorious.browser.inspect

import java.util.Locale

/**
 * Searchable / filterable view over [CaptureStore] history.
 *
 * Search matches URL, host, method, status, header names/values, body text
 * (when textual), cookie name/value, and decoded JWT claims / raw token.
 * Filters are simple equality / contains checks on method, status, content-type,
 * and host.
 */
object HistoryFilter {

    data class Criteria(
        val query: String = "",
        val method: String = "",
        val status: String = "",
        val contentType: String = "",
        val host: String = "",
    ) {
        val isEmpty: Boolean
            get() = query.isBlank() && method.isBlank() && status.isBlank() &&
                contentType.isBlank() && host.isBlank()
    }

    fun apply(exchanges: List<CapturedExchange>, criteria: Criteria): List<CapturedExchange> {
        if (criteria.isEmpty) return exchanges
        return exchanges.filter { matches(it, criteria) }
    }

    fun matches(ex: CapturedExchange, criteria: Criteria): Boolean {
        if (criteria.method.isNotBlank()) {
            if (!ex.method.equals(criteria.method.trim(), ignoreCase = true)) return false
        }
        if (criteria.status.isNotBlank()) {
            val want = criteria.status.trim()
            val got = ex.statusCode?.toString() ?: ""
            if (got != want && !got.startsWith(want)) return false
        }
        if (criteria.host.isNotBlank()) {
            val hostQ = criteria.host.trim().lowercase(Locale.US)
            if (!ex.host.lowercase(Locale.US).contains(hostQ)) return false
        }
        if (criteria.contentType.isNotBlank()) {
            val ctQ = criteria.contentType.trim().lowercase(Locale.US)
            val reqCt = ex.requestContentType()?.lowercase(Locale.US).orEmpty()
            val resCt = ex.responseContentType()?.lowercase(Locale.US).orEmpty()
            if (!reqCt.contains(ctQ) && !resCt.contains(ctQ)) return false
        }
        if (criteria.query.isNotBlank()) {
            if (!matchesQuery(ex, criteria.query.trim())) return false
        }
        return true
    }

    fun matchesQuery(ex: CapturedExchange, query: String): Boolean {
        val q = query.lowercase(Locale.US)
        if (ex.url.lowercase(Locale.US).contains(q)) return true
        if (ex.host.lowercase(Locale.US).contains(q)) return true
        if (ex.method.lowercase(Locale.US).contains(q)) return true
        if (ex.statusCode?.toString()?.contains(q) == true) return true
        if (ex.scheme.lowercase(Locale.US).contains(q)) return true

        fun headersHit(headers: List<CapturedHeader>): Boolean =
            headers.any {
                it.name.lowercase(Locale.US).contains(q) ||
                    it.value.lowercase(Locale.US).contains(q)
            }
        if (headersHit(ex.requestHeaders) || headersHit(ex.responseHeaders)) return true

        val cookies = InspectHelpers.parseCookies(ex.requestHeaders + ex.responseHeaders)
        if (cookies.any {
                it.name.lowercase(Locale.US).contains(q) ||
                    it.value.lowercase(Locale.US).contains(q) ||
                    it.attributes.any { (k, v) ->
                        k.lowercase(Locale.US).contains(q) || v.lowercase(Locale.US).contains(q)
                    }
            }
        ) {
            return true
        }

        val jwts = InspectHelpers.findJwts(
            requestHeaders = ex.requestHeaders,
            responseHeaders = ex.responseHeaders,
            requestBody = ex.requestBody,
            responseBody = ex.responseBody,
            requestContentType = ex.requestContentType(),
            responseContentType = ex.responseContentType(),
        )
        if (jwts.any { jwt ->
                jwt.raw.lowercase(Locale.US).contains(q) ||
                    jwt.source.lowercase(Locale.US).contains(q) ||
                    jwt.header?.lowercase(Locale.US)?.contains(q) == true ||
                    jwt.payload?.lowercase(Locale.US)?.contains(q) == true ||
                    jwt.claims.any { (k, v) ->
                        k.lowercase(Locale.US).contains(q) || v.lowercase(Locale.US).contains(q)
                    }
            }
        ) {
            return true
        }

        if (bodyContains(ex.requestBody, ex.requestContentType(), q)) return true
        if (bodyContains(ex.responseBody, ex.responseContentType(), q)) return true

        return false
    }

    private fun bodyContains(body: ByteArray?, contentType: String?, q: String): Boolean {
        if (body == null || body.isEmpty()) return false
        if (!InspectHelpers.isTextualContentType(contentType) &&
            !InspectHelpers.looksLikeUtf8Text(body)
        ) {
            return false
        }
        return try {
            String(body, Charsets.UTF_8).lowercase(Locale.US).contains(q)
        } catch (_: Exception) {
            false
        }
    }
}
