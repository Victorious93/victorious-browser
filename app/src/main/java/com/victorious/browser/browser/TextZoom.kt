package com.victorious.browser.browser

/**
 * Victorious Browser — page text-zoom (accessibility font scaling).
 *
 * Pure, Android-free logic for stepping and snapping the WebView `textZoom`
 * percentage through a fixed ladder of levels. The Android layer stores the
 * chosen percent in [BrowserPrefs] and applies it via `WebSettings.textZoom`.
 * Everything here is unit-tested.
 */
object TextZoom {

    /** Discrete zoom levels (percent). Must stay sorted ascending. */
    val LEVELS: List<Int> = listOf(50, 75, 90, 100, 110, 125, 150, 175, 200)

    /** Default page text zoom (100 % = WebView default). */
    const val DEFAULT: Int = 100

    val MIN: Int get() = LEVELS.first()
    val MAX: Int get() = LEVELS.last()

    /**
     * Snap an arbitrary percent to the nearest valid level. Out-of-range values
     * clamp to [MIN]/[MAX]; ties round to the lower level for predictability.
     */
    fun snap(percent: Int): Int {
        if (percent <= MIN) return MIN
        if (percent >= MAX) return MAX
        return LEVELS.minByOrNull { level ->
            val d = kotlin.math.abs(level - percent)
            // Encode "prefer lower on a tie" by nudging higher candidates.
            d * 2 + if (level > percent) 1 else 0
        } ?: DEFAULT
    }

    /** Next level up from [percent] (snapped first); stays at [MAX] when already there. */
    fun stepUp(percent: Int): Int {
        val cur = snap(percent)
        return LEVELS.firstOrNull { it > cur } ?: MAX
    }

    /** Next level down from [percent] (snapped first); stays at [MIN] when already there. */
    fun stepDown(percent: Int): Int {
        val cur = snap(percent)
        return LEVELS.lastOrNull { it < cur } ?: MIN
    }

    /** True when [percent] (snapped) differs from [DEFAULT]. */
    fun isModified(percent: Int): Boolean = snap(percent) != DEFAULT

    /** Can zoom in further? */
    fun canIncrease(percent: Int): Boolean = snap(percent) < MAX

    /** Can zoom out further? */
    fun canDecrease(percent: Int): Boolean = snap(percent) > MIN

    /** Display label, e.g. "100%". */
    fun label(percent: Int): String = "${snap(percent)}%"
}
