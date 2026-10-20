package com.victorious.browser.netid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpstreamProxyValidatorTest {
    @Test fun offWhenEmpty() {
        val r = UpstreamProxyValidator.validate(false, UpstreamProxyConfig.Type.HTTP, "", "", "", "")
        assertTrue(r is UpstreamProxyValidator.Result.Ok)
        assertFalse((r as UpstreamProxyValidator.Result.Ok).config.enabled)
    }

    @Test fun acceptsIpv4AndHostname() {
        assertTrue(UpstreamProxyValidator.isValidHost("127.0.0.1"))
        assertTrue(UpstreamProxyValidator.isValidHost("proxy.example.com"))
        assertTrue(UpstreamProxyValidator.isValidHost("orbot"))
        assertFalse(UpstreamProxyValidator.isValidHost(""))
        assertFalse(UpstreamProxyValidator.isValidHost("999.1.1.1"))
        assertFalse(UpstreamProxyValidator.isValidHost("-bad-.com"))
    }

    @Test fun portBounds() {
        assertEquals(9050, UpstreamProxyValidator.parsePort("9050"))
        assertEquals(null, UpstreamProxyValidator.parsePort("0"))
        assertEquals(null, UpstreamProxyValidator.parsePort("70000"))
        assertEquals(null, UpstreamProxyValidator.parsePort("abc"))
    }

    @Test fun socks5AuthOk() {
        val r = UpstreamProxyValidator.validate(
            true, UpstreamProxyConfig.Type.SOCKS5, "127.0.0.1", "9050", "u", "p",
        )
        assertTrue(r is UpstreamProxyValidator.Result.Ok)
        val c = (r as UpstreamProxyValidator.Result.Ok).config
        assertTrue(c.enabled)
        assertEquals(UpstreamProxyConfig.Type.SOCKS5, c.type)
        assertTrue(c.hasAuth)
    }

    @Test fun passwordWithoutUserFails() {
        val r = UpstreamProxyValidator.validate(
            true, UpstreamProxyConfig.Type.HTTP, "proxy.example.com", "8080", "", "secret",
        )
        assertTrue(r is UpstreamProxyValidator.Result.Error)
    }

    @Test fun selfLoopDetected() {
        val c = UpstreamProxyConfig(true, UpstreamProxyConfig.Type.HTTP, "127.0.0.1", 8888)
        assertTrue(UpstreamProxyValidator.isSelfLoop(c, 8888))
        assertFalse(UpstreamProxyValidator.isSelfLoop(c, 9050))
        assertFalse(UpstreamProxyValidator.isSelfLoop(c.copy(host = "10.0.0.1"), 8888))
    }

    @Test fun summaryHidesPassword() {
        val s = UpstreamProxyConfig(true, UpstreamProxyConfig.Type.HTTP, "h", 1, "alice", "sekrit").summary()
        assertTrue("sekrit" !in s)
        assertTrue("alice" in s)
    }
}
