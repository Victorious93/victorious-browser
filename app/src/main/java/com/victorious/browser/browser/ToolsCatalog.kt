package com.victorious.browser.browser

/**
 * Fleet tools reachable from inside the browser (Tools sheet / new-tab page).
 * Pure data so the id → destination mapping and grouping can be unit-tested.
 *
 * Every id must be recognized by [com.victorious.browser.toolDestination].
 */
object ToolsCatalog {
    data class Tool(val id: String, val title: String, val blurb: String)
    data class Group(val name: String, val tools: List<Tool>)

    val groups: List<Group> = listOf(
        Group(
            "Browse",
            listOf(
                Tool("panels", "Fleet panels", "Your dashboards & fleet home"),
                Tool("home", "Home", "Quick links & start page"),
                Tool("sandboxes", "Sandboxes", "Isolated browsing profiles"),
                Tool("extensions", "Extensions", "Userscript engine & manager"),
                Tool("scripts", "User scripts", "Install & edit userscripts"),
            ),
        ),
        Group(
            "Recon",
            listOf(
                Tool("network", "Proxy / HTTP history", "Intercept, repeater, history"),
                Tool("recon", "Recon workbench", "Encoders, hashes, URL tools"),
                Tool("osint", "OSINT", "Sources, dorks, fingerprints"),
                Tool("antiphish", "Anti-Phish", "Warnings & reports"),
            ),
        ),
        Group(
            "Offense / Testing",
            listOf(
                Tool("scanner", "Scanner", "Active & passive vuln scanner"),
                Tool("fuzz", "Fuzz", "Brute-force & template scan"),
                Tool("msf", "Metasploit", "MSF RPC console"),
            ),
        ),
        Group(
            "Identity & Mesh",
            listOf(
                Tool("netid", "Net ID / Spoof", "UA, MAC & geo identity"),
                Tool("wireguard", "WireGuard", "VPN tunnel"),
                Tool("headscale", "Headscale", "Tailscale control plane"),
                Tool("fleet", "Fleet control", "Bridge & fleet ops"),
            ),
        ),
        Group(
            "Evidence & Help",
            listOf(
                Tool("certs", "Certificates", "Pinned / trusted certs"),
                Tool("evidence", "Evidence", "Screenshots & captures"),
                Tool("export", "Export", "Stage findings for the fleet"),
                Tool("help", "Help / Manual", "In-app manual"),
            ),
        ),
    )

    /** Stable flat id list (order preserved). */
    val allIds: List<String> = groups.flatMap { g -> g.tools.map { it.id } }

    /** Quick-access chip ids shown on the new-tab page and in the toolbar drawer header. */
    val quickIds: List<String> = listOf("panels", "network", "recon", "osint", "scanner", "msf", "wireguard", "headscale", "antiphish", "help")

    fun find(id: String): Tool? = groups.asSequence().flatMap { it.tools.asSequence() }.firstOrNull { it.id == id }
}
