package com.victorious.browser.inspect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CookieParseTest {

    @Test
    fun cookieHeader_splitsMultiplePairs() {
        val headers = listOf(
            CapturedHeader("Cookie", "session=abc123; theme=dark; lang=en"),
        )
        val cookies = InspectHelpers.parseCookies(headers)
        assertEquals(3, cookies.size)
        assertEquals("session", cookies[0].name)
        assertEquals("abc123", cookies[0].value)
        assertEquals("Cookie", cookies[0].source)
        assertTrue(cookies[0].attributes.isEmpty())
        assertEquals("theme", cookies[1].name)
        assertEquals("dark", cookies[1].value)
        assertEquals("lang", cookies[2].name)
    }

    @Test
    fun setCookie_parsesNameValueAndAttributes() {
        val headers = listOf(
            CapturedHeader(
                "Set-Cookie",
                "sid=xyz; Path=/; Domain=.example.com; Secure; HttpOnly; SameSite=Lax; Max-Age=3600",
            ),
        )
        val cookies = InspectHelpers.parseCookies(headers)
        assertEquals(1, cookies.size)
        val c = cookies[0]
        assertEquals("sid", c.name)
        assertEquals("xyz", c.value)
        assertEquals("Set-Cookie", c.source)
        assertEquals("/", c.attributes["Path"])
        assertEquals(".example.com", c.attributes["Domain"])
        assertEquals("", c.attributes["Secure"])
        assertEquals("", c.attributes["HttpOnly"])
        assertEquals("Lax", c.attributes["SameSite"])
        assertEquals("3600", c.attributes["Max-Age"])
    }

    @Test
    fun setCookie_withoutAttributes() {
        val c = InspectHelpers.parseSetCookie("token=only")
        requireNotNull(c)
        assertEquals("token", c.name)
        assertEquals("only", c.value)
        assertTrue(c.attributes.isEmpty())
    }

    @Test
    fun setCookie_malformed_returnsNull() {
        assertEquals(null, InspectHelpers.parseSetCookie("nosquals"))
        assertEquals(null, InspectHelpers.parseSetCookie(""))
        assertEquals(null, InspectHelpers.parseSetCookie("=novalue"))
    }

    @Test
    fun mixedCookieAndSetCookie() {
        val headers = listOf(
            CapturedHeader("Cookie", "a=1; b=2"),
            CapturedHeader("Set-Cookie", "c=3; Path=/api"),
            CapturedHeader("X-Other", "ignore"),
        )
        val cookies = InspectHelpers.parseCookies(headers)
        assertEquals(3, cookies.size)
        assertEquals(listOf("Cookie", "Cookie", "Set-Cookie"), cookies.map { it.source })
        assertEquals("/api", cookies[2].attributes["Path"])
    }

    @Test
    fun cookieHeader_skipsEmptySegments() {
        val cookies = InspectHelpers.parseCookieHeader("a=1;; ; b=2")
        assertEquals(2, cookies.size)
        assertEquals("a", cookies[0].name)
        assertEquals("b", cookies[1].name)
    }
}
