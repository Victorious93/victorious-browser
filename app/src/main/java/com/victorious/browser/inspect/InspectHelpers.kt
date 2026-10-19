package com.victorious.browser.inspect

import android.util.Base64
import org.json.JSONObject

/**
 * Developer inspection helpers over captured traffic. These operate on the
 * developer's OWN dashboards' traffic, surfaced for local debugging only.
 */
object InspectHelpers {

    data class Cookie(val name: String, val value: String)

    data class DecodedJwt(
        val raw: String,
        val header: String?,
        val payload: String?,
        val valid: Boolean,
    )

    /** Parse all Cookie / Set-Cookie pairs out of a header list. */
    fun cookies(headers: List<CapturedHeader>): List<Cookie> {
        val result = mutableListOf<Cookie>()
        for (h in headers) {
            when (h.name.lowercase()) {
                "cookie" -> {
                    // Multiple "name=value" pairs separated by "; "
                    h.value.split(";").forEach { part ->
                        val eq = part.indexOf('=')
                        if (eq > 0) {
                            result += Cookie(part.substring(0, eq).trim(), part.substring(eq + 1).trim())
                        }
                    }
                }
                "set-cookie" -> {
                    // First token before ';' is the actual cookie; rest are attributes.
                    val first = h.value.substringBefore(';')
                    val eq = first.indexOf('=')
                    if (eq > 0) {
                        result += Cookie(first.substring(0, eq).trim(), first.substring(eq + 1).trim())
                    }
                }
            }
        }
        return result
    }

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
    fun decodeJwt(token: String?): DecodedJwt? {
        if (token.isNullOrBlank()) return null
        val parts = token.split(".")
        if (parts.size != 3) return null
        val header = decodeSegment(parts[0])
        val payload = decodeSegment(parts[1])
        if (header == null && payload == null) return null
        return DecodedJwt(
            raw = token,
            header = header?.let { prettyJsonOrRaw(it) },
            payload = payload?.let { prettyJsonOrRaw(it) },
            valid = header != null && payload != null,
        )
    }

    /** Convenience: pull a bearer out of headers and decode it as a JWT. */
    fun decodeBearerJwt(headers: List<CapturedHeader>): DecodedJwt? =
        decodeJwt(bearerToken(headers))

    private fun decodeSegment(seg: String): String? = try {
        val flags = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        String(Base64.decode(seg, flags), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }

    private fun prettyJsonOrRaw(s: String): String = try {
        JSONObject(s).toString(2)
    } catch (_: Exception) {
        s
    }
}
