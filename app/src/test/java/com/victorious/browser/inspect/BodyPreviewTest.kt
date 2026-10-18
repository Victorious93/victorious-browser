package com.victorious.browser.inspect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyPreviewTest {

    @Test
    fun textualJson_previewAsText() {
        val body = """{"hello":"world"}""".toByteArray()
        val preview = InspectHelpers.bodyPreview(body, "application/json", truncated = false)
        assertNotNull(preview)
        assertTrue(preview!!.isText)
        assertTrue(preview.text!!.contains("hello"))
        assertEquals(body.size, preview.byteLength)
        assertFalse(preview.truncated)
    }

    @Test
    fun binaryPng_previewAsHex() {
        val body = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0)
        val preview = InspectHelpers.bodyPreview(body, "image/png", truncated = true)
        assertNotNull(preview)
        assertFalse(preview!!.isText)
        assertNotNull(preview.hexPreview)
        assertTrue(preview.hexPreview!!.startsWith("89 50 4e 47") || preview.hexPreview!!.startsWith("89 50 4E 47"))
        assertTrue(preview.truncated)
    }

    @Test
    fun nullBody_returnsNull() {
        assertNull(InspectHelpers.bodyPreview(null, "text/plain", false))
    }

    @Test
    fun isTextualContentType() {
        assertTrue(InspectHelpers.isTextualContentType("application/json; charset=utf-8"))
        assertTrue(InspectHelpers.isTextualContentType("text/html"))
        assertTrue(InspectHelpers.isTextualContentType("application/problem+json"))
        assertFalse(InspectHelpers.isTextualContentType("image/png"))
        assertFalse(InspectHelpers.isTextualContentType(null))
    }

    @Test
    fun looksLikeUtf8Text_rejectsNul() {
        assertFalse(InspectHelpers.looksLikeUtf8Text(byteArrayOf(0x41, 0x00, 0x42)))
        assertTrue(InspectHelpers.looksLikeUtf8Text("plain text".toByteArray()))
    }

    @Test
    fun captureConfig_clamps() {
        CaptureConfig.resetForTests()
        assertEquals(CaptureConfig.DEFAULT_MAX_BODY_BYTES, CaptureConfig.maxBodyBytes())
        CaptureConfig.setMaxBodyBytes(100)
        assertEquals(CaptureConfig.MIN_MAX_BODY_BYTES, CaptureConfig.maxBodyBytes())
        CaptureConfig.setMaxBodyBytes(10_000_000)
        assertEquals(CaptureConfig.MAX_MAX_BODY_BYTES, CaptureConfig.maxBodyBytes())
        CaptureConfig.setMaxBodyBytes(64 * 1024)
        assertEquals(64 * 1024, CaptureConfig.maxBodyBytes())
        CaptureConfig.resetForTests()
    }
}
