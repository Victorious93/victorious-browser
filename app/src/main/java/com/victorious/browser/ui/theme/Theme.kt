package com.victorious.browser.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VictoriousDarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color.Black,
    secondary = NeonMagenta,
    onSecondary = Color.Black,
    tertiary = NeonCyan,
    background = Charcoal,
    onBackground = OnDark,
    surface = CharcoalElevated,
    onSurface = OnDark,
    surfaceVariant = CharcoalElevated,
    onSurfaceVariant = OnDarkMuted,
)

@Composable
fun VictoriousBrowserTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = VictoriousDarkColorScheme,
        typography = Typography,
        content = content,
    )
}
