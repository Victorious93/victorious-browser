package com.victorious.browser.recon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class EncoderToolsTest {

    @Test
    fun base64_roundTrip() {
        val plain = "hello, world — ünïcödé ✓"
        val enc = EncoderTools.base64Encode(plain)
        assertTrue(enc.ok)
        assertEquals(
            Base64.getEncoder().encodeToString(plain.toByteArray(Charsets.UTF_8)),
            enc.output,
        )
        val dec = EncoderTools.base64Decode(enc.output)
        assertTrue(dec.ok)
        assertEquals(plain, dec.output)
    }

    @Test
    fun base64Decode_invalid_returnsError() {
        val dec = EncoderTools.base64Decode("!!!not base64!!!")
        assertFalse(dec.ok)
        assertEquals("", dec.output)
    }

    @Test
    fun base64Url_roundTrip_noPadding() {
        val plain = "subjects?are=fun&and=urlsafe"
        val enc = EncoderTools.base64UrlEncode(plain)
        assertTrue(enc.ok)
        assertFalse("url-safe encode should be padless", enc.output.contains("="))
        assertFalse(enc.output.contains("+"))
        assertFalse(enc.output.contains("/"))
        val dec = EncoderTools.base64UrlDecode(enc.output)
        assertTrue(dec.ok)
        assertEquals(plain, dec.output)
    }

    @Test
    fun url_roundTrip() {
        val plain = "a b&c=d+e/f?g"
        val enc = EncoderTools.urlEncode(plain)
        assertTrue(enc.ok)
        assertTrue(enc.output.contains("+") || enc.output.contains("%20"))
        val dec = EncoderTools.urlDecode(enc.output)
        assertTrue(dec.ok)
        assertEquals(plain, dec.output)
    }

    @Test
    fun urlComponent_usesPercent20AndKeepsTilde() {
        val enc = EncoderTools.urlEncodeComponent("a b~c*")
        assertTrue(enc.ok)
        assertEquals("a%20b~c%2A", enc.output)
    }

    @Test
    fun hex_roundTrip_andKnownVector() {
        val enc = EncoderTools.hexEncode("ABC")
        assertTrue(enc.ok)
        assertEquals("414243", enc.output)
        val dec = EncoderTools.hexDecode("41 42:43")
        assertTrue(dec.ok)
        assertEquals("ABC", dec.output)
    }

    @Test
    fun hexDecode_oddLength_fails() {
        val dec = EncoderTools.hexDecode("4142F")
        assertFalse(dec.ok)
    }

    @Test
    fun hexDecode_invalidDigit_fails() {
        val dec = EncoderTools.hexDecode("41ZZ")
        assertFalse(dec.ok)
    }

    @Test
    fun html_entityRoundTrip() {
        val plain = "<a href=\"x\">T&D's ©</a>"
        val enc = EncoderTools.htmlEntityEncode(plain)
        assertTrue(enc.ok)
        assertTrue(enc.output.contains("&lt;"))
        assertTrue(enc.output.contains("&amp;"))
        assertTrue(enc.output.contains("&quot;"))
        assertTrue(enc.output.contains("&#39;"))
        val dec = EncoderTools.htmlEntityDecode(enc.output)
        assertTrue(dec.ok)
        assertEquals(plain, dec.output)
    }

    @Test
    fun htmlDecode_handlesNamedDecimalAndHex() {
        val dec = EncoderTools.htmlEntityDecode("&amp;&#65;&#x42;&nbsp;")
        assertTrue(dec.ok)
        assertEquals("&AB\u00A0", dec.output)
    }

    @Test
    fun htmlDecode_leavesUnknownEntityIntact() {
        val dec = EncoderTools.htmlEntityDecode("&notareal;")
        assertTrue(dec.ok)
        assertEquals("&notareal;", dec.output)
    }

    @Test
    fun rot13_isItsOwnInverse() {
        val plain = "Hello, World 123!"
        val once = EncoderTools.rot13(plain)
        assertTrue(once.ok)
        assertEquals("Uryyb, Jbeyq 123!", once.output)
        val twice = EncoderTools.rot13(once.output)
        assertEquals(plain, twice.output)
    }

    @Test
    fun unicodeEscape_roundTrip() {
        val plain = "A→Z ✓ café"
        val enc = EncoderTools.unicodeEscape(plain)
        assertTrue(enc.ok)
        assertTrue(enc.output.contains("\\u2192"))
        assertTrue(enc.output.startsWith("A"))
        val dec = EncoderTools.unicodeUnescape(enc.output)
        assertTrue(dec.ok)
        assertEquals(plain, dec.output)
    }

    @Test
    fun jwt_decodesHeaderPayload_neverVerifies() {
        fun enc(s: String) =
            Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray(Charsets.UTF_8))
        val token = "${enc("""{"alg":"HS256","typ":"JWT"}""")}.${enc("""{"sub":"u1","role":"admin"}""")}.sigpart"
        val view = EncoderTools.jwtDecode(token)
        assertTrue(view.decodedOk)
        assertFalse(view.signatureVerified)
        assertEquals("sigpart", view.signature)
        assertTrue(view.header!!.contains("HS256"))
        assertTrue(view.payload!!.contains("u1"))
        assertEquals("admin", view.claims["role"])
    }

    @Test
    fun jwt_malformed_reportsError() {
        val view = EncoderTools.jwtDecode("not.a.jwt.token")
        assertFalse(view.decodedOk)
        assertFalse(view.signatureVerified)
    }
}
