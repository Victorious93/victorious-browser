package com.victorious.browser.netid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoSpoofTest {
    @Test fun validCoords() {
        assertTrue(GeoSpoof.isValidLatitude(40.7))
        assertTrue(GeoSpoof.isValidLatitude(-90.0))
        assertFalse(GeoSpoof.isValidLatitude(91.0))
        assertTrue(GeoSpoof.isValidLongitude(-180.0))
        assertFalse(GeoSpoof.isValidLongitude(181.0))
        assertTrue(GeoSpoof.isValidAccuracy(25.0))
        assertFalse(GeoSpoof.isValidAccuracy(0.0))
    }

    @Test fun parseCommaDecimal() {
        assertEquals(40.7, GeoSpoof.parseCoordinate("40,7")!!, 0.0001)
    }

    @Test fun validateOk() {
        val r = GeoSpoof.validate(true, "40.7128", "-74.0060", "30")
        assertTrue(r is GeoSpoof.Result.Ok)
        val c = (r as GeoSpoof.Result.Ok).config
        assertTrue(c.enabled)
        assertEquals(40.7128, c.latitude, 1e-6)
    }

    @Test fun validateRejectsBadLat() {
        assertTrue(GeoSpoof.validate(true, "99", "0", "10") is GeoSpoof.Result.Error)
    }

    @Test fun shimEmptyWhenOff() {
        assertEquals("", GeoSpoof.shim(GeoSpoofConfig(enabled = false)))
    }

    @Test fun shimContainsCoordsWhenOn() {
        val js = GeoSpoof.shim(GeoSpoofConfig(true, 40.7128, -74.0060, 25.0))
        assertTrue(js.contains("40.7128000"))
        assertTrue(js.contains("-74.0060000"))
        assertTrue(js.contains("getCurrentPosition"))
        assertTrue(js.contains("watchPosition"))
        assertTrue("__vbGeoShim" in js)
    }

    @Test fun presetsNonEmpty() {
        assertTrue(GeoSpoof.presets.isNotEmpty())
        GeoSpoof.presets.forEach {
            assertTrue(GeoSpoof.isValidLatitude(it.lat))
            assertTrue(GeoSpoof.isValidLongitude(it.lng))
        }
    }
}
