package com.victorious.browser.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Central source of truth for the Victorious cyan → magenta neon brand.
 *
 * The pure color math ([blendArgb], [sampleRing]) lives here (and is unit-tested)
 * so the gradient the whole app draws stays consistent and deterministic.
 */
object Brand {
    val cyan = NeonCyan
    val magenta = NeonMagenta
    val violet = NeonViolet

    /** ARGB ints for the ring stops — kept as ints so the math is trivially testable. */
    const val CYAN_ARGB = 0xFF00E5FF.toInt()
    const val VIOLET_ARGB = 0xFF8A5BFF.toInt()
    const val MAGENTA_ARGB = 0xFFFF00E5.toInt()

    /** Ordered gradient stops used for the ring, borders and accents. */
    val stops: List<Color> = listOf(cyan, violet, magenta)

    /** Horizontal cyan → magenta sweep used for section headers and text accents. */
    fun horizontal(): Brush = Brush.linearGradient(colors = stops)

    /** Diagonal sweep used for card borders and buttons. */
    fun diagonal(): Brush =
        Brush.linearGradient(
            colors = stops,
            start = Offset.Zero,
            end = Offset.Infinite,
        )

    /** Radial glow used behind the brand ring / hero. */
    fun glow(): Brush =
        Brush.radialGradient(
            colors = listOf(
                cyan.copy(alpha = 0.35f),
                magenta.copy(alpha = 0.18f),
                Color.Transparent,
            ),
        )

    /** Sweep gradient for the ring stroke itself (wraps back to cyan for a seamless ring). */
    fun ringSweep(): Brush =
        Brush.sweepGradient(colors = listOf(cyan, violet, magenta, violet, cyan))

    /** A gradient border stroke of the given width. */
    fun border(width: androidx.compose.ui.unit.Dp): BorderStroke =
        BorderStroke(width, diagonal())
}

/**
 * Linear-interpolate two packed ARGB colors. Pure & deterministic — unit-tested.
 * [t] is clamped to [0,1]; channels are blended independently.
 */
fun blendArgb(startArgb: Int, endArgb: Int, t: Float): Int {
    val f = t.coerceIn(0f, 1f)
    fun ch(shift: Int): Int {
        val s = (startArgb ushr shift) and 0xFF
        val e = (endArgb ushr shift) and 0xFF
        return (s + ((e - s) * f)).toInt().coerceIn(0, 255)
    }
    val a = ch(24); val r = ch(16); val g = ch(8); val b = ch(0)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

/**
 * Sample the cyan → violet → magenta ring gradient at position [t] in [0,1].
 * Returns a packed ARGB int. Pure & deterministic — unit-tested.
 */
fun sampleRing(t: Float): Int {
    val f = t.coerceIn(0f, 1f)
    return if (f <= 0.5f) {
        blendArgb(Brand.CYAN_ARGB, Brand.VIOLET_ARGB, f / 0.5f)
    } else {
        blendArgb(Brand.VIOLET_ARGB, Brand.MAGENTA_ARGB, (f - 0.5f) / 0.5f)
    }
}
