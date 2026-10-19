package com.victorious.browser.browser

import android.content.Context
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How night mode is realised on this device's WebView. */
enum class NightModeStrategy { ALGORITHMIC, FORCE_DARK, UNSUPPORTED }

/**
 * Prefer algorithmic darkening (WebView 114+, honours the page's own dark styles),
 * fall back to the deprecated force-dark path, else report unsupported.
 */
internal fun chooseNightModeStrategy(
    algorithmicSupported: Boolean,
    forceDarkSupported: Boolean,
): NightModeStrategy = when {
    algorithmicSupported -> NightModeStrategy.ALGORITHMIC
    forceDarkSupported -> NightModeStrategy.FORCE_DARK
    else -> NightModeStrategy.UNSUPPORTED
}

/**
 * Applies night mode to [webView] and returns the strategy used.
 *
 * Algorithmic darkening only takes effect when the hosting theme is dark;
 * Theme.VictoriousBrowser inherits from android:Theme.Material, which is.
 */
@Suppress("DEPRECATION")
fun applyNightMode(webView: WebView, enabled: Boolean): NightModeStrategy {
    val settings = webView.settings
    val strategy = chooseNightModeStrategy(
        algorithmicSupported = WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING),
        forceDarkSupported = WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK),
    )
    when (strategy) {
        NightModeStrategy.ALGORITHMIC ->
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, enabled)
        NightModeStrategy.FORCE_DARK ->
            WebSettingsCompat.setForceDark(
                settings,
                if (enabled) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF,
            )
        NightModeStrategy.UNSUPPORTED -> Unit
    }
    return strategy
}

/** Persisted night-mode switch (SharedPreferences, same pattern as BridgeSettings). */
class NightModeSettings private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        _enabled.value = value
    }

    companion object {
        private const val PREFS = "browser_night_mode"
        private const val KEY_ENABLED = "enabled"

        @Volatile private var instance: NightModeSettings? = null

        fun get(context: Context): NightModeSettings =
            instance ?: synchronized(this) {
                instance ?: NightModeSettings(context).also { instance = it }
            }
    }
}
