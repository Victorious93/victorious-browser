package com.victorious.browser.osint.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneLookupTest {
    private fun ok(raw: String, region: String = "US") =
        (PhoneLookup.analyze(raw, region) as PhoneLookup.Outcome.Ok).result

    @Test fun parsesUsNumberWithFormats() {
        val r = ok("(650) 253-0000")
        assertTrue(r.valid)
        assertEquals(1, r.countryCode)
        assertEquals("US", r.regionCode)
        assertEquals("+16502530000", r.e164)
        assertEquals("+1 650-253-0000", r.international)
        assertEquals("tel:+1-650-253-0000", r.rfc3966)
        assertTrue(r.timeZones.contains("America/Los_Angeles"))
    }

    @Test fun ukMobileHasMobileLineTypeAndCarrierData() {
        val r = ok("+44 7400 123456")
        assertEquals("GB", r.regionCode)
        assertEquals("Mobile", r.lineType)
        assertEquals("+447400123456", r.e164)
    }

    @Test fun plusPrefixIgnoresDefaultRegion() {
        assertEquals("DE", ok("+49 30 901820", region = "US").regionCode)
    }

    @Test fun errors() {
        assertTrue(PhoneLookup.analyze("") is PhoneLookup.Outcome.Error)
        assertTrue(PhoneLookup.analyze("hello") is PhoneLookup.Outcome.Error)
        assertTrue(PhoneLookup.analyze("1".repeat(41)) is PhoneLookup.Outcome.Error)
        assertFalse(ok("+1 555 0100").valid)
    }

    @Test fun pivotsAreHttpsAndIncludeWaMe() {
        val p = PhoneLookup.pivots(ok("+16502530000"))
        assertTrue(p.all { it.url.startsWith("https://") })
        assertTrue(p.any { it.url == "https://wa.me/16502530000" })
        assertTrue(p.any { it.url == "https://t.me/+16502530000" })
    }
}
