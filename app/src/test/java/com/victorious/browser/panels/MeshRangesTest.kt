package com.victorious.browser.panels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshRangesTest {
    @Test
    fun confirmed_package_single_value() {
        assertEquals("com.victorious.mesh", MeshConfig.MESH_PACKAGE)
        assertEquals(MeshConfig.MESH_PACKAGE, MeshReachability.MESH_PACKAGE)
    }

    @Test
    fun defaults_are_tailscale_headscale_ranges_and_unset_domain() {
        val s = MeshSettings()
        assertEquals(listOf("100.64.0.0/10", "fd7a:115c:a1e0::/48"), s.ranges)
        assertNull(s.normalizedBaseDomain)
        assertFalse(s.bridgeEnabled)
        assertEquals(2, s.parsedRanges.size)
    }

    @Test
    fun parse_valid_and_invalid() {
        assertEquals(Cidr(listOf(100, 64, 0, 0), 10), MeshRanges.parse("100.64.0.0/10"))
        assertEquals(16, MeshRanges.parse("fd7a:115c:a1e0::/48")!!.network.size)
        assertNull(MeshRanges.parse("100.64.0.0"))
        assertNull(MeshRanges.parse("100.64.0.0/33"))
        assertNull(MeshRanges.parse("fd7a::/129"))
        assertNull(MeshRanges.parse("host.example/24"))
        assertNull(MeshRanges.parse("/8"))
        val r = MeshRanges.parseAll(listOf("10.0.0.0/8, bogus\nfd00::/8;  "))
        assertEquals(2, r.valid.size)
        assertEquals(listOf("bogus"), r.invalid)
    }

    @Test
    fun match_ipv4_and_ipv6() {
        val ranges = MeshSettings().parsedRanges
        assertTrue(MeshRanges.matches("100.64.0.1", ranges))
        assertTrue(MeshRanges.matches("100.127.255.255", ranges))
        assertFalse(MeshRanges.matches("100.128.0.0", ranges))
        assertTrue(MeshRanges.matches("fd7a:115c:a1e0::1", ranges))
        assertTrue(MeshRanges.matches("[fd7a:115c:a1e0:ffff::9]", ranges))
        assertFalse(MeshRanges.matches("fd7a:115c:a1e1::1", ranges))
        assertFalse(MeshRanges.matches("not-an-ip", ranges))
    }

    @Test
    fun configurable_ranges_override_defaults() {
        val custom = MeshSettings(ranges = listOf("10.99.0.0/16", "fd00:abcd::/32"))
        assertEquals(HostKind.MESH_IPV4, MeshResolution.classifyHost("10.99.3.4", custom))
        assertEquals(HostKind.DIRECT, MeshResolution.classifyHost("100.64.0.1", custom))
        assertEquals(HostKind.MESH_IPV6, MeshResolution.classifyHost("fd00:abcd::5", custom))
        assertEquals(HostKind.DIRECT, MeshResolution.classifyHost("fd7a:115c:a1e0::1", custom))
    }

    @Test
    fun odd_prefixes_and_zero() {
        val c = MeshRanges.parse("192.168.4.0/22")!!
        assertTrue(c.contains(MeshRanges.addressBytes("192.168.7.255")!!))
        assertFalse(c.contains(MeshRanges.addressBytes("192.168.8.0")!!))
        assertTrue(MeshRanges.parse("0.0.0.0/0")!!.contains(listOf(8, 8, 8, 8)))
        assertFalse(c.contains(MeshRanges.addressBytes("::1")!!))
    }

    @Test
    fun bridge_answer_overrides_passive_signals() {
        val base = MeshProbeSnapshot(false, true, false, HostKind.MESH_IPV4, "100.64.0.1", vpnTransportActive = true)
        assertTrue(base.tunnelLikelyUp)
        assertFalse(base.copy(bridgeConnected = false).tunnelLikelyUp)
        assertTrue(base.copy(meshAddressPresent = false, vpnTransportActive = false, bridgeConnected = true).tunnelLikelyUp)
        assertTrue(base.copy(meshAddressPresent = false, vpnTransportActive = false, meshVpnCorrelated = true).tunnelLikelyUp)
    }
}
