package com.victorious.browser.inspect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class JwtDecodeTest {

    /** Build a unsigned-looking JWT (header.payload.sig) with URL-safe base64. */
    private fun makeJwt(headerJson: String, payloadJson: String, sig: String = "sig"): String {
        fun enc(s: String): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray(Charsets.UTF_8))
        return "${enc(headerJson)}.${enc(payloadJson)}.$sig"
    }

    private val sampleHeader = """{"alg":"HS256","typ":"JWT"}"""
    private val samplePayload = """{"sub":"user-1","role":"admin","iat":1516239022}"""

    @Test
    fun decodeJwt_decodesHeaderAndPayload_withoutVerifying() {
        val token = makeJwt(sampleHeader, samplePayload)
        val jwt = InspectHelpers.decodeJwt(token, source = "test")
        assertNotNull(jwt)
        assertTrue(jwt!!.decodedOk)
        assertFalse(jwt.signatureVerified)
        assertEquals("test", jwt.source)
        assertTrue(jwt.header!!.contains("HS256"))
        assertTrue(jwt.payload!!.contains("user-1"))
        assertEquals("user-1", jwt.claims["sub"])
        assertEquals("admin", jwt.claims["role"])
        assertEquals("1516239022", jwt.claims["iat"])
    }

    @Test
    fun decodeJwt_rejectsMalformed() {
        assertNull(InspectHelpers.decodeJwt(null))
        assertNull(InspectHelpers.decodeJwt(""))
        assertNull(InspectHelpers.decodeJwt("not.a.jwt"))
        assertNull(InspectHelpers.decodeJwt("only.two"))
        assertNull(InspectHelpers.decodeJwt("a.b.c.d"))
    }

    @Test
    fun looksLikeJwt_requiresAlgInHeader() {
        val good = makeJwt(sampleHeader, samplePayload)
        assertTrue(InspectHelpers.looksLikeJwt(good))
        val noAlg = makeJwt("""{"typ":"JWT"}""", samplePayload)
        assertFalse(InspectHelpers.looksLikeJwt(noAlg))
        assertFalse(InspectHelpers.looksLikeJwt("foo.bar.baz"))
    }

    @Test
    fun bearerToken_extractsValue() {
        val headers = listOf(CapturedHeader("Authorization", "Bearer tok123"))
        assertEquals("tok123", InspectHelpers.bearerToken(headers))
        assertNull(InspectHelpers.bearerToken(listOf(CapturedHeader("Authorization", "Basic x"))))
    }

    @Test
    fun findJwts_fromAuthorizationCookieAndBody() {
        val token = makeJwt(sampleHeader, samplePayload)
        val reqHeaders = listOf(
            CapturedHeader("Authorization", "Bearer $token"),
            CapturedHeader("Cookie", "session=abc; id_token=$token"),
        )
        val body = """{"access_token":"$token","ok":true}""".toByteArray()
        val found = InspectHelpers.findJwts(
            requestHeaders = reqHeaders,
            requestBody = body,
            requestContentType = "application/json",
        )
        // Deduped by raw token — Authorization + cookie + body all same token → 1
        assertEquals(1, found.size)
        assertEquals("Authorization", found[0].source)
        assertEquals("user-1", found[0].claims["sub"])
    }

    @Test
    fun findJwts_detectsDistinctTokens() {
        val t1 = makeJwt(sampleHeader, """{"sub":"a"}""")
        val t2 = makeJwt(sampleHeader, """{"sub":"b"}""")
        val found = InspectHelpers.findJwts(
            requestHeaders = listOf(CapturedHeader("Authorization", "Bearer $t1")),
            responseHeaders = listOf(CapturedHeader("Set-Cookie", "refresh=$t2; Path=/; HttpOnly")),
        )
        assertEquals(2, found.size)
        val subs = found.map { it.claims["sub"] }.toSet()
        assertEquals(setOf("a", "b"), subs)
    }

    @Test
    fun findJwtCandidates_inFreeText() {
        val token = makeJwt(sampleHeader, samplePayload)
        val text = "prefix $token suffix"
        val candidates = InspectHelpers.findJwtCandidates(text)
        assertEquals(listOf(token), candidates)
    }
}
