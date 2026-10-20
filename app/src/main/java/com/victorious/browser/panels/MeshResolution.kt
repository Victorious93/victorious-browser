package com.victorious.browser.panels

import java.net.URI

/**
 * Pure (Android-free, unit-testable) mesh resolution + decision logic.
 *
 * CONFIRMED by the Victorious Mesh agent (see docs/MANUAL.md "Mesh"):
 *  [Certain] Mesh package is `com.victorious.mesh` (debug + release, no suffix).
 *  [Certain] Default address ranges are Tailscale/Headscale 100.64.0.0/10 and
 *            fd7a:115c:a1e0::/48 — but they are owner-EDITABLE ([MeshSettings]).
 *  [Certain] MagicDNS suffix is NOT *.ts.net; it is the owner's Headscale
 *            `base_domain` (unknown → configurable, default unset; when unset
 *            MagicDNS-suffix matching is skipped).
 *  [Guessing] Interface-name prefixes are only a weak fallback signal.
 */
data class MeshSettings(
    /** CIDR strings (IPv4 or IPv6), editable by the owner. */
    val ranges: List<String> = MeshConfig.DEFAULT_RANGES,
    /** Headscale `base_domain` (e.g. "mesh.example"). Null/blank = unset. */
    val baseDomain: String? = null,
    /** Best-effort use of the Victorious Mesh bridge client (read-only). */
    val bridgeEnabled: Boolean = false,
) {
    /** Parsed, valid CIDRs; invalid entries are dropped (see [MeshRanges.parseAll]). */
    val parsedRanges: List<Cidr> by lazy { MeshRanges.parseAll(ranges).valid }

    /** Normalised base domain without leading/trailing dots, or null when unset. */
    val normalizedBaseDomain: String? by lazy { MeshConfig.normalizeBaseDomain(baseDomain) }
}

object MeshConfig {
    /** [Certain] Confirmed Victorious Mesh applicationId (debug and release). */
    const val MESH_PACKAGE = "com.victorious.mesh"

    const val DEFAULT_IPV4_RANGE = "100.64.0.0/10"
    const val DEFAULT_IPV6_RANGE = "fd7a:115c:a1e0::/48"
    val DEFAULT_RANGES: List<String> = listOf(DEFAULT_IPV4_RANGE, DEFAULT_IPV6_RANGE)

    /** Live settings (loaded from [MeshConfigStore] at runtime; defaults in tests). */
    @Volatile
    var current: MeshSettings = MeshSettings()

    /**
     * [Guessing] WEAK fallback only: interface name prefixes. tun0/tunN naming is
     * unreliable — primary detection is ConnectivityManager TRANSPORT_VPN +
     * owner/package correlation (see MeshReachability).
     */
    val meshIfacePrefixes: List<String> = listOf("tun", "tailscale", "ts-", "wg", "headscale", "victorious")

    fun normalizeBaseDomain(raw: String?): String? {
        val t = raw?.trim()?.lowercase()?.trim('.') ?: return null
        if (t.isEmpty() || t.contains(' ') || t.contains('/')) return null
        return t
    }
}

/** A parsed CIDR: [network] bytes (4 or 16) + [prefix] length. */
data class Cidr(val network: List<Int>, val prefix: Int) {
    val isV4: Boolean get() = network.size == 4

    /** [addr] must be the same family byte list (0..255 each). */
    fun contains(addr: List<Int>): Boolean {
        if (addr.size != network.size) return false
        var bits = prefix
        for (i in addr.indices) {
            if (bits <= 0) return true
            val take = minOf(8, bits)
            val mask = (0xff shl (8 - take)) and 0xff
            if ((addr[i] and mask) != (network[i] and mask)) return false
            bits -= take
        }
        return true
    }
}

data class RangeParseResult(val valid: List<Cidr>, val invalid: List<String>)

object MeshRanges {
    /** Parse "a.b.c.d/n" or "v6::/n" (no DNS). Null if malformed. */
    fun parse(raw: String): Cidr? {
        val t = raw.trim()
        val slash = t.indexOf('/')
        if (slash <= 0) return null
        val ip = t.substring(0, slash)
        val prefix = t.substring(slash + 1).toIntOrNull() ?: return null
        val bytes = addressBytes(ip) ?: return null
        if (prefix < 0 || prefix > bytes.size * 8) return null
        return Cidr(bytes, prefix)
    }

