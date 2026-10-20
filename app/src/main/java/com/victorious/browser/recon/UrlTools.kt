package com.victorious.browser.recon

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Pure, Android-free URL parsing / rebuilding helpers for the Recon Workbench.
 *
 * Parse a URL into its parts, edit/add/remove query params and rebuild, split and
 * decode a raw query string, and normalize. All offline string work — no network.
 */
object UrlTools {

    /** One query parameter. Values are stored DECODED. */
    data class Param(val name: String, val value: String)

    data class ParsedUrl(
        val scheme: String?,
        val userInfo: String?,
        val host: String?,
        /** -1 when no explicit port. */
        val port: Int,
        val path: String,
        val params: List<Param>,
        val fragment: String?,
        val rawQuery: String?,
        val error: String? = null,
    )

    /**
     * Parse [url] into components. Falls back gracefully: a parse failure returns a
     * [ParsedUrl] with [error] set rather than throwing.
     */
    fun parse(url: String): ParsedUrl {
        val trimmed = url.trim()
        return try {
            val uri = URI(trimmed)
            val rawQuery = uri.rawQuery
            ParsedUrl(
                scheme = uri.scheme,
                userInfo = uri.userInfo,
                host = uri.host,
                port = uri.port,
                path = uri.rawPath ?: "",
                params = if (rawQuery.isNullOrEmpty()) emptyList() else splitQuery(rawQuery),
                fragment = uri.rawFragment,
                rawQuery = rawQuery,
            )
        } catch (e: Exception) {
            ParsedUrl(null, null, null, -1, "", emptyList(), null, null, e.message ?: "parse error")
        }
    }

    /**
     * Split a raw query string ("a=1&b=2&c") into decoded name/value pairs.
     * A key with no '=' yields a Param with an empty value.
     */
    fun splitQuery(rawQuery: String): List<Param> {
        val q = rawQuery.removePrefix("?")
        if (q.isEmpty()) return emptyList()
        return q.split("&").filter { it.isNotEmpty() }.map { pair ->
            val eq = pair.indexOf('=')
            if (eq < 0) {
                Param(decode(pair), "")
            } else {
                Param(decode(pair.substring(0, eq)), decode(pair.substring(eq + 1)))
            }
        }
    }

    /** Build an encoded query string from decoded params (no leading '?'). */
    fun buildQuery(params: List<Param>): String =
        params.joinToString("&") { p ->
            if (p.value.isEmpty()) encode(p.name) else "${encode(p.name)}=${encode(p.value)}"
        }

    // --- Param editing (immutable helpers) --------------------------------------

    /** Return params with every occurrence of [name] set to [value] (adds if absent). */
    fun setParam(params: List<Param>, name: String, value: String): List<Param> {
        if (params.none { it.name == name }) return params + Param(name, value)
        return params.map { if (it.name == name) it.copy(value = value) else it }
    }

    fun addParam(params: List<Param>, name: String, value: String): List<Param> =
        params + Param(name, value)

    fun removeParam(params: List<Param>, name: String): List<Param> =
        params.filterNot { it.name == name }

    fun getParam(params: List<Param>, name: String): List<String> =
        params.filter { it.name == name }.map { it.value }

    // --- Rebuild ----------------------------------------------------------------

    /** Rebuild a URL string from components. */
    fun build(parsed: ParsedUrl): String {
        val sb = StringBuilder()
        parsed.scheme?.let { sb.append(it).append("://") }
        parsed.userInfo?.let { sb.append(it).append('@') }
        parsed.host?.let { sb.append(it) }
        if (parsed.port >= 0) sb.append(':').append(parsed.port)
        sb.append(parsed.path)
        val query = buildQuery(parsed.params)
        if (query.isNotEmpty()) sb.append('?').append(query)
        parsed.fragment?.let { sb.append('#').append(it) }
        return sb.toString()
    }

    // --- Normalization ----------------------------------------------------------

    /**
     * Normalize a URL: lowercase scheme + host, drop a default port (80/http,
     * 443/https), ensure a root path, collapse dot segments, and re-encode the
     * query consistently. Returns the input unchanged if it cannot be parsed.
     */
    fun normalize(url: String): String {
        val parsed = parse(url)
        if (parsed.error != null || parsed.scheme == null) return url.trim()
        val scheme = parsed.scheme.lowercase()
        val host = parsed.host?.lowercase()
        val port = when {
            parsed.port == 80 && scheme == "http" -> -1
            parsed.port == 443 && scheme == "https" -> -1
            else -> parsed.port
        }
        val path = collapseDotSegments(parsed.path).ifEmpty { if (host != null) "/" else "" }
        return build(
            parsed.copy(scheme = scheme, host = host, port = port, path = path),
        )
    }

    /** RFC 3986 §5.2.4 remove_dot_segments (operates on the path only). */
    fun collapseDotSegments(path: String): String {
        if (path.isEmpty()) return path
        val absolute = path.startsWith("/")
        val trailingSlash = path.endsWith("/") && path.length > 1
        val out = ArrayDeque<String>()
        for (seg in path.split("/")) {
            when (seg) {
                "", "." -> {}
                ".." -> if (out.isNotEmpty()) out.removeLast()
                else -> out.addLast(seg)
            }
        }
        val joined = out.joinToString("/")
        val sb = StringBuilder()
        if (absolute) sb.append('/')
        sb.append(joined)
        if (trailingSlash && joined.isNotEmpty()) sb.append('/')
        return sb.toString().ifEmpty { if (absolute) "/" else "" }
    }

    // --- internals --------------------------------------------------------------

    private fun decode(s: String): String = try {
        URLDecoder.decode(s, "UTF-8")
    } catch (_: Exception) {
        s
    }

    private fun encode(s: String): String =
        URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
