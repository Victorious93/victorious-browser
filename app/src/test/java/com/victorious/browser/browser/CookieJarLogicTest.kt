package com.victorious.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class CookieJarLogicTest {

    private fun jwt(): String {
        fun enc(s: String) =
            Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray())
        return "${enc("""{"alg":"none"}""")}.${enc("""{"sub":"a"}""")}.sig"
    }

    @Test
    fun parseJarSplitsPairsAndAttachesJwt() {
        val token = jwt()
        val cookies = CookieJarLogic.parseJar("theme=dark; access=$token")
        assertEquals(2, cookies.size)
        assertEquals("theme", cookies[0].name)
        assertEquals("dark", cookies[0].value)
        assertNull(cookies[0].jwt)
        assertEquals("access", cookies[1].name)
        assertNotNull(cookies[1].jwt)
        assertFalse(cookies[1].jwt!!.signatureVerified)
        assertEquals("a", cookies[1].jwt!!.claims["sub"])
    }

    @Test
    fun parseJarEmpty() {
        assertTrue(CookieJarLogic.parseJar(null).isEmpty())
        assertTrue(CookieJarLogic.parseJar("").isEmpty())
        assertTrue(CookieJarLogic.parseJar("   ").isEmpty())
    }

    @Test
    fun setAndExpireLines() {
        assertEquals("sid=abc; Path=/", CookieJarLogic.setCookieLine("sid", "abc"))
        assertTrue(CookieJarLogic.expireCookieLine("sid").startsWith("sid=; Path=/; Max-Age=0"))
        assertTrue(CookieJarLogic.expireCookieLine("sid").contains("01 Jan 1970"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBadCookieName() {
        CookieJarLogic.setCookieLine("a;b", "1")
    }

    @Test
    fun exportJsonIncludesOriginAndJwtNote() {
        val cookies = CookieJarLogic.parseJar("access=${jwt()}")
        val json = CookieJarLogic.exportJson("https://app.example/path", cookies, 42L)
        assertTrue(json.contains("\"origin\": \"https://app.example\""))
        assertTrue(json.contains("decoded-not-verified"))
        assertTrue(json.contains("\"exportedAt\": 42"))
        assertFalse(json.contains("\"signatureVerified\": true"))
    }
}
