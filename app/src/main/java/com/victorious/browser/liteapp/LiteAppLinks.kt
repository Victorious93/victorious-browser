package com.victorious.browser.liteapp

/**
 * Deep-link format for a Lite App shortcut. The link carries only the panel id;
 * the URL is always resolved from [com.victorious.browser.panels.PanelStore] at
 * launch, so a shortcut can never be pointed at an arbitrary URL.
 */
object LiteAppLinks {
    private const val SCHEME = "victorious-lite"
    private const val HOST = "panel"
    private val PANEL_ID = Regex("^$SCHEME://$HOST/([A-Za-z0-9-]{1,64})$")

    fun dataString(panelId: String): String = "$SCHEME://$HOST/$panelId"

    fun parsePanelId(data: String?): String? =
        data?.let { PANEL_ID.matchEntire(it)?.groupValues?.get(1) }

    fun shortcutId(panelId: String): String = "lite-$panelId"
}
