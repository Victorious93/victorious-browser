package com.victorious.browser.panels

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI

/**
 * Lightweight async reachability check for *direct* (non-mesh) panels: attempt a
 * TCP connect to host:port with a short timeout. Does not speak HTTP — just
 * "is something listening".
 *
 * Mesh-flagged panels use [MeshReachability] instead (host classification +
 * tunnel signals + TCP probe → [MeshOpenDecision]).
 */
object ReachabilityChecker {
    private const val TIMEOUT_MS = 1_500

    suspend fun check(url: String): Reachability = withContext(Dispatchers.IO) {
        try {
            val uri = URI(if ("://" in url) url else "http://$url")
            val host = uri.host ?: return@withContext Reachability.UNKNOWN
            val port = when {
                uri.port > 0 -> uri.port
                uri.scheme.equals("https", ignoreCase = true) -> 443
                else -> 80
            }
            Socket().use { sock ->
                sock.connect(InetSocketAddress(host, port), TIMEOUT_MS)
            }
            Reachability.UP
        } catch (_: Exception) {
            Reachability.DOWN
        }
    }
}
