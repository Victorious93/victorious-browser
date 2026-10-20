package com.victorious.browser.panels

import com.victorious.browser.bridge.ConfigBackup
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MeshBridgeProtocolTest {
    private val node = "mesh-3f9a1c0b7d2e4f6a8b1c2d3e4f5a6b7c"

    @Test
    fun only_mesh_status_and_mesh_ip_allowed() {
        assertEquals(setOf("mesh.status", "mesh.ip.get"), MeshBridgeProtocol.ALLOWED_OPS)
        for (op in listOf("node.status.get", "mesh.up", "mesh.down", "mesh.capabilities.get")) {
            try {
                MeshBridgeProtocol.buildLegacyRequest(op, "r", null); fail("$op allowed")
            } catch (_: IllegalArgumentException) {}
            try {
                MeshBridgeProtocol.buildEnvelope(op, "r", node, "0123456789abcdef0123456789abcdef", 1L); fail("$op allowed")
            } catch (_: IllegalArgumentException) {}
        }
    }

    @Test
    fun legacy_request_shape() {
        val o = MeshBridgeProtocol.buildLegacyRequest("mesh.status", "vb-1", node)
        assertEquals(1, o.getInt("protocol_major"))
        assertEquals("mesh.status", o.getString("operation"))
        assertEquals(node, o.getString("target_node_id"))
        assertEquals(0, o.getJSONObject("parameters").length())
        assertFalse(MeshBridgeProtocol.buildLegacyRequest("mesh.status", "vb-1", "bad").has("target_node_id"))
    }

    @Test
    fun envelope_shape_and_ttl_clamp() {
        val e = MeshBridgeProtocol.buildEnvelope("mesh.ip.get", "vb-2", node,
            "01234567-89ab-cdef-0123-456789ABCDEF", 1000L, ttlMs = 999_999L)
        assertEquals("remote.command", e.getString("op"))
        assertEquals("mesh.ip.get", e.getString("actionRef"))
        assertEquals(300_000L, e.getLong("ttlMs"))
        assertEquals("0123456789abcdef0123456789abcdef", e.getString("nonce"))
        assertFalse(e.getBoolean("destructive"))
        assertEquals(120_000L, MeshBridgeProtocol.clampTtl(null))
        assertEquals(120_000L, MeshBridgeProtocol.clampTtl(0))
        assertEquals(5_000L, MeshBridgeProtocol.clampTtl(5_000))
        try { MeshBridgeProtocol.buildEnvelope("mesh.status", "r", "mesh-xyz", "0123456789abcdef0123456789abcdef", 1L); fail() } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun parse_status_success() {
        val raw = """{"protocol_major":1,"protocol_minor":1,"request_id":"r","node_id":"$node","ok":true,
            "result":{"backend_state":"Running","connected":true,"control_url":"https://x","ips":["100.101.102.103","fd7a:115c:a1e0::1"]},"error":null}"""
        val r = MeshBridgeProtocol.parseResponse(raw) as BridgeReply.Success
        assertEquals(node, r.nodeId)
        val s = MeshBridgeProtocol.parseStatus(r.result)
        assertEquals("Running", s.backendState)
        assertTrue(s.connected)
        assertEquals(2, s.ipCount)
        assertFalse(s.toString().contains("100.101"))
    }

    @Test
    fun parse_status_down_and_ip() {
        val s = MeshBridgeProtocol.parseStatus(JSONObject("""{"backend_state":"Stopped","connected":false,"ips":[]}"""))
        assertFalse(s.connected)
        val ip = MeshBridgeProtocol.parseIp(JSONObject("""{"ipv4":"100.64.0.7","ipv6":null}"""))
        assertEquals("100.64.0.7", ip.ipv4)
        assertNull(ip.ipv6)
        assertFalse(ip.toString().contains("100.64"))
    }

    @Test
    fun parse_unauthorized_and_errors_degrade() {
        val u = MeshBridgeProtocol.parseResponse("""{"ok":false,"error":"unauthorized"}""") as BridgeReply.Failure
        assertEquals(BridgeErrorKind.NOT_AUTHORIZED, u.kind)
        val u2 = MeshBridgeProtocol.parseResponse("""{"ok":false,"error":{"category":"unauthorized_caller"}}""") as BridgeReply.Failure
        assertEquals(BridgeErrorKind.NOT_AUTHORIZED, u2.kind)
        val d = MeshBridgeProtocol.parseResponse("""{"ok":false,"node_id":"$node","error":"external_control_disabled"}""") as BridgeReply.Failure
        assertEquals(BridgeErrorKind.DISABLED, d.kind)
        assertEquals(node, d.nodeId)
        assertEquals(BridgeErrorKind.MALFORMED, (MeshBridgeProtocol.parseResponse("nope") as BridgeReply.Failure).kind)
        assertEquals(BridgeErrorKind.MALFORMED, (MeshBridgeProtocol.parseResponse(null) as BridgeReply.Failure).kind)
        assertEquals(BridgeErrorKind.PROTOCOL, MeshBridgeProtocol.classify("expired"))
        assertTrue(MeshBridgeState.Unavailable(BridgeErrorKind.NOT_AUTHORIZED).label().contains("not authorized yet"))
        assertTrue(MeshBridgeState.Unavailable(BridgeErrorKind.TIMEOUT).label().contains("unavailable"))
    }

    @Test
    fun mesh_ip_never_in_config_export() {
        val prefs = mapOf(
            "mesh_config" to mapOf<String, Any?>(
                "ranges" to "100.64.0.0/10", "base_domain" to "fleet.example", "bridge_enabled" to true,
                "mesh_ip" to "100.64.0.7", "last_Mesh_IPv4" to "100.64.0.7", "meship6" to "fd7a::1",
            ),
        )
        val out = ConfigBackup.exportEntry(ConfigBackup.PACKAGE, prefs).toString()
        assertFalse(out.contains("100.64.0.7"))
        assertFalse(out.contains("fd7a::1"))
        assertTrue(out.contains("base_domain"))
        assertTrue(ConfigBackup.refuseIfSecretRequested(listOf("mesh_ip")) != null)
    }
}
