package com.victorious.browser.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persisted app-UI theme choice (SharedPreferences, same pattern as the other
 * `*Settings` singletons). Default is dark, matching this app's original
 * always-dark design; [VictoriousLightColorScheme] is opt-in.
 */
class ThemeSettings private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _useLightTheme = MutableStateFlow(prefs.getBoolean(KEY_LIGHT, false))
    val useLightTheme: StateFlow<Boolean> = _useLightTheme.asStateFlow()

    fun setLightTheme(value: Boolean) {
        prefs.edit().putBoolean(KEY_LIGHT, value).apply()
        _useLightTheme.value = value
    }

    fun toggle() = setLightTheme(!_useLightTheme.value)

    companion object {
        private const val PREFS = "theme_settings"
        private const val KEY_LIGHT = "use_light_theme"

        @Volatile private var instance: ThemeSettings? = null

        fun get(context: Context): ThemeSettings =
            instance ?: synchronized(this) {
                instance ?: ThemeSettings(context).also { instance = it }
            }
    }
}
