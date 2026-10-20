package com.victorious.browser.inspect

import org.json.JSONObject
import java.util.Base64
import java.util.Locale

/**
 * Developer inspection helpers over captured traffic. These operate on the
 * developer's OWN dashboards' traffic, surfaced for local debugging only.
 *
 * JWT handling deliberately decodes header + payload only (base64url) and
 * never verifies signatures — every decoded result is labeled as such.
 */
object InspectHelpers {

    data class ParsedCookie(
        val name: String,
        val value: String,
        /** Origin: "Cookie" (request) or "Set-Cookie" (response). */
        val source: String,
        /** Set-Cookie attributes (Path, Domain, Secure, …); empty for Cookie header pairs. */
        val attributes: Map<String, String>,
    )

    data class DecodedJwt(
        val raw: String,
        val header: String?,
        val payload: String?,
        /** True when both header and payload base64url-decoded successfully. */
        val decodedOk: Boolean,
        /** Always false — this tool never verifies JWT signatures. */
        val signatureVerified: Boolean = false,
        /** Where the token was found (e.g. "Authorization", "cookie:session", "request-body"). */
        val source: String,
        /** Flattened claim map from the payload JSON when parseable. */
        val claims: Map<String, String> = emptyMap(),
    )

    data class BodyPreview(
        val contentType: String?,
        val isText: Boolean,
        val text: String?,
        val hexPreview: String?,
        val byteLength: Int,
        val truncated: Boolean,
    )

    // --- Cookies ----------------------------------------------------------------

    /**
     * Parse Cookie and Set-Cookie headers into name/value (+ attributes for Set-Cookie).
     */
    fun parseCookies(headers: List<CapturedHeader>): List<ParsedCookie> {
        val result = mutableListOf<ParsedCookie>()
        for (h in headers) {
            when (h.name.lowercase(Locale.US)) {
                "cookie" -> result += parseCookieHeader(h.value)
                "set-cookie" -> parseSetCookie(h.value)?.let { result += it }
            }
        }
        return result
    }

    /** @deprecated Prefer [parseCookies]; kept for callers that only need name/value. */
    fun cookies(headers: List<CapturedHeader>): List<ParsedCookie> = parseCookies(headers)

    fun parseCookieHeader(value: String): List<ParsedCookie> {
        val out = mutableListOf<ParsedCookie>()
        value.split(";").forEach { part ->
            val trimmed = part.trim()
            if (trimmed.isEmpty()) return@forEach
            val eq = trimmed.indexOf('=')
            if (eq > 0) {
                out += ParsedCookie(
                    name = trimmed.substring(0, eq).trim(),
                    value = trimmed.substring(eq + 1).trim(),
                    source = "Cookie",
                    attributes = emptyMap(),
                )
            }
        }
        return out
    }

    fun parseSetCookie(value: String): ParsedCookie? {
        val segments = value.split(";").map { it.trim() }.filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null
        val first = segments[0]
        val eq = first.indexOf('=')
        if (eq <= 0) return null
        val name = first.substring(0, eq).trim()
        val cookieValue = first.substring(eq + 1).trim()
        val attrs = linkedMapOf<String, String>()
        for (i in 1 until segments.size) {
            val seg = segments[i]
            val aEq = seg.indexOf('=')
            if (aEq > 0) {
                attrs[seg.substring(0, aEq).trim()] = seg.substring(aEq + 1).trim()
            } else {
                // Flags like Secure / HttpOnly
                attrs[seg] = ""
            }
        }
        return ParsedCookie(name = name, value = cookieValue, source = "Set-Cookie", attributes = attrs)
    }

    // --- JWT --------------------------------------------------------------------

    /** Extract the bearer token value from an Authorization header, if present. */
    fun bearerToken(headers: List<CapturedHeader>): String? {
        val auth = headers.firstOrNull { it.name.equals("Authorization", ignoreCase = true) }?.value
            ?: return null
        val prefix = "Bearer "
        return if (auth.regionMatches(0, prefix, 0, prefix.length, ignoreCase = true)) {
            auth.substring(prefix.length).trim()
        } else {
            null
        }
    }

