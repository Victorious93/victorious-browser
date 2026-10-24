package com.victorious.browser.ui.theme

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

/**
 * [enableEdgeToEdge] with no arguments picks light-vs-dark status/navigation-bar icon
 * color based on the *device's* system day/night setting, not this app's own content.
 * This app's theme is an explicit in-app toggle (see [ThemeSettings]), independent of
 * the device setting, so the bare call can mismatch: e.g. device in Light mode while
 * this app is still showing its charcoal dark theme would render dark icons over a
 * dark background -- low-contrast at best, invisible at worst.
 *
 * Call this instead of the bare `enableEdgeToEdge()` in every Activity that uses
 * [VictoriousBrowserTheme], passing the same `darkTheme` flag, and call it again
 * whenever that flag changes (e.g. from a `LaunchedEffect(darkTheme)`) so the system
 * bars stay in sync with an in-session toggle.
 */
fun ComponentActivity.enableThemedEdgeToEdge(darkTheme: Boolean) {
    val style = if (darkTheme) {
        SystemBarStyle.dark(Color.TRANSPARENT)
    } else {
        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
    }
    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
}
