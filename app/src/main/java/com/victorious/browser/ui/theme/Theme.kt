package com.victorious.browser.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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

private val VictoriousLightColorScheme = lightColorScheme(
    primary = NeonCyanOnLight,
    onPrimary = Color.White,
    primaryContainer = CloudHigh,
    onPrimaryContainer = NeonCyanOnLight,
    secondary = NeonMagentaOnLight,
    onSecondary = Color.White,
    secondaryContainer = CloudHigh,
    onSecondaryContainer = NeonMagentaOnLight,
    tertiary = NeonViolet,
    onTertiary = Color.White,
    background = CloudBase,
    onBackground = OnLight,
    surface = CloudElevated,
    onSurface = OnLight,
    surfaceVariant = CloudHigh,
    onSurfaceVariant = OnLightMuted,
    surfaceContainerLowest = Cloud,
    surfaceContainerLow = CloudBase,
    surfaceContainer = CloudElevated,
    surfaceContainerHigh = CloudHigh,
    surfaceContainerHighest = CloudLine,
    outline = CloudLine,
    outlineVariant = CloudLine,
    error = StatusDanger,
    onError = Color.White,
)

/**
 * App-wide theme. Defaults to the original always-dark neon look; pass
 * `darkTheme = false` (see [ThemeSettings]) to switch to the light variant.
 * Neither branch follows the device's system day/night setting on its own —
 * the choice is this app's own persisted toggle, not `isSystemInDarkTheme()`.
 */
@Composable
fun VictoriousBrowserTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) VictoriousDarkColorScheme else VictoriousLightColorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}