    /**
     * Decode a JWT's header and payload with base64url (NO signature verification).
     * Returns null if the string is not a well-formed three-part JWT.
     */
    fun decodeJwt(token: String?, source: String = "unknown"): DecodedJwt? {
        if (token.isNullOrBlank()) return null
        val parts = token.trim().split(".")
        if (parts.size != 3) return null
        // Reject obviously non-JWT segments (too short / whitespace).
        if (parts.any { it.isEmpty() || it.any(Char::isWhitespace) }) return null
        val headerRaw = decodeSegment(parts[0]) ?: return null
        val payloadRaw = decodeSegment(parts[1]) ?: return null
        // Require JSON-looking header (must contain "alg" typically).
        if (!looksLikeJsonObject(headerRaw)) return null
        val claims = extractClaims(payloadRaw)
        return DecodedJwt(
            raw = token.trim(),
            header = prettyJsonOrRaw(headerRaw),
            payload = prettyJsonOrRaw(payloadRaw),
            decodedOk = true,
            signatureVerified = false,
            source = source,
            claims = claims,
        )
    }

    /** Convenience: pull a bearer out of headers and decode it as a JWT. */
    fun decodeBearerJwt(headers: List<CapturedHeader>): DecodedJwt? =
        decodeJwt(bearerToken(headers), source = "Authorization")

    /**
     * Scan Authorization, Cookie / Set-Cookie values, and request/response body
     * text for JWT-looking tokens; decode each (no signature check).
     */
    fun findJwts(
        requestHeaders: List<CapturedHeader>,
        responseHeaders: List<CapturedHeader> = emptyList(),
        requestBody: ByteArray? = null,
        responseBody: ByteArray? = null,
        requestContentType: String? = null,
        responseContentType: String? = null,
    ): List<DecodedJwt> {
        val found = linkedMapOf<String, DecodedJwt>() // dedupe by raw token

        fun accept(token: String?, source: String) {
            val jwt = decodeJwt(token, source) ?: return
            found.putIfAbsent(jwt.raw, jwt)
        }

        accept(bearerToken(requestHeaders), "Authorization")

        // Non-Bearer Authorization that is itself a JWT
        requestHeaders.firstOrNull { it.name.equals("Authorization", ignoreCase = true) }?.value?.let { auth ->
            val trimmed = auth.trim()
            if (!trimmed.regionMatches(0, "Bearer ", 0, 7, ignoreCase = true)) {
                accept(trimmed, "Authorization")
            }
        }

        parseCookies(requestHeaders + responseHeaders).forEach { c ->
            accept(c.value, "${c.source.lowercase(Locale.US)}:${c.name}")
            // Also scan cookie value for an embedded JWT substring
            findJwtCandidates(c.value).forEach { accept(it, "${c.source.lowercase(Locale.US)}:${c.name}") }
        }

        bodyAsSearchText(requestBody, requestContentType)?.let { text ->
            findJwtCandidates(text).forEach { accept(it, "request-body") }
        }
        bodyAsSearchText(responseBody, responseContentType)?.let { text ->
            findJwtCandidates(text).forEach { accept(it, "response-body") }
        }

        return found.values.toList()
    }

    /**
     * Heuristic: three base64url segments separated by dots.
     * Conservative length bounds to avoid matching arbitrary dotted strings.
     */
    fun looksLikeJwt(token: String): Boolean {
        val parts = token.trim().split(".")
        if (parts.size != 3) return false
        if (parts.any { it.isEmpty() || it.length > 8_192 }) return false
        val b64url = Regex("^[A-Za-z0-9_-]+$")
        if (parts.any { !b64url.matches(it) }) return false
        // Header segment should decode to JSON with alg
        val header = decodeSegment(parts[0]) ?: return false
        return looksLikeJsonObject(header) && header.contains("\"alg\"")
    }

