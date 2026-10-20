package com.victorious.browser.panels

import android.content.Context

/**
 * Persists owner-editable [MeshSettings] in SharedPreferences `mesh_config`.
 * Holds ONLY ranges, base_domain and the bridge toggle — never mesh IPs
 * (those are SENSITIVE and live in memory only; also stripped from config.export).
 */
object MeshConfigStore {
    const val PREFS = "mesh_config"
    const val KEY_RANGES = "ranges"
    const val KEY_BASE_DOMAIN = "base_domain"
    const val KEY_BRIDGE_ENABLED = "bridge_enabled"

    fun load(context: Context): MeshSettings {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ranges = p.getString(KEY_RANGES, null)?.let { MeshRanges.splitText(it) }
            ?.takeIf { it.isNotEmpty() } ?: MeshConfig.DEFAULT_RANGES
        val s = MeshSettings(
            ranges = ranges,
            baseDomain = p.getString(KEY_BASE_DOMAIN, null),
            bridgeEnabled = p.getBoolean(KEY_BRIDGE_ENABLED, false),
        )
        MeshConfig.current = s
        return s
    }

    fun save(context: Context, s: MeshSettings) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_RANGES, s.ranges.joinToString("\n"))
            .putString(KEY_BASE_DOMAIN, MeshConfig.normalizeBaseDomain(s.baseDomain) ?: "")
            .putBoolean(KEY_BRIDGE_ENABLED, s.bridgeEnabled)
            .apply()
        MeshConfig.current = s
    }
}
