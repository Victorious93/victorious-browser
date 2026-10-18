package com.victorious.browser.panels

/**
 * A fleet panel entry — one of the owner's own locally-hosted / fleet web dashboards.
 *
 * TODO(control-bridge): future ops `browser.panel.list` / `browser.panel.open` will
 * read and drive [PanelStore]. Do NOT build the Messenger bridge in this layer.
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
     * When true, this panel is expected to be reachable over the Victorious Mesh
     * (routing provided by the separate Victorious Mesh app). This flag is
     * informational for now — mesh resolution is deferred.
     *
     * TODO(mesh-reachability): later resolve mesh reachability via the mesh app;
     * do NOT implement VPN/mesh networking here.
     */
    val reachOverMesh: Boolean = false,
)
