package com.victorious.browser.export

/**
 * Pure secret-stripping logic for fleet exports.
 *
 * Mirrors the config-export rule used elsewhere in the app: an export must NEVER
 * carry cookies, passwords, proxy credentials, or certificate/key material. This
 * object centralizes the deny-lists and the cleaning functions so they can be
 * unit-tested without Android.
 *
 * Stripping is by header/param NAME (case-insensitive) and is conservative: any
 * name that looks credential-bearing is dropped entirely rather than masked, so
 * no secret value ever leaves the device in an export payload.
 */
object SecretStripper {

    /** Header names that must never appear in an export (lower-cased). */
    val SENSITIVE_HEADERS: Set<String> = setOf(
        "cookie",
        "set-cookie",
        "authorization",
        "proxy-authorization",
        "proxy-authenticate",
        "www-authenticate",
        "x-api-key",
        "x-auth-token",
        "x-amz-security-token",
        "x-csrf-token",
        "x-xsrf-token",
    )

    /**
     * Param / field name fragments that mark a value as a secret (lower-cased,
     * matched as a substring so e.g. "user_password" and "apiKey" both match).
     */
    val SENSITIVE_PARAM_FRAGMENTS: List<String> = listOf(
        "password",
        "passwd",
        "pwd",
        "secret",
        "token",
        "apikey",
        "api_key",
        "access_key",
        "private_key",
        "privatekey",
        "client_secret",
        "session",
        "cookie",
        "credential",
        "auth",
        "cert",
        "p12",
        "pfx",
        "keystore",
    )

    fun isSensitiveHeader(name: String): Boolean =
        SENSITIVE_HEADERS.contains(name.trim().lowercase())

    fun isSensitiveParam(name: String): Boolean {
        val n = name.trim().lowercase()
        if (n.isEmpty()) return false
        return SENSITIVE_PARAM_FRAGMENTS.any { n.contains(it) }
    }

    /** Return headers with every sensitive entry removed. Order preserved. */
    fun stripHeaders(headers: Map<String, String>): Map<String, String> =
        headers.filterKeys { !isSensitiveHeader(it) }

    /** Return params with every sensitive entry removed. Order preserved. */
    fun stripParams(params: Map<String, String>): Map<String, String> =
        params.filterKeys { !isSensitiveParam(it) }

    /**
     * Strip the query string of a URL of any sensitive params, keeping the rest
     * intact. Returns the cleaned URL. Fragment is preserved.
     */
    fun stripUrlQuery(url: String): String {
        val qIdx = url.indexOf('?')
        if (qIdx < 0) return url
        val base = url.substring(0, qIdx)
        val afterQ = url.substring(qIdx + 1)
        val fragIdx = afterQ.indexOf('#')
        val query = if (fragIdx >= 0) afterQ.substring(0, fragIdx) else afterQ
        val fragment = if (fragIdx >= 0) afterQ.substring(fragIdx) else ""
        if (query.isEmpty()) return url
        val kept = query.split('&').filter { pair ->
            if (pair.isEmpty()) return@filter false
            val name = pair.substringBefore('=')
            !isSensitiveParam(name)
        }
        return if (kept.isEmpty()) base + fragment
        else base + "?" + kept.joinToString("&") + fragment
    }

    /** Parse a URL query string into an ordered name→value map (first wins). */
    fun parseQuery(url: String): Map<String, String> {
        val qIdx = url.indexOf('?')
        if (qIdx < 0) return emptyMap()
        var query = url.substring(qIdx + 1)
        val fragIdx = query.indexOf('#')
        if (fragIdx >= 0) query = query.substring(0, fragIdx)
        if (query.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (pair in query.split('&')) {
            if (pair.isEmpty()) continue
            val name = pair.substringBefore('=')
            val value = if (pair.contains('=')) pair.substringAfter('=') else ""
            if (name.isNotEmpty() && !out.containsKey(name)) out[name] = value
        }
        return out
    }
}
