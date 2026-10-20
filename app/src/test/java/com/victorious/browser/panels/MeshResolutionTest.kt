package com.victorious.browser.panels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for pure mesh host classification + open/block decision mapping.
 * No Android runtime, no network I/O.
 */
class MeshResolutionTest {

    // --- classifyHost ---

    @Test
    fun classify_loopback() {
        assertEquals(HostKind.LOOPBACK, MeshResolution.classifyHost("127.0.0.1"))
        assertEquals(HostKind.LOOPBACK, MeshResolution.classifyHost("localhost"))
        assertEquals(HostKind.LOOPBACK, MeshResolution.classifyHost("::1"))
    }

    @Test
    fun classify_mesh_cgnat_ipv4() {
        assertEquals(HostKind.MESH_IPV4, MeshResolution.classifyHost("100.64.0.1"))
        assertEquals(HostKind.MESH_IPV4, MeshResolution.classifyHost("100.127.255.254"))
        // Outside /10
        assertEquals(HostKind.DIRECT, MeshResolution.classifyHost("100.128.0.1"))
        assertEquals(HostKind.DIRECT, MeshResolution.classifyHost("100.63.255.255"))
        assertEquals(HostKind.DIRECT, MeshResolution.classifyHost("192.168.1.1"))
        assertEquals(HostKind.DIRECT, MeshResolution.classifyHost("8.8.8.8"))
    }

    @Test
    fun classify_mesh_ipv6() {
        assertEquals(HostKind.MESH_IPV6, MeshResolution.classifyHost("fd7a:115c:a1e0::1"))
        assertEquals(HostKind.MESH_IPV6, MeshResolution.classifyHost("fd7a:115c:a1e0:abcd::42"))
        assertEquals(HostKind.DIRECT, MeshResolution.classifyHost("2001:db8::1"))
        assertEquals(HostKind.DIRECT, MeshResolution.classifyHost("fd00::1"))
    }

    @Test
    fun classify_magic_dns_unset_base_domain_skips_suffix_matching() {
        val unset = MeshSettings(baseDomain = null)
        // fleet.example is NOT the Victorious Mesh suffix.
        assertEquals(HostKind.DIRECT, MeshResolution.classifyHost("node-alpha.ts.net", unset))
        assertEquals(HostKind.DIRECT, MeshResolution.classifyHost("admin.mesh.victorious", unset))
        assertEquals(HostKind.DIRECT, MeshResolution.classifyHost("example.com", unset))
        assertEquals(HostKind.DIRECT, MeshResolution.classifyHost("node-alpha.ts.net", MeshSettings(baseDomain = "  ")))
    }

    @Test
    fun classify_magic_dns_with_base_domain() {
        val s = MeshSettings(baseDomain = ".Fleet.Example.")
        assertEquals(HostKind.MAGIC_DNS, MeshResolution.classifyHost("node-alpha.fleet.example", s))
        assertEquals(HostKind.MAGIC_DNS, MeshResolution.classifyHost("a.b.fleet.example.", s))
        assertEquals(HostKind.DIRECT, MeshResolution.classifyHost("node-alpha.ts.net", s))
        assertEquals(HostKind.DIRECT, MeshResolution.classifyHost("evilfleet.example", s))
        assertTrue(MeshResolution.matchesBaseDomain("x.fleet.example", "fleet.example"))
        assertFalse(MeshResolution.matchesBaseDomain("x.fleet.example", null))
    }

    @Test
    fun classify_bare_node_name() {
        assertEquals(HostKind.MESH_NODE_NAME, MeshResolution.classifyHost("node-alpha"))
        assertEquals(HostKind.MESH_NODE_NAME, MeshResolution.classifyHost("hs-admin"))
        assertEquals(HostKind.UNKNOWN, MeshResolution.classifyHost(null))
        assertEquals(HostKind.UNKNOWN, MeshResolution.classifyHost(""))
        assertEquals(HostKind.UNKNOWN, MeshResolution.classifyHost("   "))
    }

    @Test
    fun isMeshShaped() {
        assertTrue(MeshResolution.isMeshShaped(HostKind.MESH_IPV4))
        assertTrue(MeshResolution.isMeshShaped(HostKind.MESH_IPV6))
        assertTrue(MeshResolution.isMeshShaped(HostKind.MAGIC_DNS))
        assertTrue(MeshResolution.isMeshShaped(HostKind.MESH_NODE_NAME))
        assertFalse(MeshResolution.isMeshShaped(HostKind.LOOPBACK))
        assertFalse(MeshResolution.isMeshShaped(HostKind.DIRECT))
        assertFalse(MeshResolution.isMeshShaped(HostKind.UNKNOWN))
    }

