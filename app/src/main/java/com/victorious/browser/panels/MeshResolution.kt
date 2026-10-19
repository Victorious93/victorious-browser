package com.victorious.browser.panels

import java.net.URI

/**
 * Pure (Android-free, unit-testable) mesh resolution + decision logic.
 *
 * ASSUMPTIONS (labelled honestly — no Victorious Mesh API is used or invented):
 *  - Victorious Mesh is Headscale/Tailscale-style, so node addresses are assumed to
 *    live in the Tailscale CGNAT range 100.64.0.0/10 (IPv4) or fd7a:115c:a1e0::/48
 *    (IPv6 ULA). If Victorious Mesh uses a different prefix, update [isMeshIpv4]/[isMeshIpv6].
 *  - MagicDNS-style names are assumed to end in `.ts.net` or a configurable tailnet
 *    base domain (see [MeshConfig.magicDnsSuffixes]); bare single-label names
 *    (e.g. `node-alpha`) are treated as mesh node names when the panel is flagged
 *    `reachOverMesh`.
 *  - There is NO confirmed Victorious Mesh content-provider / intent / Broadcast
 *    contract published for this browser. Tunnel-up detection is therefore a
 *    best-effort probe of known interface name prefixes + CGNAT address presence.
 *    Coordinate with the Victorious Mesh agent for a real published contract.
 *    Labels: [Certain] for host classification math; [Likely] for CGNAT/MagicDNS
 *    conventions; [Guessing] for interface-name heuristics.
 */
object MeshConfig {
    /** [Likely] Tailscale/Headscale CGNAT IPv4. */
    const val CGNAT_PREFIX_BITS = 10
    const val CGNAT_NETWORK = 0x64400000 // 100.64.0.0

    /** [Likely] Tailscale IPv6 ULA prefix. */
    val IPV6_MESH_PREFIX = listOf(0xfd7a, 0x115c, 0xa1e0)

    /**
     * [Likely] MagicDNS suffixes. Victorious Mesh may use a custom base domain —
     * keep this list editable so the owner can add their Headscale base domain
     * without a code change later (pass via PanelStore or prefs if needed).
     */
    val magicDnsSuffixes: List<String> = listOf(
        ".ts.net",
        ".tailnet",
        ".mesh.victorious",
    )

    /**
     * [Guessing] Interface name prefixes commonly used by Tailscale/Headscale
     * userspace or kernel TUN on Android. Confirm with Victorious Mesh agent.
     */
    val meshIfacePrefixes: List<String> = listOf(
        "tun",      // generic TUN (Android VpnService often uses tun0)
        "tailscale",
        "ts-",
        "wg",        // wireguard (some Headscale clients)
        "headscale",
        "victorious",
    )
}

/**
 * Classification of a host string extracted from a panel URL.
 */
enum class HostKind {
    /** 127.0.0.1 / ::1 / localhost */
    LOOPBACK,
    /** Plain LAN / public IPv4 or hostname (not mesh-shaped) */
    DIRECT,
    /** IPv4 in 100.64.0.0/10 */
    MESH_IPV4,
    /** IPv6 in fd7a:115c:a1e0::/48 */
    MESH_IPV6,
    /** Ends with a known MagicDNS suffix */
    MAGIC_DNS,
    /** Bare single-label name (e.g. "node-alpha") treated as mesh node when flagged */
    MESH_NODE_NAME,
    /** Empty / unparseable */
    UNKNOWN,
}

/**
 * Outcome of a mesh reachability decision for a panel open attempt.
 */
sealed class MeshOpenDecision {
    /** Tunnel appears up and TCP probe succeeded — safe to open [url] in WebView. */
    data class Open(val url: String) : MeshOpenDecision()

    /**
     * Host looks like a mesh target, but nothing answered the probe.
     * [tunnelLikelyUp] true → name resolved / route exists but service down;
     * false → tunnel appears down (actionable: bring up Victorious Mesh).
     */
    data class Blocked(
        val reason: MeshBlockReason,
        val message: String,
        val tunnelLikelyUp: Boolean,
    ) : MeshOpenDecision()
}

enum class MeshBlockReason {
    /** DNS / host classification failed. */
    UNKNOWN_HOST,
    /** Probe failed and no mesh interface / CGNAT address detected. */
    TUNNEL_DOWN,
    /** Mesh interface or CGNAT address seen, but TCP probe to target failed. */
    UNREACHABLE,
    /** Panel not flagged for mesh — caller should use plain reachability. */
    NOT_MESH_PANEL,
}

