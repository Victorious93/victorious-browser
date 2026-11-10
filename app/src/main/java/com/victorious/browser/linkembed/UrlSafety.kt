package com.victorious.browser.linkembed

import java.net.URI

/**
 * Scheme / shape gate for every URL the link-embedding feature touches (preview
 * fetches, redirects, oEmbed endpoints, images, iframe sources, copied snippets).
 *
 * Only absolute `http` and `https` URLs with a host pass. Everything else —
 * `javascript:`, `data:`, `file:`, `content:`, `intent:`, `blob:`, `about:`,
 * scheme-relative `//host`, relative paths, URLs carrying `user:pass@` credentials,
 * control characters or absurd lengths — is rejected. Pure Kotlin (unit-tested).
 */
object UrlSafety {
    const val MAX_URL_LENGTH = 4096

    sealed class Result {
        data class Ok(val url: String) : Result()
        data class Rejected(val reason: String) : Result()
    }

    fun check(raw: String?): Result {
        if (raw == null) return Result.Rejected("No URL")
        val s = raw.trim()
        if (s.isEmpty()) return Result.Rejected("No URL")
        if (s.length > MAX_URL_LENGTH) return Result.Rejected("URL longer than $MAX_URL_LENGTH characters")
        if (s.any { it.code < 0x20 || it.code == 0x7F }) return Result.Rejected("URL contains control characters")
        val schemeEnd = s.indexOf(':')
        if (schemeEnd <= 0) return Result.Rejected("Not an absolute http(s) URL")
        val scheme = s.substring(0, schemeEnd).lowercase()
        if (scheme != "http" && scheme != "https") return Result.Rejected("Blocked scheme \"$scheme:\" (only http and https are allowed)")
        if (!s.substring(schemeEnd).startsWith("://")) return Result.Rejected("Malformed http(s) URL")
        val uri = try {
            URI(s)
        } catch (_: Exception) {
            // Tolerate common unescaped characters (spaces, |, ^) by re-trying with them encoded.
            try { URI(lenientEncode(s)) } catch (_: Exception) { return Result.Rejected("Malformed URL") }
        }
        val host = uri.host ?: uri.rawAuthority?.substringAfterLast('@')?.substringBefore(':')
        if (host.isNullOrBlank()) return Result.Rejected("URL has no host")
        if (uri.rawUserInfo != null) return Result.Rejected("URLs with embedded credentials (user:pass@) are not allowed")
        val normalizedScheme = scheme + s.substring(schemeEnd)
        return Result.Ok(normalizedScheme)
    }

    /** The sanitized URL, or null when [check] rejects it. */
    fun sanitize(raw: String?): String? = (check(raw) as? Result.Ok)?.url

    fun isAllowed(raw: String?): Boolean = check(raw) is Result.Ok

    /** Resolves [ref] against [base] and re-checks the result. Returns null when unsafe. */
    fun resolve(base: String, ref: String?): String? {
        val r = ref?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val resolved = try {
            URI(lenientEncode(base)).resolve(lenientEncode(r)).toString()
        } catch (_: Exception) {
            return null
        }
        return sanitize(resolved)
    }

    fun hostOf(url: String?): String? = try {
        url?.let { URI(lenientEncode(it.trim())).host?.lowercase() }
    } catch (_: Exception) {
        null
    }

    /** `scheme://host[:port]` for an http(s) URL, or null. */
    fun originOf(url: String?): String? = try {
        val u = URI(lenientEncode(url?.trim() ?: return null))
        val scheme = u.scheme?.lowercase() ?: return null
        val host = u.host?.lowercase() ?: return null
        val defaultPort = if (scheme == "https") 443 else 80
        if (u.port == -1 || u.port == defaultPort) "$scheme://$host" else "$scheme://$host:${u.port}"
    } catch (_: Exception) {
        null
    }

    private fun lenientEncode(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            ' ' -> append("%20")
            '|' -> append("%7C")
            '^' -> append("%5E")
            '"' -> append("%22")
            '<' -> append("%3C")
            '>' -> append("%3E")
            '\\' -> append("%5C")
            '{' -> append("%7B")
            '}' -> append("%7D")
            '`' -> append("%60")
            else -> append(c)
        }
    }
}