    // --- parseTarget ---

    @Test
    fun parseTarget_defaults_and_ports() {
        val (h1, p1) = MeshResolution.parseTarget("http://100.64.0.5:8080/admin")
        assertEquals("100.64.0.5", h1)
        assertEquals(8080, p1)

        val (h2, p2) = MeshResolution.parseTarget("https://node-alpha.fleet.example/x")
        assertEquals("node-alpha.fleet.example", h2)
        assertEquals(443, p2)

        val (h3, p3) = MeshResolution.parseTarget("node-alpha")
        assertEquals("node-alpha", h3)
        assertEquals(80, p3)
    }

    // --- CGNAT math ---

    @Test
    fun cgnat_prefix_math() {
        val inside = MeshResolution.parseIpv4("100.64.0.0")!!
        assertTrue(MeshResolution.isMeshIpv4(inside))
        val last = MeshResolution.parseIpv4("100.127.255.255")!!
        assertTrue(MeshResolution.isMeshIpv4(last))
        val outsideLo = MeshResolution.parseIpv4("100.63.255.255")!!
        assertFalse(MeshResolution.isMeshIpv4(outsideLo))
        val outsideHi = MeshResolution.parseIpv4("100.128.0.0")!!
        assertFalse(MeshResolution.isMeshIpv4(outsideHi))
    }

    // --- decide ---

    private fun snap(
        kind: HostKind,
        host: String? = "100.64.0.1",
        tcp: Boolean = false,
        iface: Boolean = false,
        addr: Boolean = false,
        dns: Boolean? = null,
        vpn: Boolean = false,
    ) = MeshProbeSnapshot(
        meshInterfacePresent = iface,
        meshAddressPresent = addr,
        tcpReachable = tcp,
        hostKind = kind,
        resolvedHost = host,
        dnsResolved = dns,
        vpnTransportActive = vpn,
    )

    @Test
    fun decide_open_when_tcp_ok() {
        val d = MeshResolution.decide(
            "http://100.64.0.1:8080/",
            reachOverMesh = true,
            snapshot = snap(HostKind.MESH_IPV4, tcp = true, iface = true),
        )
        assertTrue(d is MeshOpenDecision.Open)
        assertEquals("http://100.64.0.1:8080/", (d as MeshOpenDecision.Open).url)
    }

    @Test
    fun decide_tunnel_down_when_no_signals_and_tcp_fail() {
        val d = MeshResolution.decide(
            "http://node-alpha.fleet.example/",
            reachOverMesh = true,
            snapshot = snap(HostKind.MAGIC_DNS, host = "node-alpha.fleet.example", tcp = false),
        )
        assertTrue(d is MeshOpenDecision.Blocked)
        val b = d as MeshOpenDecision.Blocked
        assertEquals(MeshBlockReason.TUNNEL_DOWN, b.reason)
        assertFalse(b.tunnelLikelyUp)
        assertTrue(b.message.contains("Victorious Mesh", ignoreCase = true))
    }

    @Test
    fun decide_unreachable_when_tunnel_signals_but_tcp_fail() {
        val d = MeshResolution.decide(
            "http://100.64.1.2:3790/",
            reachOverMesh = true,
            snapshot = snap(HostKind.MESH_IPV4, host = "100.64.1.2", tcp = false, addr = true),
        )
        assertTrue(d is MeshOpenDecision.Blocked)
        val b = d as MeshOpenDecision.Blocked
        assertEquals(MeshBlockReason.UNREACHABLE, b.reason)
        assertTrue(b.tunnelLikelyUp)
    }

    @Test
    fun decide_unreachable_when_iface_present_but_tcp_fail() {
        val d = MeshResolution.decide(
            "http://hs-admin/",
            reachOverMesh = true,
            snapshot = snap(HostKind.MESH_NODE_NAME, host = "hs-admin", tcp = false, iface = true),
        )
        val b = d as MeshOpenDecision.Blocked
        assertEquals(MeshBlockReason.UNREACHABLE, b.reason)
    }

    @Test
    fun decide_unknown_host() {
        val d = MeshResolution.decide(
            "not-a-url",
            reachOverMesh = true,
            snapshot = snap(HostKind.UNKNOWN, host = null, tcp = false),
        )
        val b = d as MeshOpenDecision.Blocked
        assertEquals(MeshBlockReason.UNKNOWN_HOST, b.reason)
    }

