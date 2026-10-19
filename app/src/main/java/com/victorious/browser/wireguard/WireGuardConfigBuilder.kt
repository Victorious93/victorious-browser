package com.victorious.browser.wireguard

import com.wireguard.config.BadConfigException
import com.wireguard.config.Config
import java.io.BufferedReader
import java.io.StringReader

/**
 * One peer's worth of structured WireGuard settings — the fields an "advanced
 * WireGuard settings" screen would expose, as opposed to pasting a raw wg-quick
 * file. No Android or native-code dependency: `com.wireguard.config.*` (used here)
 * is plain Java, separate from `com.wireguard.android.backend.GoBackend`'s JNI
 * calls, so [buildWgQuickText] and [parse] run and are unit-tested in an ordinary
 * JVM test against the real WireGuard-for-Android library's own parser — not a
 * hand-written assumption about what it accepts.
 */
data class WireGuardProfileFields(
    val name: String,
    val privateKeyBase64: String,
    val addresses: List<String>,
    val dnsServers: List<String> = emptyList(),
    val listenPort: Int? = null,
    val peerPublicKeyBase64: String,
    val presharedKeyBase64: String? = null,
    val endpoint: String,
    val allowedIps: List<String>,
    val persistentKeepaliveSeconds: Int? = null,
)

object WireGuardConfigBuilder {
    /** Assembles [fields] into a wg-quick-format config text (the same shape `wg-quick` / Headscale CLIs use). */
    fun buildWgQuickText(fields: WireGuardProfileFields): String = buildString {
        append("[Interface]\n")
        append("PrivateKey = ${fields.privateKeyBase64}\n")
        if (fields.addresses.isNotEmpty()) append("Address = ${fields.addresses.joinToString(", ")}\n")
        if (fields.dnsServers.isNotEmpty()) append("DNS = ${fields.dnsServers.joinToString(", ")}\n")
        fields.listenPort?.let { append("ListenPort = $it\n") }
        append("\n[Peer]\n")
        append("PublicKey = ${fields.peerPublicKeyBase64}\n")
        fields.presharedKeyBase64?.let { append("PresharedKey = $it\n") }
        append("Endpoint = ${fields.endpoint}\n")
        if (fields.allowedIps.isNotEmpty()) append("AllowedIPs = ${fields.allowedIps.joinToString(", ")}\n")
        fields.persistentKeepaliveSeconds?.let { append("PersistentKeepalive = $it\n") }
    }

    /**
     * Parses [fields] via the real library's [Config.parse] — this is where a bad key,
     * a malformed CIDR, an invalid endpoint, etc. actually get caught, using the same
     * validation GoBackend itself relies on, not a reimplementation of it.
     */
    fun parse(fields: WireGuardProfileFields): Result<Config> = try {
        val text = buildWgQuickText(fields)
        Result.success(Config.parse(BufferedReader(StringReader(text))))
    } catch (e: BadConfigException) {
        Result.failure(e)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
