package com.victorious.browser.panels

/**
 * A fleet panel entry — one of the owner's own locally-hosted / fleet web dashboards.
 *
 * Fleet bridge ops `browser.panel.list` / `browser.panel.open` read/drive [PanelStore].
 */
enum class PanelKind {
    LOCAL_HTTP,
    HEADSCALE_ADMIN,
    METASPLOIT_WEB,
    KYC_AUDITLAB,
    OTHER,
}

enum class Reachability {
    UNKNOWN,
    UP,
    DOWN,
}

data class Panel(
    val id: String,
    val name: String,
    val url: String,
    val kind: PanelKind,
    /**
     * When true, opening this panel goes through [MeshReachability] /
     * [MeshResolution]: host is classified (mesh IP / MagicDNS / node name),
     * a best-effort tunnel signal + TCP probe runs, and the UI either opens
     * the WebView or shows an actionable tunnel-down / unreachable / unknown-host
     * message. Mesh *routing* itself is provided by the separate Victorious Mesh
     * app (this browser does not hold the VpnService slot).
     */
    val reachOverMesh: Boolean = false,
)
