package com.victorious.browser.headscale

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These exercise only the pure JSON (de)serialization in [HeadscaleApiClient],
 * against hand-written bodies shaped like Headscale's published API docs — not a
 * captured real response (see the object's doc for why: Headscale is self-hosted
 * and there was no server available to test against in this session).
 */
class HeadscaleApiClientTest {
    @Test
    fun parsesNodesFromDocumentedShape() {
        val body = """
            {"nodes":[
              {"id":"1","name":"laptop","user":{"name":"alice"},"ipAddresses":["100.64.0.1"],"online":true,"lastSeen":"2026-01-01T00:00:00Z"},
              {"id":"2","name":"phone","user":{"name":"bob"},"ipAddresses":["100.64.0.2"],"online":false,"expiry":"2026-02-01T00:00:00Z"}
            ]}
        """.trimIndent()
        val nodes = HeadscaleApiClient.parseNodesJson(body)
        assertEquals(2, nodes.size)
        assertEquals("laptop", nodes[0].name)
        assertEquals("alice", nodes[0].userName)
        assertEquals(listOf("100.64.0.1"), nodes[0].ipAddresses)
        assertEquals(true, nodes[0].online)
        assertEquals(false, nodes[1].online)
        assertEquals("2026-02-01T00:00:00Z", nodes[1].expiry)
    }

    @Test
    fun parsesEmptyNodesList() {
        assertTrue(HeadscaleApiClient.parseNodesJson("""{"nodes":[]}""").isEmpty())
    }

    @Test
    fun parsesPreAuthKeysFromDocumentedShape() {
        val body = """
            {"preAuthKeys":[
              {"id":"1","key":"abc123","reusable":true,"ephemeral":false,"used":false,"expiration":"2026-03-01T00:00:00Z"}
            ]}
        """.trimIndent()
        val keys = HeadscaleApiClient.parsePreAuthKeysJson(body)
        assertEquals(1, keys.size)
        assertEquals("abc123", keys[0].key)
        assertEquals(true, keys[0].reusable)
        assertEquals(false, keys[0].used)
    }

    @Test
    fun buildsCreatePreAuthKeyBodyWithExpiration() {
        val body = HeadscaleApiClient.buildCreatePreAuthKeyBody("alice", reusable = true, ephemeral = false, expirationIso = "2026-03-01T00:00:00Z")
        val obj = JSONObject(body)
        assertEquals("alice", obj.getString("user"))
        assertEquals(true, obj.getBoolean("reusable"))
        assertEquals(false, obj.getBoolean("ephemeral"))
        assertEquals("2026-03-01T00:00:00Z", obj.getString("expiration"))
    }

    @Test
    fun buildsCreatePreAuthKeyBodyWithoutExpirationWhenNull() {
        val body = HeadscaleApiClient.buildCreatePreAuthKeyBody("alice", reusable = false, ephemeral = true, expirationIso = null)
        assertTrue(!JSONObject(body).has("expiration"))
    }
}
