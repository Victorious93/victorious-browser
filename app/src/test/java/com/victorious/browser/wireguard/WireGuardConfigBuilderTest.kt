package com.victorious.browser.wireguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These tests run against the real `com.wireguard.config.Config` parser (see
 * [WireGuardConfigBuilder]'s doc for why that's possible in a plain JUnit test) —
 * a malformed key or CIDR here is a failure the actual WireGuard library would
 * also reject, not an assumption of ours.
 */
class WireGuardConfigBuilderTest {
    // 32 random bytes, base64 — valid WireGuard key *shape* (the library validates
    // length, not that a key was honestly generated via Curve25519 clamping).
    private val privateKey = "eMqOaKMBbTaMylldCOxeD+L9HXz737yuebUBH/9ZDrY="
    private val peerPublicKey = "kBvd2RcNR5HfV9lu88vmQHJ1nebNS9CLMgvdGgsxZ9E="

    private fun validFields(
        dnsServers: List<String> = listOf("100.64.0.1"),
        listenPort: Int? = null,
        presharedKeyBase64: String? = null,
        persistentKeepaliveSeconds: Int? = null,
    ) = WireGuardProfileFields(
        name = "fleet-node",
        privateKeyBase64 = privateKey,
        addresses = listOf("100.64.0.5/32"),
        dnsServers = dnsServers,
        listenPort = listenPort,
        peerPublicKeyBase64 = peerPublicKey,
        presharedKeyBase64 = presharedKeyBase64,
        endpoint = "headscale.example.com:51820",
        allowedIps = listOf("0.0.0.0/0", "::/0"),
        persistentKeepaliveSeconds = persistentKeepaliveSeconds,
    )

    @Test
    fun buildsWgQuickTextWithInterfaceAndPeerSections() {
        val text = WireGuardConfigBuilder.buildWgQuickText(validFields())
        assertTrue(text.contains("[Interface]"))
        assertTrue(text.contains("PrivateKey = $privateKey"))
        assertTrue(text.contains("[Peer]"))
        assertTrue(text.contains("PublicKey = $peerPublicKey"))
        assertTrue(text.contains("Endpoint = headscale.example.com:51820"))
    }

    @Test
    fun realLibraryParsesAMinimalValidProfile() {
        val result = WireGuardConfigBuilder.parse(validFields())
        assertTrue(result.isSuccess)
        val config = result.getOrThrow()
        assertEquals(1, config.peers.size)
        assertEquals("headscale.example.com", config.peers[0].endpoint.get().host)
    }

    @Test
    fun realLibraryParsesOptionalFieldsWhenPresent() {
        val result = WireGuardConfigBuilder.parse(
            validFields(listenPort = 51820, persistentKeepaliveSeconds = 25),
        )
        assertTrue(result.isSuccess)
        val iface = result.getOrThrow().`interface`
        assertEquals(51820, iface.listenPort.get())
        assertEquals(25, result.getOrThrow().peers[0].persistentKeepalive.get())
    }

    @Test
    fun realLibraryRejectsAMalformedPrivateKey() {
        val result = WireGuardConfigBuilder.parse(validFields().copy(privateKeyBase64 = "not-a-key"))
        assertTrue(result.isFailure)
    }

    @Test
    fun realLibraryRejectsAMalformedAllowedIpCidr() {
        val result = WireGuardConfigBuilder.parse(
            validFields().copy(allowedIps = listOf("not-a-cidr")),
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun realLibraryRejectsAMalformedEndpoint() {
        val result = WireGuardConfigBuilder.parse(validFields().copy(endpoint = "no-port-here"))
        assertTrue(result.isFailure)
    }
}
