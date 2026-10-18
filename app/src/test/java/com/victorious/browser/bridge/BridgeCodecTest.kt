package com.victorious.browser.bridge

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeCodecTest {

    @Test
    fun roundTripOkResponse() {
        val result = JSONObject().put("status", "opened")
        val encoded = BridgeCodec.encodeResponse(BridgeCodec.ok("abc", result))
        val parsed = JSONObject(encoded)
        assertEquals("abc", parsed.getString("id"))
        assertTrue(parsed.getBoolean("ok"))
        assertEquals("opened", parsed.getJSONObject("result").getString("status"))
        assertFalse(parsed.has("error"))
    }

    @Test
    fun roundTripErrorResponse() {
        val encoded = BridgeCodec.encodeResponse(
            BridgeCodec.err("x", BridgeErrorCategory.NOT_PAIRED, "external control is off"),
        )
        val parsed = JSONObject(encoded)
        assertEquals("x", parsed.getString("id"))
        assertFalse(parsed.getBoolean("ok"))
        assertEquals("not_paired", parsed.getJSONObject("error").getString("category"))
        assertEquals("external control is off", parsed.getJSONObject("error").getString("message"))
    }

    @Test
    fun parseRequestLine() {
        val req = BridgeCodec.parseRequestLine(
            """{"id":"1","op":"browser.open_url","params":{"url":"http://127.0.0.1/"}}""",
        )
        assertEquals("1", req.id)
        assertEquals("browser.open_url", req.op)
        assertEquals("http://127.0.0.1/", req.params.getString("url"))
    }

    @Test
    fun parameterlessOpWithNonEmptyParams_isInvalidParameters() {
        for (op in BridgeProtocol.PARAMETERLESS_OPS) {
            val req = BridgeRequest(
                id = "t-$op",
                op = op,
                params = JSONObject().put("extra", true),
            )
            val err = BridgeCodec.requireEmptyParams(req)
            assertNotNull("expected invalid_parameters for $op", err)
            assertEquals(BridgeErrorCategory.INVALID_PARAMETERS, err!!.error!!.category)
            assertFalse(err.ok)
        }
    }

    @Test
    fun parameterlessOpWithEmptyParams_ok() {
        for (op in BridgeProtocol.PARAMETERLESS_OPS) {
            val req = BridgeRequest(id = "ok-$op", op = op, params = JSONObject())
            assertNull(BridgeCodec.requireEmptyParams(req))
        }
    }

    @Test
    fun errorCategoriesWireNames() {
        assertEquals("invalid_parameters", BridgeErrorCategory.INVALID_PARAMETERS.wire)
        assertEquals("not_paired", BridgeErrorCategory.NOT_PAIRED.wire)
        assertEquals("not_found", BridgeErrorCategory.NOT_FOUND.wire)
        assertEquals("proxy_error", BridgeErrorCategory.PROXY_ERROR.wire)
        assertEquals("internal", BridgeErrorCategory.INTERNAL.wire)
    }

    @Test
    fun nodeIdRegex() {
        assertTrue(BridgeSettings.isValidNodeId("browser-" + "a".repeat(32)))
        assertTrue(BridgeSettings.isValidNodeId("browser-" + "0123456789abcdef".repeat(2)))
        assertFalse(BridgeSettings.isValidNodeId("browser-short"))
        assertFalse(BridgeSettings.isValidNodeId("node-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
    }

    @Test
    fun allowlistHasNoDebugPackage() {
        assertEquals("ai.droidcommand.app", ControllerAllowlist.CONTROLLER_PACKAGE)
        assertFalse(ControllerAllowlist.isAllowedPackage("ai.droidcommand.app.debug"))
        assertTrue(ControllerAllowlist.DEBUG_CERT_SHA256.startsWith("88:00:51:A6"))
        assertTrue(ControllerAllowlist.DEBUG_CERT_SHA256.endsWith("C8:4D"))
        assertTrue(ControllerAllowlist.RELEASE_CERT_SHA256.startsWith("9E:A7"))
        assertTrue(ControllerAllowlist.RELEASE_CERT_SHA256.endsWith("2D:8D"))
    }
}
