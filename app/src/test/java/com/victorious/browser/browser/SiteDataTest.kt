package com.victorious.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteDataTest {

    @Test
    fun originOmitsDefaultPorts() {
        assertEquals("https://app.example", SiteData.originOf("https://app.example/path?q=1#x"))
        assertEquals("https://app.example", SiteData.originOf("HTTPS://APP.EXAMPLE:443/"))
        assertEquals("http://app.example", SiteData.originOf("http://app.example:80/"))
        assertEquals("https://app.example:8443", SiteData.originOf("https://app.example:8443/x"))
    }

    @Test
    fun originRejectsNonHttp() {
        assertNull(SiteData.originOf(""))
        assertNull(SiteData.originOf("about:blank"))
        assertNull(SiteData.originOf("javascript:alert(1)"))
        assertNull(SiteData.originOf("data:text/html,x"))
        assertNull(SiteData.originOf("not a url"))
    }

    @Test
    fun clearStorageScriptTargetsBothStores() {
        val js = SiteData.clearStorageScript()
        assertTrue(js.contains("localStorage.clear()"))
        assertTrue(js.contains("sessionStorage.clear()"))
        assertTrue(js.contains("location.origin"))
    }
}