    @Test
    fun decide_not_mesh_panel() {
        val d = MeshResolution.decide(
            "http://127.0.0.1:8080/",
            reachOverMesh = false,
            snapshot = snap(HostKind.LOOPBACK, host = "127.0.0.1", tcp = true),
        )
        val b = d as MeshOpenDecision.Blocked
        assertEquals(MeshBlockReason.NOT_MESH_PANEL, b.reason)
    }

    @Test
    fun expand_ipv6_handles_compression() {
        val parts = MeshResolution.expandIpv6("fd7a:115c:a1e0::1")!!
        assertEquals(8, parts.size)
        assertEquals(0xfd7a, parts[0])
        assertEquals(0x115c, parts[1])
        assertEquals(0xa1e0, parts[2])
        assertEquals(1, parts[7])
    }

    // --- DNS / soft-signal mapping ---

    @Test
    fun decide_unknown_host_when_name_fails_but_tunnel_up() {
        val d = MeshResolution.decide(
            "http://no-such-node.fleet.example/",
            reachOverMesh = true,
            snapshot = snap(HostKind.MAGIC_DNS, host = "no-such-node.fleet.example", dns = false, addr = true),
        )
        val b = d as MeshOpenDecision.Blocked
        assertEquals(MeshBlockReason.UNKNOWN_HOST, b.reason)
        assertTrue(b.tunnelLikelyUp)
    }

    @Test
    fun decide_tunnel_down_when_name_fails_and_no_signals() {
        val d = MeshResolution.decide(
            "http://node-alpha/",
            reachOverMesh = true,
            snapshot = snap(HostKind.MESH_NODE_NAME, host = "node-alpha", dns = false),
        )
        val b = d as MeshOpenDecision.Blocked
        assertEquals(MeshBlockReason.TUNNEL_DOWN, b.reason)
        assertTrue(b.message.startsWith("Victorious Mesh tunnel appears down"))
    }

    @Test
    fun decide_tunnel_down_resolves_but_no_route() {
        val d = MeshResolution.decide(
            "http://node-alpha.fleet.example/",
            reachOverMesh = true,
            snapshot = snap(HostKind.MAGIC_DNS, host = "node-alpha.fleet.example", dns = true),
        )
        val b = d as MeshOpenDecision.Blocked
        assertEquals(MeshBlockReason.TUNNEL_DOWN, b.reason)
        assertTrue(b.message.contains("resolves but has no route"))
    }

    @Test
    fun decide_vpn_transport_counts_as_soft_tunnel_signal() {
        val d = MeshResolution.decide(
            "http://100.64.0.9:80/",
            reachOverMesh = true,
            snapshot = snap(HostKind.MESH_IPV4, host = "100.64.0.9", vpn = true),
        )
        val b = d as MeshOpenDecision.Blocked
        assertEquals(MeshBlockReason.UNREACHABLE, b.reason)
        assertTrue(b.tunnelLikelyUp)
    }

    @Test
    fun decide_open_even_without_tunnel_signals_when_tcp_ok() {
        // Probe success is authoritative; heuristics may miss a renamed interface.
        val d = MeshResolution.decide(
            "http://100.64.0.1/",
            reachOverMesh = true,
            snapshot = snap(HostKind.MESH_IPV4, tcp = true),
        )
        assertTrue(d is MeshOpenDecision.Open)
    }

    @Test
    fun needsDnsLookup_mapping() {
        assertTrue(MeshResolution.needsDnsLookup(HostKind.MAGIC_DNS))
        assertTrue(MeshResolution.needsDnsLookup(HostKind.MESH_NODE_NAME))
        assertFalse(MeshResolution.needsDnsLookup(HostKind.MESH_IPV4))
        assertFalse(MeshResolution.needsDnsLookup(HostKind.MESH_IPV6))
        assertFalse(MeshResolution.needsDnsLookup(HostKind.LOOPBACK))
        assertFalse(MeshResolution.needsDnsLookup(HostKind.UNKNOWN))
    }

    @Test
    fun parseTarget_ipv6_literal_and_bad_input() {
        val (h, p) = MeshResolution.parseTarget("http://[fd7a:115c:a1e0::5]:8443/")
        assertEquals(HostKind.MESH_IPV6, MeshResolution.classifyHost(h))
        assertEquals(8443, p)
        val (bad, _) = MeshResolution.parseTarget("http://bad host/")
        assertEquals(HostKind.UNKNOWN, MeshResolution.classifyHost(bad))
    }
}

