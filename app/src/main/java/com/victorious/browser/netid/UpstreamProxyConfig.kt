package com.victorious.browser.netid

/**
 * Upstream proxy the local LittleProxy instance chains to. Pure model + validation;
 * no Android or LittleProxy types so it can be unit-tested on the JVM.
 */
data class UpstreamProxyConfig(
    val enabled: Boolean = false,
    val type: Type = Type.HTTP,
    val host: String = "",
    val port: Int = 0,
    val username: String = "",
    val password: String = "",
) {
    enum class Type(val label: String) {
        HTTP("HTTP"),
        /** HTTP CONNECT proxy reached over TLS. */
        HTTPS("HTTPS"),
        SOCKS5("SOCKS5"),
    }

    val hasAuth: Boolean get() = username.isNotEmpty()

    /** Display string, never includes the password. */
    fun summary(): String =
        "${type.label} ${formatHostPort(host, port)}" + if (hasAuth) " (auth: $username)" else ""

    companion object {
        val OFF = UpstreamProxyConfig()

        fun formatHostPort(host: String, port: Int): String =
            if (host.contains(':')) "[$host]:$port" else "$host:$port"
    }
}

object UpstreamProxyValidator {
    sealed class Result {
        data class Ok(val config: UpstreamProxyConfig) : Result()
        data class Error(val message: String) : Result()
    }

    private val hostnameLabel = Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$")
    private val ipv4 = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")
    private val ipv6 = Regex("^[0-9A-Fa-f:.]+$")

    fun isValidHost(raw: String): Boolean {
        val host = raw.trim().removePrefix("[").removeSuffix("]")
        if (host.isEmpty() || host.length > 253) return false
        ipv4.matchEntire(host)?.let { m -> return m.groupValues.drop(1).all { it.toInt() in 0..255 } }
        if (host.contains(':')) return ipv6.matches(host) && host.count { it == ':' } in 2..7
        // All-numeric dotted strings that failed IPv4 are rejected (e.g. 999.1.1.1).
        if (host.all { it.isDigit() || it == '.' }) return false
        return host.trimEnd('.').split('.').all { hostnameLabel.matches(it) }
    }

    fun parsePort(raw: String): Int? = raw.trim().toIntOrNull()?.takeIf { it in 1..65535 }

    /** Validates raw UI input. [enabled] = false still validates if fields are non-empty. */
    fun validate(
        enabled: Boolean,
        type: UpstreamProxyConfig.Type,
        host: String,
        port: String,
        username: String,
        password: String,
    ): Result {
        val h = host.trim().removePrefix("[").removeSuffix("]")
        if (!enabled && h.isEmpty() && port.isBlank()) {
            return Result.Ok(UpstreamProxyConfig.OFF)
        }
        if (!isValidHost(h)) return Result.Error("Invalid host")
        val p = parsePort(port) ?: return Result.Error("Port must be 1–65535")
        val user = username.trim()
        if (user.isEmpty() && password.isNotEmpty()) return Result.Error("Password set without username")
        if (user.any { it == ':' } && type != UpstreamProxyConfig.Type.SOCKS5) {
            return Result.Error("Username may not contain ':' for HTTP Basic auth")
        }
        if (type == UpstreamProxyConfig.Type.SOCKS5 && (user.length > 255 || password.length > 255)) {
            return Result.Error("SOCKS5 credentials max 255 chars")
        }
        return Result.Ok(UpstreamProxyConfig(enabled, type, h, p, user, password))
    }

    /** Guards against chaining the local proxy into itself. */
    fun isSelfLoop(config: UpstreamProxyConfig, localPort: Int): Boolean {
        val loopback = config.host == "127.0.0.1" || config.host.equals("localhost", true) || config.host == "::1"
        return loopback && config.port == localPort
    }
}
