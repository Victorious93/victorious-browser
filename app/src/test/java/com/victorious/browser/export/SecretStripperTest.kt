package com.victorious.browser.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretStripperTest {
    @Test fun sensitiveHeadersDetected() {
        assertTrue(SecretStripper.isSensitiveHeader("Cookie"))
        assertTrue(SecretStripper.isSensitiveHeader("set-cookie"))
        assertTrue(SecretStripper.isSensitiveHeader("Authorization"))
        assertTrue(SecretStripper.isSensitiveHeader("Proxy-Authorization"))
        assertFalse(SecretStripper.isSensitiveHeader("Accept"))
        assertFalse(SecretStripper.isSensitiveHeader("User-Agent"))
    }

    @Test fun sensitiveParamsDetected() {
        assertTrue(SecretStripper.isSensitiveParam("password"))
        assertTrue(SecretStripper.isSensitiveParam("user_password"))
        assertTrue(SecretStripper.isSensitiveParam("apiKey"))
        assertTrue(SecretStripper.isSensitiveParam("access_token"))
        assertTrue(SecretStripper.isSensitiveParam("sessionId"))
        assertFalse(SecretStripper.isSensitiveParam("q"))
        assertFalse(SecretStripper.isSensitiveParam("page"))
    }

    @Test fun stripHeadersRemovesSecrets() {
        val headers = linkedMapOf(
            "Accept" to "*/*",
            "Authorization" to "Bearer abc",
            "Cookie" to "sid=1",
            "User-Agent" to "ua",
        )
        val cleaned = SecretStripper.stripHeaders(headers)
        assertEquals(setOf("Accept", "User-Agent"), cleaned.keys)
    }

    @Test fun stripParamsRemovesSecrets() {
        val params = linkedMapOf("q" to "x", "token" to "zzz", "page" to "2")
        assertEquals(setOf("q", "page"), SecretStripper.stripParams(params).keys)
    }

    @Test fun stripUrlQueryDropsSecretParams() {
        val url = "https://h/p?q=1&password=secret&page=2#frag"
        assertEquals("https://h/p?q=1&page=2#frag", SecretStripper.stripUrlQuery(url))
    }

    @Test fun stripUrlQueryNoQueryUnchanged() {
        assertEquals("https://h/p", SecretStripper.stripUrlQuery("https://h/p"))
    }

    @Test fun stripUrlQueryAllSecretDropsQuestionMark() {
        assertEquals("https://h/p", SecretStripper.stripUrlQuery("https://h/p?token=a&secret=b"))
    }

    @Test fun parseQueryOrdered() {
        val m = SecretStripper.parseQuery("https://h/p?a=1&b=2&a=3")
        assertEquals("1", m["a"])
        assertEquals("2", m["b"])
    }
}
