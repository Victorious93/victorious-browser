package com.victorious.browser.recon

import com.victorious.browser.inspect.InspectHelpers
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64

/**
 * Pure, Android-free text transforms for the Recon Workbench encoder/decoder.
 *
 * Every transform is two-way where it makes sense and takes text in → text out so
 * results can be chained (output of one fed to the next). Nothing here touches the
 * network, the filesystem, or Android APIs, so it is all unit-testable on the JVM.
 *
 * All transforms are offline and defensive: they only reshape text the operator
 * already holds (e.g. a token or param from their own authorized target's traffic).
 */
object EncoderTools {

    /** Result wrapper so the UI can show a clean error instead of throwing. */
    data class Result(val ok: Boolean, val output: String, val error: String? = null) {
        companion object {
            fun ok(s: String) = Result(true, s)
            fun fail(msg: String) = Result(false, "", msg)
        }
    }

    private fun guard(block: () -> String): Result = try {
        Result.ok(block())
    } catch (e: Exception) {
        Result.fail(e.message ?: e.javaClass.simpleName)
    }

    // --- Base64 (standard, with padding) ---------------------------------------

    fun base64Encode(input: String): Result = guard {
        Base64.getEncoder().encodeToString(input.toByteArray(Charsets.UTF_8))
    }

    fun base64Decode(input: String): Result = guard {
        String(Base64.getDecoder().decode(input.trim()), Charsets.UTF_8)
    }

    // --- Base64 URL-safe (no padding on encode; tolerant on decode) ------------

    fun base64UrlEncode(input: String): Result = guard {
        Base64.getUrlEncoder().withoutPadding().encodeToString(input.toByteArray(Charsets.UTF_8))
    }

    fun base64UrlDecode(input: String): Result = guard {
        // getUrlDecoder tolerates missing padding.
        String(Base64.getUrlDecoder().decode(input.trim()), Charsets.UTF_8)
    }

    // --- URL percent-encoding ---------------------------------------------------

    /** application/x-www-form-urlencoded style (space → '+'). */
    fun urlEncode(input: String): Result = guard {
        URLEncoder.encode(input, "UTF-8")
    }

    fun urlDecode(input: String): Result = guard {
        URLDecoder.decode(input, "UTF-8")
    }

    /** RFC 3986 component encoding (space → %20, keeps unreserved chars). */
    fun urlEncodeComponent(input: String): Result = guard {
        URLEncoder.encode(input, "UTF-8")
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")
    }

    // --- Hex --------------------------------------------------------------------

    fun hexEncode(input: String): Result = guard {
        val sb = StringBuilder()
        for (b in input.toByteArray(Charsets.UTF_8)) {
            sb.append(String.format("%02x", b.toInt() and 0xFF))
        }
        sb.toString()
    }

    fun hexDecode(input: String): Result = guard {
        val cleaned = input.filterNot { it.isWhitespace() || it == ':' || it == '-' }
        require(cleaned.length % 2 == 0) { "Hex input must have an even number of digits" }
        require(cleaned.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) { "Invalid hex digit" }
        val out = ByteArray(cleaned.length / 2)
        for (i in out.indices) {
            out[i] = cleaned.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        String(out, Charsets.UTF_8)
    }

    // --- HTML entities ----------------------------------------------------------

    private val htmlNamed = mapOf(
        "amp" to '&', "lt" to '<', "gt" to '>', "quot" to '"', "apos" to '\'',
        "nbsp" to '\u00A0', "copy" to '\u00A9', "reg" to '\u00AE', "euro" to '\u20AC',
    )

    /** Escape the five core characters plus non-ASCII as numeric entities. */
    fun htmlEntityEncode(input: String): Result = guard {
        val sb = StringBuilder()
        for (c in input) {
            when (c) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&#39;")
                else -> if (c.code > 126) sb.append("&#").append(c.code).append(';') else sb.append(c)
            }
        }
        sb.toString()
    }

    fun htmlEntityDecode(input: String): Result = guard {
        val re = Regex("&(#x?[0-9A-Fa-f]+|[A-Za-z][A-Za-z0-9]*);")
        re.replace(input) { m ->
            val body = m.groupValues[1]
            when {
                body.startsWith("#x") || body.startsWith("#X") ->
                    body.substring(2).toInt(16).toChar().toString()
                body.startsWith("#") ->
                    body.substring(1).toInt(10).toChar().toString()
                else -> htmlNamed[body]?.toString() ?: m.value
            }
        }
    }

    // --- ROT13 ------------------------------------------------------------------

    /** ROT13 is its own inverse. */
    fun rot13(input: String): Result = guard {
        buildString {
            for (c in input) {
                append(
                    when (c) {
                        in 'a'..'z' -> 'a' + ((c - 'a' + 13) % 26)
                        in 'A'..'Z' -> 'A' + ((c - 'A' + 13) % 26)
                        else -> c
                    },
                )
            }
        }
    }

    // --- Unicode escapes (\uXXXX) ----------------------------------------------

    /** Escape every non-ASCII char (and control chars) as \\uXXXX. */
    fun unicodeEscape(input: String): Result = guard {
        val sb = StringBuilder()
        for (c in input) {
            if (c.code in 32..126) sb.append(c)
            else sb.append("\\u").append(String.format("%04x", c.code))
        }
        sb.toString()
    }

    fun unicodeUnescape(input: String): Result = guard {
        val re = Regex("""\\u([0-9A-Fa-f]{4})""")
        re.replace(input) { m -> m.groupValues[1].toInt(16).toChar().toString() }
    }

    // --- JWT split/decode (reuses existing decoder; decode-not-verify) ---------

    data class JwtView(
        val header: String?,
        val payload: String?,
        val signature: String?,
        val decodedOk: Boolean,
        val signatureVerified: Boolean = false,
        val claims: Map<String, String> = emptyMap(),
        val error: String? = null,
    )

    /**
     * Split a JWT into its three segments and decode header + payload via the
     * shared [InspectHelpers.decodeJwt]. Signatures are NEVER verified.
     */
    fun jwtDecode(token: String): JwtView {
        val trimmed = token.trim()
        val parts = trimmed.split(".")
        val sig = if (parts.size >= 3) parts[2] else null
        val decoded = InspectHelpers.decodeJwt(trimmed, source = "recon-workbench")
            ?: return JwtView(
                header = null, payload = null, signature = sig,
                decodedOk = false, error = "Not a well-formed three-part JWT",
            )
        return JwtView(
            header = decoded.header,
            payload = decoded.payload,
            signature = sig,
            decodedOk = decoded.decodedOk,
            signatureVerified = false,
            claims = decoded.claims,
        )
    }
}
