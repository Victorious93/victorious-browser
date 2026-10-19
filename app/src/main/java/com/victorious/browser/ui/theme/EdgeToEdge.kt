package com.victorious.browser.ui.theme

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

/**
 * [enableEdgeToEdge] with no arguments picks light-vs-dark status/navigation-bar icon
 * color based on the *device's* system day/night setting, not this app's content. This
 * app's UI (see [VictoriousBrowserTheme]) is permanently dark — there is no light
 * variant — so on a device set to system Light mode, the stock call would render dark
 * icons over our charcoal background: low-contrast at best, invisible at worst.
 *
 * This forces light (white) system-bar icons unconditionally, matching the UI's actual
 * color regardless of the device's own theme setting. Call this instead of the bare
 * `enableEdgeToEdge()` in every Activity that uses [VictoriousBrowserTheme].
 */
fun ComponentActivity.enableAlwaysDarkEdgeToEdge() {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
    )
}
