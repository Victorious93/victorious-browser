package com.victorious.browser.ui

import com.victorious.browser.ui.theme.Brand
import com.victorious.browser.ui.theme.blendArgb
import com.victorious.browser.ui.theme.sampleRing
import org.junit.Assert.assertEquals
import org.junit.Test

class BrandGradientTest {
    private fun a(c: Int) = (c ushr 24) and 0xFF
    private fun r(c: Int) = (c ushr 16) and 0xFF
    private fun g(c: Int) = (c ushr 8) and 0xFF
    private fun b(c: Int) = c and 0xFF

    @Test
    fun blend_endpoints_returnExactColors() {
        assertEquals(Brand.CYAN_ARGB, blendArgb(Brand.CYAN_ARGB, Brand.MAGENTA_ARGB, 0f))
        assertEquals(Brand.MAGENTA_ARGB, blendArgb(Brand.CYAN_ARGB, Brand.MAGENTA_ARGB, 1f))
    }

    @Test
    fun blend_clampsOutOfRange() {
        assertEquals(Brand.CYAN_ARGB, blendArgb(Brand.CYAN_ARGB, Brand.MAGENTA_ARGB, -5f))
        assertEquals(Brand.MAGENTA_ARGB, blendArgb(Brand.CYAN_ARGB, Brand.MAGENTA_ARGB, 9f))
    }

    @Test
    fun blend_midpoint_isChannelAverage() {
        val mid = blendArgb(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0.5f)
        assertEquals(255, a(mid))
        assertEquals(127, r(mid))
        assertEquals(127, g(mid))
        assertEquals(127, b(mid))
    }

    @Test
    fun blend_preservesFullAlpha() {
        val c = blendArgb(Brand.CYAN_ARGB, Brand.MAGENTA_ARGB, 0.3f)
        assertEquals(0xFF, a(c))
    }

    @Test
    fun sampleRing_hitsStops() {
        assertEquals(Brand.CYAN_ARGB, sampleRing(0f))
        assertEquals(Brand.VIOLET_ARGB, sampleRing(0.5f))
        assertEquals(Brand.MAGENTA_ARGB, sampleRing(1f))
    }

    @Test
    fun sampleRing_isContinuousAroundMid() {
        val justBelow = sampleRing(0.49f)
        val justAbove = sampleRing(0.51f)
        // both should be close to violet; channels within a small delta
        assertEquals(r(Brand.VIOLET_ARGB).toFloat(), r(justBelow).toFloat(), 8f)
        assertEquals(r(Brand.VIOLET_ARGB).toFloat(), r(justAbove).toFloat(), 8f)
    }
}
