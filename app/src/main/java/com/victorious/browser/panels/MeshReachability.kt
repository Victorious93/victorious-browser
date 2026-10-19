package com.victorious.browser.panels

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Collections

/**
 * Android-side mesh reachability probe for fleet panels flagged [Panel.reachOverMesh].
 *
 * HONEST CONTRACT STATUS
 * ----------------------
 * No published Victorious Mesh content-provider, Broadcast, or Intent contract was
 * confirmed in this repo or via public docs at implementation time. Therefore:
 *
 *  [Certain]  TCP connect probe to host:port (same idea as [ReachabilityChecker]).
 *  [Certain]  Host classification via [MeshResolution.classifyHost].
 *  [Certain]  Separate name-resolution step (InetAddress lookup) so an unknown
 *             host is distinguished from "resolves but no route".
 *  [Likely]   Presence of a local address in Tailscale CGNAT 100.64.0.0/10 or
 *             fd7a:115c:a1e0::/48 means *some* mesh-style tunnel is up.
 *  [Guessing] Scanning [NetworkInterface] names for prefixes in
 *             [MeshConfig.meshIfacePrefixes] (tun/tailscale/wg/…). Android VpnService
 *             typically exposes tun0; Victorious Mesh may use a different name.
 *  [Guessing] Package-presence check for a configurable Victorious Mesh package
 *             name — useful UX hint only; not proof the tunnel is up.
 *
 * Follow-up: coordinate with the Victorious Mesh agent to publish a stable
 * ContentProvider / Intent contract (e.g. `content://com.victorious.mesh/status`
 * returning tunnelUp + node list). Until then this probe is best-effort.
 *
 * This browser deliberately does NOT claim the VpnService slot.
 */
object MeshReachability {
    private const val TCP_TIMEOUT_MS = 1_800

    /**
     * Known / candidate package ids for the Victorious Mesh app.
     * [Guessing] — confirm with the Mesh agent and trim this list.
     */
    val MESH_PACKAGE_CANDIDATES: List<String> = listOf(
        "com.victorious.mesh",
        "com.victorious.meshvpn",
        "com.victorious.vpn",
    )

    /**
     * Full probe for a mesh panel: classify host, look for mesh iface/address
     * (+ optional VPN-transport soft signal when [context] is given), resolve the
     * name, attempt TCP connect, return a decision.
     */
    suspend fun resolveAndDecide(panel: Panel, context: Context? = null): MeshOpenDecision =
        withContext(Dispatchers.IO) {
            val snapshot = probeBlocking(panel.url, context)
            MeshResolution.decide(panel.url, panel.reachOverMesh, snapshot)
        }

    /**
     * Probe only (no decide) — used by list-row recheck for mesh panels.
     */
    suspend fun probeFor(url: String, context: Context? = null): MeshProbeSnapshot =
        withContext(Dispatchers.IO) { probeBlocking(url, context) }

    /** Launch intent for an installed Victorious Mesh candidate package, or null. */
    fun meshAppLaunchIntent(context: Context): android.content.Intent? {
        val pm = context.packageManager
        return MESH_PACKAGE_CANDIDATES.firstNotNullOfOrNull { pkg -> pm.getLaunchIntentForPackage(pkg) }
    }

    fun isMeshAppInstalled(context: Context): Boolean {
        val pm = context.packageManager
        return MESH_PACKAGE_CANDIDATES.any { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    /**
     * Best-effort: any local interface/address that looks like a mesh tunnel.
     * Safe to call on main thread for a quick UI hint (does no TCP).
     */
    fun tunnelSignalsPresent(): Boolean {
        return detectMeshInterface() || detectMeshAddress()
    }

    // --- internals ---

    private fun probeBlocking(url: String, context: Context?): MeshProbeSnapshot {
        val (host, port) = MeshResolution.parseTarget(url)
        val kind = MeshResolution.classifyHost(host)
        val iface = detectMeshInterface()
        val addr = detectMeshAddress()
        val vpn = context?.let { vpnTransportActive(it) } ?: false

        // [Certain] Name resolution step, separate from TCP, so "unknown host"
        // can be told apart from "resolves but no route".
        val isLiteral = host != null &&
            (MeshResolution.parseIpv4(host) != null || host.contains(':'))
        val dns: Boolean? = if (host.isNullOrBlank() || isLiteral || !MeshResolution.needsDnsLookup(kind)) {
            null
        } else {
            try {
                InetAddress.getAllByName(host).isNotEmpty()
            } catch (_: Exception) {
                false
            }
        }

        val tcp = if (host.isNullOrBlank() || dns == false) {
            false
        } else {
            try {
                Socket().use { sock ->
                    sock.connect(InetSocketAddress(host, port), TCP_TIMEOUT_MS)
                }
                true
            } catch (_: Exception) {
                false
            }
        }
        return MeshProbeSnapshot(
            meshInterfacePresent = iface,
            meshAddressPresent = addr,
            tcpReachable = tcp,
            hostKind = kind,
            resolvedHost = host,
            dnsResolved = dns,
            vpnTransportActive = vpn,
        )
    }

    /**
     * [Guessing] Walk [NetworkInterface] names for known prefixes.
     */
    fun detectMeshInterface(): Boolean {
        return try {
            val ifaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            ifaces.any { ni ->
                if (!ni.isUp || ni.isLoopback) return@any false
                val name = ni.name.lowercase()
                MeshConfig.meshIfacePrefixes.any { prefix ->
                    name == prefix || name.startsWith(prefix)
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * [Likely] Any local InetAddress in CGNAT / mesh IPv6 range.
     */
    fun detectMeshAddress(): Boolean {
        return try {
            val ifaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            ifaces.any { ni ->
                if (!ni.isUp) return@any false
                Collections.list(ni.inetAddresses).any { inet ->
                    when (inet) {
                        is Inet4Address -> {
                            val bytes = inet.address
                            if (bytes.size != 4) return@any false
                            val v = ((bytes[0].toInt() and 0xff) shl 24) or
                                ((bytes[1].toInt() and 0xff) shl 16) or
                                ((bytes[2].toInt() and 0xff) shl 8) or
                                (bytes[3].toInt() and 0xff)
                            MeshResolution.isMeshIpv4(v)
                        }
                        is Inet6Address -> {
                            MeshResolution.isMeshIpv6(inet.hostAddress?.substringBefore('%') ?: "")
                        }
                        else -> false
                    }
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Optional ConnectivityManager view — VPN transport flag.
     * [Likely] Android marks VpnService tunnels with [NetworkCapabilities.TRANSPORT_VPN].
     * Note: ANY VPN (not just Victorious Mesh) will trip this; used only as a soft signal.
     */
    fun vpnTransportActive(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            // Prefer activeNetwork; fall back to scanning known networks without
            // the deprecated allNetworks accessor when possible.
            val active = cm.activeNetwork
            if (active != null) {
                val caps = cm.getNetworkCapabilities(active)
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    return true
                }
            }
            @Suppress("DEPRECATION")
            cm.allNetworks.any { n ->
                val caps = cm.getNetworkCapabilities(n) ?: return@any false
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            }
        } catch (_: Exception) {
            false
        }
    }
}
