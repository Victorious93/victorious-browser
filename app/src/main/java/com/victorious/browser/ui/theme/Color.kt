package com.victorious.browser.ui.theme

import androidx.compose.ui.graphics.Color

// ── Victorious neon brand palette ────────────────────────────────────────────
// Brand mark: a glowing cyan → magenta ring on dark charcoal, matching the fleet.

// Accents (the ring gradient stops)
val NeonCyan = Color(0xFF00E5FF)
val NeonMagenta = Color(0xFFFF00E5)
val NeonViolet = Color(0xFF8A5BFF) // mid-gradient blend, used for focus/links

// Charcoal base ramp (darkest → elevated)
val CharcoalBase = Color(0xFF0C0D10)
val Charcoal = Color(0xFF121317)
val CharcoalElevated = Color(0xFF191B20)
val CharcoalHigh = Color(0xFF23262D)
val CharcoalLine = Color(0xFF2E323B)

// Foreground
val OnDark = Color(0xFFE7E9EE)
val OnDarkMuted = Color(0xFF9AA0AB)

// Semantic status colors (readable on charcoal)
val StatusOk = Color(0xFF28E07B)
val StatusWarn = Color(0xFFFFB020)
val StatusDanger = Color(0xFFFF4D6A)
val StatusRunning = NeonCyan
val StatusIdle = Color(0xFF7A818C)

// ── Light-theme ramp (same brand accents, opt-in alternative to the charcoal
// base above) ────────────────────────────────────────────────────────────────
val CloudBase = Color(0xFFF4F5F7)
val Cloud = Color(0xFFFFFFFF)
val CloudElevated = Color(0xFFFFFFFF)
val CloudHigh = Color(0xFFE9EBEF)
val CloudLine = Color(0xFFD7DAE1)

val OnLight = Color(0xFF1A1C20)
val OnLightMuted = Color(0xFF5B6069)

// The raw neon accents are too low-contrast as text/icon color on a light
// background; darker variants keep the brand hue while staying readable.
val NeonCyanOnLight = Color(0xFF0088A3)
val NeonMagentaOnLight = Color(0xFFB4009E)