    /** Split on commas/newlines/whitespace; returns valid + rejected entries. */
    fun parseAll(entries: List<String>): RangeParseResult {
        val valid = mutableListOf<Cidr>()
        val invalid = mutableListOf<String>()
        entries.flatMap { splitText(it) }.forEach { e ->
            val c = parse(e)
            if (c != null) valid += c else invalid += e
        }
        return RangeParseResult(valid, invalid)
    }

    fun splitText(text: String): List<String> =
        text.split(',', '\n', ' ', '\t', ';').map { it.trim() }.filter { it.isNotEmpty() }

    /** Address literal → byte list (IPv4 4 bytes, IPv6 16 bytes). No DNS. */
    fun addressBytes(ip: String): List<Int>? {
        val h = ip.trim().removePrefix("[").removeSuffix("]").substringBefore('%')
        MeshResolution.parseIpv4(h)?.let { v ->
            return listOf((v ushr 24) and 0xff, (v ushr 16) and 0xff, (v ushr 8) and 0xff, v and 0xff)
        }
        if (!h.contains(':')) return null
        val hextets = MeshResolution.expandIpv6(h) ?: return null
        return hextets.flatMap { listOf((it ushr 8) and 0xff, it and 0xff) }
    }

    fun matches(ip: String, ranges: List<Cidr>): Boolean {
        val b = addressBytes(ip) ?: return false
        return ranges.any { it.contains(b) }
    }
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
    /**
     * [Likely] A TRANSPORT_VPN network was correlated with Victorious Mesh
     * (owner UID == com.victorious.mesh uid, or VPN link address in mesh ranges).
     */
    val meshVpnCorrelated: Boolean = false,
    /**
     * Authoritative answer from the Victorious Mesh bridge (`mesh.status.connected`)
     * when the bridge is enabled AND authorized. `null` = bridge unavailable /
     * not authorized → fall back to passive inference.
     */
    val bridgeConnected: Boolean? = null,
) {
    /** Bridge answer when present, otherwise passive best-effort signals. */
    val tunnelLikelyUp: Boolean
        get() = bridgeConnected
            ?: (meshVpnCorrelated || meshAddressPresent || vpnTransportActive || meshInterfacePresent)
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
    fun classifyHost(host: String?, settings: MeshSettings = MeshConfig.current): HostKind {
        if (host.isNullOrBlank()) return HostKind.UNKNOWN
        val h = host.trim().lowercase().trimEnd('.')

        if (h == "localhost" || h == "127.0.0.1" || h == "::1" || h == "0:0:0:0:0:0:0:1") {
            return HostKind.LOOPBACK
        }

        // IPv4?
        val ipv4 = parseIpv4(h)
        if (ipv4 != null) {
            return if (isMeshIpv4(ipv4, settings)) HostKind.MESH_IPV4 else HostKind.DIRECT
        }

        // IPv6? (URI may strip brackets already)
        val bare = h.removePrefix("[").removeSuffix("]")
        if (bare.contains(':')) {
            return if (isMeshIpv6(bare, settings)) HostKind.MESH_IPV6 else HostKind.DIRECT
        }

        // MagicDNS suffix = owner's Headscale base_domain. Skipped when unset.
        if (matchesBaseDomain(h, settings.normalizedBaseDomain)) {
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

    /** True when [host] equals or is a subdomain of [baseDomain]; false when unset. */
    fun matchesBaseDomain(host: String?, baseDomain: String?): Boolean {
        val b = MeshConfig.normalizeBaseDomain(baseDomain) ?: return false
        val h = host?.trim()?.lowercase()?.trimEnd('.') ?: return false
        return h.endsWith(".$b")
    }

    fun isMeshIpv4(address: Int, settings: MeshSettings = MeshConfig.current): Boolean {
        val b = listOf((address ushr 24) and 0xff, (address ushr 16) and 0xff, (address ushr 8) and 0xff, address and 0xff)
        return settings.parsedRanges.any { it.isV4 && it.contains(b) }
    }

    fun isMeshIpv6(host: String, settings: MeshSettings = MeshConfig.current): Boolean {
        val b = MeshRanges.addressBytes(host) ?: return false
        if (b.size != 16) return false
        return settings.parsedRanges.any { !it.isV4 && it.contains(b) }
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
