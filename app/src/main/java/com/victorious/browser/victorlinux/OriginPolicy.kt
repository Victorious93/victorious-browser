package com.victorious.browser.victorlinux

import java.net.URI

/**
 * Where a VictorLinux PC may be reached from this phone. `127.0.0.1` on Android is the PHONE, so a
 * loopback origin is refused unless the owner states it is a tunnel (adb reverse, SSH forward, ...).
 * Plain http is only accepted for private-network IP literals; everything else must be https.
 */
object OriginPolicy {
    sealed class Result {
        data class Ok(val origin: String, val host: String, val port: Int, val https: Boolean, val warnings: List<String>) : Result()
        data class Rejected(val reason: String) : Result()
    }

    fun parse(raw: String, tunnel: Boolean = false): Result {
        val text = raw.trim()
        if (text.isEmpty()) return Result.Rejected("Enter the PC's address, for example https://192.168.1.20:8080")
        val uri = try { URI(if ("://" in text) text else "https://$text") } catch (_: Exception) { return Result.Rejected("That is not a valid address.") }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") return Result.Rejected("Only http:// and https:// addresses are supported.")
        if (uri.userInfo != null) return Result.Rejected("Do not put a username, password or key in the address.")
        if (uri.rawQuery != null || uri.rawFragment != null || (uri.rawPath ?: "").trim('/').isNotEmpty())
            return Result.Rejected("Use only the PC's address and port, without a path, query or key.")
        val host = uri.host?.lowercase()?.removePrefix("[")?.removeSuffix("]") ?: return Result.Rejected("The address has no host name.")
        val https = scheme == "https"
        val port = if (uri.port > 0) uri.port else if (https) 443 else 80
        val warnings = mutableListOf<String>()
        if (isLoopback(host)) {
            if (!tunnel) return Result.Rejected("$host is this phone, not your PC. Use the PC's LAN or VPN/mesh address, or confirm that this is a tunnel to the PC.")
            warnings += "Loopback address: only valid while the tunnel to the PC is running."
        }
        if (!https) {
            if (!(isPrivateIpLiteral(host) || (tunnel && isLoopback(host)))) return Result.Rejected("Plain http is only allowed for private-network IP addresses. Use https for $host.")
            warnings += "Unencrypted connection: the key and chat travel in clear text on this network. Prefer https with a pinned certificate."
        }
        val shownHost = if (":" in host) "[$host]" else host
        val defaultPort = if (https) 443 else 80
        val origin = "$scheme://$shownHost" + if (port != defaultPort) ":$port" else ""
        return Result.Ok(origin, host, port, https, warnings)
    }

    fun isLoopback(host: String): Boolean {
        val h = host.lowercase()
        if (h == "localhost" || h == "::1" || h == "0:0:0:0:0:0:0:1") return true
        val p = ipv4(h) ?: return false
        return p[0] == 127
    }

    private fun ipv4(h: String): IntArray? {
        val parts = h.split('.')
        if (parts.size != 4) return null
        val n = parts.map { it.toIntOrNull() ?: return null }
        return if (n.all { it in 0..255 }) n.toIntArray() else null
    }

    fun isPrivateIpLiteral(host: String): Boolean {
        val h = host.lowercase()
        ipv4(h)?.let { p ->
            return p[0] == 10 || (p[0] == 172 && p[1] in 16..31) || (p[0] == 192 && p[1] == 168) ||
                (p[0] == 100 && p[1] in 64..127) || (p[0] == 169 && p[1] == 254)
        }
        if (":" in h) return h.startsWith("fe8") || h.startsWith("fe9") || h.startsWith("fea") || h.startsWith("feb") || h.startsWith("fc") || h.startsWith("fd")
        return false
    }

    /** True when [url] belongs to the saved [origin] exactly (scheme, host and port): the only place its key may go. */
    fun sameOrigin(origin: String, url: String): Boolean {
        val a = (parse(origin, tunnel = true) as? Result.Ok) ?: return false
        val b = (parse(url, tunnel = true) as? Result.Ok) ?: return false
        return a.origin == b.origin
    }
}