    fun findJwtCandidates(text: String): List<String> {
        // Token-ish runs of base64url chars with exactly two dots inside.
        val re = Regex("""[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""")
        return re.findAll(text).map { it.value }.filter { looksLikeJwt(it) }.distinct().toList()
    }

    // --- Body preview -----------------------------------------------------------

    fun bodyPreview(
        body: ByteArray?,
        contentType: String?,
        truncated: Boolean,
        maxChars: Int = 16_384,
    ): BodyPreview? {
        if (body == null) return null
        val textLike = isTextualContentType(contentType) || looksLikeUtf8Text(body)
        return if (textLike) {
            val raw = try {
                String(body, Charsets.UTF_8)
            } catch (_: Exception) {
                null
            }
            BodyPreview(
                contentType = contentType,
                isText = true,
                text = raw?.take(maxChars),
                hexPreview = null,
                byteLength = body.size,
                truncated = truncated || (raw != null && raw.length > maxChars),
            )
        } else {
            BodyPreview(
                contentType = contentType,
                isText = false,
                text = null,
                hexPreview = toHexPreview(body, maxBytes = 256),
                byteLength = body.size,
                truncated = truncated,
            )
        }
    }

    fun isTextualContentType(contentType: String?): Boolean {
        if (contentType.isNullOrBlank()) return false
        val ct = contentType.lowercase(Locale.US).substringBefore(';').trim()
        return ct.startsWith("text/") ||
            ct == "application/json" ||
            ct == "application/javascript" ||
            ct == "application/xml" ||
            ct == "application/xhtml+xml" ||
            ct == "application/x-www-form-urlencoded" ||
            ct == "application/graphql" ||
            ct.endsWith("+json") ||
            ct.endsWith("+xml")
    }

    fun looksLikeUtf8Text(bytes: ByteArray, sample: Int = 512): Boolean {
        if (bytes.isEmpty()) return true
        val n = minOf(bytes.size, sample)
        var printable = 0
        for (i in 0 until n) {
            val b = bytes[i].toInt() and 0xFF
            when {
                b == 0 -> return false // NUL → binary
                b == 9 || b == 10 || b == 13 -> printable++
                b in 32..126 -> printable++
                b >= 128 -> printable++ // allow UTF-8 high bytes
            }
        }
        return printable.toDouble() / n >= 0.85
    }

    fun toHexPreview(bytes: ByteArray, maxBytes: Int = 256): String {
        val n = minOf(bytes.size, maxBytes)
        val sb = StringBuilder(n * 3)
        for (i in 0 until n) {
            if (i > 0) {
                if (i % 16 == 0) sb.append('\n') else sb.append(' ')
            }
            sb.append(String.format(Locale.US, "%02x", bytes[i].toInt() and 0xFF))
        }
        if (bytes.size > maxBytes) sb.append("\n… (").append(bytes.size - maxBytes).append(" more bytes)")
        return sb.toString()
    }

    // --- internals --------------------------------------------------------------

    private fun bodyAsSearchText(body: ByteArray?, contentType: String?): String? {
        if (body == null || body.isEmpty()) return null
        if (!isTextualContentType(contentType) && !looksLikeUtf8Text(body)) return null
        return try {
            String(body, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeSegment(seg: String): String? = try {
        val decoder = Base64.getUrlDecoder()
        // java.util.Base64 URL decoder tolerates missing padding.
        String(decoder.decode(seg), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }

    private fun looksLikeJsonObject(s: String): Boolean {
        val t = s.trim()
        return t.startsWith("{") && t.endsWith("}")
    }

    private fun prettyJsonOrRaw(s: String): String = try {
        JSONObject(s).toString(2)
    } catch (_: Exception) {
        s
    }

    private fun extractClaims(payloadRaw: String): Map<String, String> = try {
        val obj = JSONObject(payloadRaw)
        val out = linkedMapOf<String, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            out[k] = obj.opt(k)?.toString() ?: "null"
        }
        out
    } catch (_: Exception) {
        emptyMap()
    }
}
