package com.victorious.browser.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VictoriousDarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color.Black,
    primaryContainer = CharcoalHigh,
    onPrimaryContainer = NeonCyan,
    secondary = NeonMagenta,
    onSecondary = Color.Black,
    secondaryContainer = CharcoalHigh,
    onSecondaryContainer = NeonMagenta,
    tertiary = NeonViolet,
    onTertiary = Color.Black,
    background = Charcoal,
    onBackground = OnDark,
    surface = CharcoalElevated,
    onSurface = OnDark,
    surfaceVariant = CharcoalHigh,
    onSurfaceVariant = OnDarkMuted,
    surfaceContainerLowest = CharcoalBase,
    surfaceContainerLow = Charcoal,
    surfaceContainer = CharcoalElevated,
    surfaceContainerHigh = CharcoalHigh,
    surfaceContainerHighest = CharcoalLine,
    outline = CharcoalLine,
    outlineVariant = CharcoalLine,
    error = StatusDanger,
    onError = Color.Black,
)

@Composable
fun VictoriousBrowserTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = VictoriousDarkColorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}