data class MeshProbeSnapshot(
    /** [Guessing] Any network interface whose name matches [MeshConfig.meshIfacePrefixes]. */
    val meshInterfacePresent: Boolean,
    /** [Likely] Any local address in the CGNAT / mesh IPv6 range. */
    val meshAddressPresent: Boolean,
    /** Result of TCP connect to the resolved host:port. */
    val tcpReachable: Boolean,
    /** Host classification. */
    val hostKind: HostKind,
    /** Host string extracted from the panel URL and used for the probe. */
    val resolvedHost: String?,
    /**
     * [Certain] Result of name resolution (InetAddress lookup) for name-based hosts.
     * `null` = not applicable (IP literal / nothing to resolve); `true` = resolved;
     * `false` = lookup failed (unknown host, or MagicDNS not serving because the
     * tunnel is down).
     */
    val dnsResolved: Boolean? = null,
    /**
     * [Likely] Android reports a network with TRANSPORT_VPN. Soft signal only —
     * ANY VPN (not just Victorious Mesh) sets this.
     */
    val vpnTransportActive: Boolean = false,
) {
    /** Best-effort "some mesh-style tunnel seems up" — see labels on each field. */
    val tunnelLikelyUp: Boolean
        get() = meshInterfacePresent || meshAddressPresent || vpnTransportActive
}

object MeshResolution {

    /**
     * Extract host + effective port from a panel URL (adds http:// if missing).
     */
    fun parseTarget(url: String): Pair<String?, Int> {
        return try {
            val uri = URI(if ("://" in url) url else "http://$url")
            val host = uri.host
            val port = when {
                uri.port > 0 -> uri.port
                uri.scheme.equals("https", ignoreCase = true) -> 443
                else -> 80
            }
            host to port
        } catch (_: Exception) {
            null to 80
        }
    }

    /** Classify a host string. Pure — no network I/O. */
    fun classifyHost(host: String?): HostKind {
        if (host.isNullOrBlank()) return HostKind.UNKNOWN
        val h = host.trim().lowercase().trimEnd('.')

        if (h == "localhost" || h == "127.0.0.1" || h == "::1" || h == "0:0:0:0:0:0:0:1") {
            return HostKind.LOOPBACK
        }

        // IPv4?
        val ipv4 = parseIpv4(h)
        if (ipv4 != null) {
            return if (isMeshIpv4(ipv4)) HostKind.MESH_IPV4 else HostKind.DIRECT
        }

        // IPv6? (URI may strip brackets already)
        val bare = h.removePrefix("[").removeSuffix("]")
        if (bare.contains(':')) {
            return if (isMeshIpv6(bare)) HostKind.MESH_IPV6 else HostKind.DIRECT
        }

        // MagicDNS suffix?
        if (MeshConfig.magicDnsSuffixes.any { h.endsWith(it) }) {
            return HostKind.MAGIC_DNS
        }

        // Single-label (no dots) → mesh node name candidate
        if (!h.contains('.')) {
            return HostKind.MESH_NODE_NAME
        }

        // Multi-label hostname without MagicDNS suffix → treat as direct
        return HostKind.DIRECT
    }

    fun isMeshShaped(kind: HostKind): Boolean = when (kind) {
        HostKind.MESH_IPV4, HostKind.MESH_IPV6, HostKind.MAGIC_DNS, HostKind.MESH_NODE_NAME -> true
        else -> false
    }

