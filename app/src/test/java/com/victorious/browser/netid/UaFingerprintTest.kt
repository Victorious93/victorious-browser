package com.victorious.browser.netid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UaFingerprintTest {
    @Test fun defaultResolvesNull() {
        assertNull(UaPresets.resolve(UaPreset.DEFAULT, ""))
    }

    @Test fun chromeAndroidMaps() {
        val ua = UaPresets.resolve(UaPreset.CHROME_ANDROID, "")!!
        assertTrue("Android" in ua)
        assertTrue("Chrome" in ua)
        assertTrue("Mobile" in ua)
    }

    @Test fun chromeDesktopMaps() {
        val ua = UaPresets.resolve(UaPreset.CHROME_DESKTOP, "")!!
        assertTrue("Windows" in ua)
        assertFalse("Mobile" in ua)
    }

    @Test fun safariIosMaps() {
        val ua = UaPresets.resolve(UaPreset.SAFARI_IOS, "")!!
        assertTrue("iPhone" in ua)
        assertTrue("Safari" in ua)
    }

    @Test fun googlebotMaps() {
        assertTrue("Googlebot" in UaPresets.resolve(UaPreset.GOOGLEBOT, "")!!)
    }

    @Test fun customStripsNewlines() {
        val ua = UaPresets.resolve(UaPreset.CUSTOM, "Mozilla\nEvil")!!
        assertFalse("\n" in ua)
        assertEquals("MozillaEvil", ua)
    }

    @Test fun customEmptyIsNull() {
        assertNull(UaPresets.resolve(UaPreset.CUSTOM, "  "))
    }

    @Test fun fromNameFallback() {
        assertEquals(UaPreset.DEFAULT, UaPreset.fromName(null))
        assertEquals(UaPreset.DEFAULT, UaPreset.fromName("nope"))
        assertEquals(UaPreset.GOOGLEBOT, UaPreset.fromName("GOOGLEBOT"))
    }

    @Test fun jsStringEscapes() {
        assertEquals("\"a\\\"b\"", FingerprintShim.jsString("a\"b"))
        assertEquals("\"a\\\\b\"", FingerprintShim.jsString("a\\b"))
        assertTrue("\\u003c" in FingerprintShim.jsString("<script>"))
    }

    @Test fun languagesParse() {
        assertEquals(listOf("en-US", "en"), FingerprintShim.parseLanguages("en-US, en"))
        assertTrue(FingerprintShim.parseLanguages("!!!").isEmpty())
    }

    @Test fun shimEmptyWhenAllOff() {
        assertEquals("", FingerprintShim.generate(FingerprintConfig()))
    }

    @Test fun shimIncludesEnabledSurfaces() {
        val js = FingerprintShim.generate(
            FingerprintConfig(
                spoofPlatform = true, platform = "Win32",
                spoofLanguages = true, languages = "en-US,en",
                spoofHardwareConcurrency = true, hardwareConcurrency = 4,
                spoofScreen = true, screenWidth = 1280, screenHeight = 720,
                spoofTimezone = true, timezoneOffsetMinutes = 300,
                spoofWebGl = true,
            ),
        )
        assertTrue("__vbFpShim" in js)
        assertTrue("Win32" in js)
        assertTrue("hardwareConcurrency" in js)
        assertTrue("1280" in js)
        assertTrue("getTimezoneOffset" in js)
        assertTrue("37445" in js) // UNMASKED_VENDOR_WEBGL
        assertTrue("37446" in js)
    }

    @Test fun timezoneBounds() {
        assertTrue(FingerprintShim.isValidTimezoneOffset(0))
        assertTrue(FingerprintShim.isValidTimezoneOffset(-840))
        assertFalse(FingerprintShim.isValidTimezoneOffset(900))
    }
}
