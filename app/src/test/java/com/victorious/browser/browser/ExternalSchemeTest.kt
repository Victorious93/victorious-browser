package com.victorious.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalSchemeTest {

    @Test
    fun httpStaysInApp() {
        assertEquals(ExternalScheme.Decision.InApp, ExternalScheme.route("https://example.com/x"))
        assertEquals(ExternalScheme.Decision.InApp, ExternalScheme.route("http://example.com"))
        assertEquals(ExternalScheme.Decision.InApp, ExternalScheme.route("/relative/path"))
        assertEquals(ExternalScheme.Decision.InApp, ExternalScheme.route("example.com"))
    }

    @Test
    fun blockedSchemesFromPages() {
        for (s in listOf("javascript:alert(1)", "file:///sdcard/x", "content://media/1", "data:text/html,hi", "about:blank", "blob:https://x/1")) {
            val d = ExternalScheme.route(s)
            assertTrue("$s should be blocked", d is ExternalScheme.Decision.Blocked)
        }
    }

    @Test
    fun everydayExternalSchemes() {
        for (s in listOf("mailto:a@b.c", "tel:+15551212", "sms:555", "geo:0,0", "market://details?id=x", "intent://scan/#Intent;end")) {
            val d = ExternalScheme.route(s)
            assertTrue("$s should be external", d is ExternalScheme.Decision.External)
        }
    }

    @Test
    fun unknownSchemesNeedConfirm() {
        val d = ExternalScheme.route("myapp://open?x=1")
        assertTrue(d is ExternalScheme.Decision.Confirm)
        assertEquals("myapp", (d as ExternalScheme.Decision.Confirm).scheme)
    }

    @Test
    fun schemeOfRejectsJunk() {
        assertNull(ExternalScheme.schemeOf(""))
        assertNull(ExternalScheme.schemeOf(":no"))
        assertNull(ExternalScheme.schemeOf("1http:x"))
        assertEquals("https", ExternalScheme.schemeOf("HTTPS://X"))
    }
}

class IntentUriTest {
    @Test
    fun extractsHttpsFallback() {
        val uri = "intent://scan/#Intent;scheme=zxing;package=com.google.zxing.client.android;S.browser_fallback_url=https%3A%2F%2Fexample.com%2Ffallback;end"
        assertEquals("https://example.com/fallback", IntentUri.browserFallbackUrl(uri))
    }

    @Test
    fun rejectsNonHttpFallback() {
        val uri = "intent://x/#Intent;S.browser_fallback_url=javascript%3Aalert(1);end"
        assertNull(IntentUri.browserFallbackUrl(uri))
    }

    @Test
    fun missingFallbackIsNull() {
        assertNull(IntentUri.browserFallbackUrl("intent://x/#Intent;end"))
        assertNull(IntentUri.browserFallbackUrl("mailto:a@b.c"))
    }

    @Test
    fun unsafeTargetDetection() {
        assertTrue(IntentUri.isUnsafeTarget("javascript:alert(1)"))
        assertTrue(IntentUri.isUnsafeTarget("file:///etc/passwd"))
        assertFalse(IntentUri.isUnsafeTarget("https://example.com"))
        assertFalse(IntentUri.isUnsafeTarget(null))
    }
}