    /**
     * Map a [MeshProbeSnapshot] into an open / block decision for a mesh panel.
     * Pure decision function — unit-tested.
     */
    fun decide(
        panelUrl: String,
        reachOverMesh: Boolean,
        snapshot: MeshProbeSnapshot,
    ): MeshOpenDecision {
        if (!reachOverMesh) {
            return MeshOpenDecision.Blocked(
                reason = MeshBlockReason.NOT_MESH_PANEL,
                message = "Panel is not flagged for mesh; use direct reachability.",
                tunnelLikelyUp = false,
            )
        }

        val tunnelUp = snapshot.tunnelLikelyUp
        val host = snapshot.resolvedHost

        if (snapshot.hostKind == HostKind.UNKNOWN || host.isNullOrBlank()) {
            return MeshOpenDecision.Blocked(
                reason = MeshBlockReason.UNKNOWN_HOST,
                message = "Unknown host — could not parse a mesh node name or address from the panel URL.",
                tunnelLikelyUp = tunnelUp,
            )
        }

        if (snapshot.tcpReachable) {
            return MeshOpenDecision.Open(panelUrl)
        }

        // Name lookup failed.
        if (snapshot.dnsResolved == false) {
            return if (tunnelUp) {
                MeshOpenDecision.Blocked(
                    reason = MeshBlockReason.UNKNOWN_HOST,
                    message = "Unknown mesh host \"$host\" — the name did not resolve even though " +
                        "a mesh tunnel appears up. Check the node name / MagicDNS base domain, " +
                        "or use the node's mesh IP.",
                    tunnelLikelyUp = true,
                )
            } else {
                MeshOpenDecision.Blocked(
                    reason = MeshBlockReason.TUNNEL_DOWN,
                    message = "Victorious Mesh tunnel appears down — bring it up in the Victorious Mesh app, " +
                        "then retry. (\"$host\" did not resolve; mesh names usually only resolve while " +
                        "the tunnel is up. This browser does not hold the VpnService slot.)",
                    tunnelLikelyUp = false,
                )
            }
        }

        // Name resolved (or IP literal) but nothing answered.
        return if (tunnelUp) {
            MeshOpenDecision.Blocked(
                reason = MeshBlockReason.UNREACHABLE,
                message = "Mesh target \"$host\" did not respond. " +
                    "Victorious Mesh tunnel appears up, but this node/service is unreachable. " +
                    "Check the node is online and the port is correct.",
                tunnelLikelyUp = true,
            )
        } else {
            val resolvedNote = if (snapshot.dnsResolved == true) "\"$host\" resolves but has no route. " else ""
            MeshOpenDecision.Blocked(
                reason = MeshBlockReason.TUNNEL_DOWN,
                message = "Victorious Mesh tunnel appears down — bring it up in the Victorious Mesh app, " +
                    "then retry. $resolvedNote" +
                    "(This browser does not hold the VpnService slot — mesh routing comes from Victorious Mesh.)",
                tunnelLikelyUp = false,
            )
        }
    }

    /** True when [kind] needs a name lookup (not an IP literal / unknown). */
    fun needsDnsLookup(kind: HostKind): Boolean = when (kind) {
        HostKind.MAGIC_DNS, HostKind.MESH_NODE_NAME, HostKind.DIRECT -> true
        else -> false
    }

    fun isMeshIpv4(address: Int): Boolean {
        val mask = (-1) shl (32 - MeshConfig.CGNAT_PREFIX_BITS)
        return (address and mask) == MeshConfig.CGNAT_NETWORK
    }

    fun isMeshIpv6(host: String): Boolean {
        // Expand / compare first 3 hextets against fd7a:115c:a1e0
        val parts = expandIpv6(host) ?: return false
        if (parts.size < 3) return false
        return parts[0] == MeshConfig.IPV6_MESH_PREFIX[0] &&
            parts[1] == MeshConfig.IPV6_MESH_PREFIX[1] &&
            parts[2] == MeshConfig.IPV6_MESH_PREFIX[2]
    }

    fun parseIpv4(host: String): Int? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        var acc = 0
        for (p in parts) {
            val n = p.toIntOrNull() ?: return null
            if (n !in 0..255) return null
            acc = (acc shl 8) or n
        }
        return acc
    }

    /** Minimal IPv6 expander for prefix checks (handles :: once). */
    fun expandIpv6(host: String): List<Int>? {
        val s = host.lowercase()
        if (s.count { it == ':' } < 2) return null
        val sides = s.split("::", limit = 2)
        fun parseSide(side: String): List<Int>? {
            if (side.isEmpty()) return emptyList()
            return side.split(':').map {
                it.toIntOrNull(16)?.takeIf { v -> v in 0..0xffff } ?: return null
            }
        }
        return when (sides.size) {
            1 -> {
                val parts = parseSide(sides[0]) ?: return null
                if (parts.size != 8) return null
                parts
            }
            2 -> {
                val left = parseSide(sides[0]) ?: return null
                val right = parseSide(sides[1]) ?: return null
                val fill = 8 - left.size - right.size
                if (fill < 0) return null
                left + List(fill) { 0 } + right
            }
            else -> null
        }
    }
}
