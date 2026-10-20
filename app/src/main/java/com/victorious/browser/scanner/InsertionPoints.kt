package com.victorious.browser.scanner

import com.victorious.browser.inspect.CapturedHeader
import com.victorious.browser.inspect.EditableRequest
import java.net.URI

/**
 * Enumerates insertion points of a request (query params, form-body params,
 * selected headers) and produces a mutated [EditableRequest] with one point's
 * value replaced by a payload. Pure; unit-tested in [InsertionPointsTest].
 */
object InsertionPoints {

    enum class Kind { QUERY, BODY, HEADER }

    data class Point(val kind: Kind, val name: String, val originalValue: String)

    /** Headers worth fuzzing for injection; others left alone to avoid breakage. */
    private val INJECTABLE_HEADERS = setOf("user-agent", "referer", "x-forwarded-for", "cookie")

    fun enumerate(req: EditableRequest): List<Point> {
        val points = mutableListOf<Point>()
        // Query params
        for (p in parseQuery(req.url)) {
            points += Point(Kind.QUERY, p.first, p.second)
        }
        // Body params (only urlencoded forms)
        if (isFormUrlEncoded(req)) {
            for (p in parseQueryString(req.bodyAsUtf8())) {
                points += Point(Kind.BODY, p.first, p.second)
            }
        }
        // Selected headers
        for (h in req.headers) {
            if (h.name.lowercase() in INJECTABLE_HEADERS) {
                points += Point(Kind.HEADER, h.name, h.value)
            }
        }
        return points
    }

    /** Return a new request with [point] set to [payload] (replacing value). */
    fun apply(req: EditableRequest, point: Point, payload: String): EditableRequest {
        return when (point.kind) {
            Kind.QUERY -> req.copy(url = replaceQueryParam(req.url, point.name, payload))
            Kind.BODY -> {
                val newBody = replaceParamInQueryString(req.bodyAsUtf8(), point.name, payload)
                req.copy(body = newBody.toByteArray(Charsets.UTF_8))
            }
            Kind.HEADER -> {
                val newHeaders = req.headers.map {
                    if (it.name.equals(point.name, ignoreCase = true)) CapturedHeader(it.name, payload) else it
                }
                req.copy(headers = newHeaders)
            }
        }
    }

    fun isFormUrlEncoded(req: EditableRequest): Boolean {
        val ct = req.headers.firstOrNull { it.name.equals("Content-Type", true) }?.value?.lowercase()
        return ct?.contains("application/x-www-form-urlencoded") == true && req.body != null
    }

    fun parseQuery(url: String): List<Pair<String, String>> {
        val q = try {
            URI(url).rawQuery
        } catch (_: Exception) {
            null
        } ?: return emptyList()
        return parseQueryString(q)
    }

    fun parseQueryString(qs: String): List<Pair<String, String>> {
        if (qs.isEmpty()) return emptyList()
        return qs.split('&').filter { it.isNotEmpty() }.map {
            val idx = it.indexOf('=')
            if (idx < 0) it to "" else it.substring(0, idx) to it.substring(idx + 1)
        }
    }

    fun replaceQueryParam(url: String, name: String, value: String): String {
        return try {
            val u = URI(url)
            val rawQuery = u.rawQuery ?: return url
            val newQuery = replaceParamInQueryString(rawQuery, name, value)
            val base = url.substringBefore('?')
            val frag = u.rawFragment?.let { "#$it" } ?: ""
            "$base?$newQuery$frag"
        } catch (_: Exception) {
            url
        }
    }

    fun replaceParamInQueryString(qs: String, name: String, value: String): String {
        val encoded = urlEncode(value)
        var replaced = false
        val parts = qs.split('&').map { part ->
            val idx = part.indexOf('=')
            val key = if (idx < 0) part else part.substring(0, idx)
            if (key == name) {
                replaced = true
                "$key=$encoded"
            } else {
                part
            }
        }
        return if (replaced) parts.joinToString("&") else qs
    }

    private fun urlEncode(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8")
}
