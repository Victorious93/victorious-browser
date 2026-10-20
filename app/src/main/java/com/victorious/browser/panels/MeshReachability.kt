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
 * Signals, strongest first:
 *  [Likely]   Victorious Mesh bridge `mesh.status.connected` (only when the bridge
 *             toggle is on AND Mesh authorizes this browser; otherwise skipped).
 *  [Likely]   ConnectivityManager TRANSPORT_VPN network correlated with
 *             `com.victorious.mesh` (owner UID on API 29+ when visible, or a VPN
 *             link address inside the configured mesh ranges).
 *  [Likely]   Local address inside the configured mesh ranges.
 *  [Likely]   Any TRANSPORT_VPN network (soft — any VPN trips it).
 *  [Guessing] Interface-name prefixes (weak fallback; tunN naming is unreliable).
 *
 * This browser deliberately does NOT claim the VpnService slot.
 */
object MeshReachability {
    private const val TCP_TIMEOUT_MS = 1_800

    /** [Certain] Confirmed Victorious Mesh package (debug + release). */
    const val MESH_PACKAGE: String = MeshConfig.MESH_PACKAGE

    /**
     * Full probe for a mesh panel: classify host, look for mesh iface/address
     * (+ optional VPN-transport soft signal when [context] is given), resolve the
     * name, attempt TCP connect, return a decision.
     */
    suspend fun resolveAndDecide(panel: Panel, context: Context? = null): MeshOpenDecision =
        withContext(Dispatchers.IO) {
            val bridge = context?.let { bridgeConnected(it) }
            val snapshot = probeBlocking(panel.url, context).copy(bridgeConnected = bridge)
            MeshResolution.decide(panel.url, panel.reachOverMesh, snapshot)
        }

    /**
     * Probe only (no decide) — used by list-row recheck for mesh panels.
     */
    suspend fun probeFor(url: String, context: Context? = null): MeshProbeSnapshot =
        withContext(Dispatchers.IO) {
            val bridge = context?.let { bridgeConnected(it) }
            probeBlocking(url, context).copy(bridgeConnected = bridge)
        }

    /**
     * `mesh.status.connected` from the bridge, or null when the toggle is off or
     * the bridge is absent / not authorized / errors (graceful fallback).
     */
    suspend fun bridgeConnected(context: Context): Boolean? {
        if (!MeshConfig.current.bridgeEnabled) return null
        return when (val r = MeshBridgeClient(context).status()) {
            is BridgeReply.Success -> MeshBridgeProtocol.parseStatus(r.result).connected
            is BridgeReply.Failure -> null
        }
    }

    /** Launch intent for Victorious Mesh, or null. */
    fun meshAppLaunchIntent(context: Context): android.content.Intent? =
        try { context.packageManager.getLaunchIntentForPackage(MESH_PACKAGE) } catch (_: Exception) { null }

    fun isMeshAppInstalled(context: Context): Boolean = meshUid(context) != null

    /** UID of the installed Mesh package, or null. */
    fun meshUid(context: Context): Int? = try {
        context.packageManager.getApplicationInfo(MESH_PACKAGE, 0).uid
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (_: Exception) {
        null
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
        val correlated = context?.let { meshVpnCorrelated(it) } ?: false

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
            meshVpnCorrelated = correlated,
        )
    }

    /**
     * [Guessing] WEAK fallback: walk [NetworkInterface] names for known prefixes.
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
     * [Likely] Any local InetAddress inside the configured mesh ranges.
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

    /**
     * [Likely] A TRANSPORT_VPN network that belongs to Victorious Mesh:
     * owner UID (API 29+, often hidden from non-owner apps → INVALID_UID) equals
     * the `com.victorious.mesh` uid, OR the VPN's LinkProperties carry an
     * address inside the configured mesh ranges.
     */
    fun meshVpnCorrelated(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val meshUid = meshUid(context)
            val ranges = MeshConfig.current.parsedRanges
            @Suppress("DEPRECATION")
            cm.allNetworks.any { n ->
                val caps = cm.getNetworkCapabilities(n) ?: return@any false
                if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return@any false
                if (meshUid != null && android.os.Build.VERSION.SDK_INT >= 29 &&
                    caps.ownerUid == meshUid
                ) return@any true
                val lp = cm.getLinkProperties(n) ?: return@any false
                lp.linkAddresses.any { la ->
                    val host = la.address.hostAddress?.substringBefore('%') ?: return@any false
                    MeshRanges.matches(host, ranges)
                }
            }
        } catch (_: Exception) {
            false
        }
    }
}
