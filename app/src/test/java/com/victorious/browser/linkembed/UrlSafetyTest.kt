package com.victorious.browser.linkembed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlSafetyTest {
    @Test fun allowsHttpAndHttps() {
        assertEquals("https://example.com/a?b=1", UrlSafety.sanitize("https://example.com/a?b=1"))
        assertEquals("http://example.com", UrlSafety.sanitize("  http://example.com  "))
        assertEquals("https://Example.com/", UrlSafety.sanitize("HTTPS://Example.com/"))
    }

    @Test fun rejectsUnsafeSchemes() {
        listOf(
            "javascript:alert(1)", "JavaScript:alert(1)", "data:text/html,<script>x</script>",
            "file:///etc/passwd", "content://com.android.contacts/data", "intent://scan#Intent;end",
            "blob:https://example.com/uuid", "about:blank", "ftp://example.com/x", "chrome://settings",
            "vbscript:msgbox", "ws://example.com",
        ).forEach { assertNull(it, UrlSafety.sanitize(it)) }
    }

    @Test fun rejectsMalformedRelativeAndCredentialed() {
        assertNull(UrlSafety.sanitize(""))
        assertNull(UrlSafety.sanitize(null))
        assertNull(UrlSafety.sanitize("//example.com/x"))
        assertNull(UrlSafety.sanitize("/relative/path"))
        assertNull(UrlSafety.sanitize("https:example.com"))
        assertNull(UrlSafety.sanitize("https://"))
        assertNull(UrlSafety.sanitize("https://user:pass@example.com/"))
        assertNull(UrlSafety.sanitize("https://exa\nmple.com/"))
        assertNull(UrlSafety.sanitize("https://example.com/" + "a".repeat(UrlSafety.MAX_URL_LENGTH)))
    }

    @Test fun rejectionCarriesReason() {
        val r = UrlSafety.check("javascript:alert(1)")
        assertTrue(r is UrlSafety.Result.Rejected && r.reason.contains("javascript"))
    }

    @Test fun resolveRechecksResult() {
        assertEquals("https://example.com/img/a.png", UrlSafety.resolve("https://example.com/post/1", "/img/a.png"))
        assertEquals("https://cdn.example.com/x.png", UrlSafety.resolve("https://example.com/", "//cdn.example.com/x.png"))
        assertNull(UrlSafety.resolve("https://example.com/", "javascript:alert(1)"))
        assertNull(UrlSafety.resolve("https://example.com/", "data:image/png;base64,AAAA"))
        assertNull(UrlSafety.resolve("https://example.com/", ""))
    }

    @Test fun originOf() {
        assertEquals("https://example.com", UrlSafety.originOf("https://Example.com:443/x"))
        assertEquals("http://example.com:8080", UrlSafety.originOf("http://example.com:8080/x"))
    }
}
