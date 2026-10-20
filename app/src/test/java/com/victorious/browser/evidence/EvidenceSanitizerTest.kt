package com.victorious.browser.evidence

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceSanitizerTest {

    @Test fun stripsSecretsFromExchange() {
        val out = EvidenceSanitizer.sanitizedExchange(
            method = "GET",
            url = "https://example.com/a?token=abc&q=1&password=pw",
            requestHeaders = linkedMapOf(
                "Accept" to "*/*",
                "Cookie" to "sid=1",
                "Authorization" to "Bearer zzz",
                "User-Agent" to "ua",
            ),
            responseHeaders = linkedMapOf(
                "Content-Type" to "text/html",
                "Set-Cookie" to "sid=2",
            ),
            statusCode = 200,
        )
        // secrets gone
        assertFalse(out.contains("sid=1"))
        assertFalse(out.contains("sid=2"))
        assertFalse(out.contains("Bearer zzz"))
        assertFalse(out.contains("token=abc"))
        assertFalse(out.contains("password=pw"))
        // kept
        assertTrue(out.contains("q=1"))
        assertTrue(out.contains("Content-Type: text/html"))
        assertTrue(out.contains("User-Agent: ua"))
        assertTrue(out.contains("status: 200"))
    }
}
