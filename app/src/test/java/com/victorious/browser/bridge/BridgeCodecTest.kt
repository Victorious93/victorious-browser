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
        assertEquals("requires_confirm", BridgeErrorCategory.REQUIRES_CONFIRM.wire)
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
    }

    @Test
    fun pinnedControllerCertFingerprintsExact() {
        val debug =
            "88:00:51:A6:D9:5D:38:DD:8D:81:E1:93:1E:17:05:48:03:87:D5:93:C3:09:43:53:99:68:E6:EF:11:A0:C8:4D"
        val release =
            "9E:A7:B3:14:94:5B:0A:62:97:15:6F:F7:06:E7:0C:0A:B1:B7:24:DE:06:D5:09:9C:A5:12:8A:B4:43:F7:2D:8D"
        assertEquals(debug, ControllerAllowlist.DEBUG_CERT_SHA256)
        assertEquals(release, ControllerAllowlist.RELEASE_CERT_SHA256)
        // Format-matched to CallerVerifier (%02X colon) + normalize (uppercase, keep colons).
        assertTrue(ControllerAllowlist.isAllowedCert(debug))
        assertTrue(ControllerAllowlist.isAllowedCert(release.lowercase()))
        assertTrue(ControllerAllowlist.PINNED_CERTS.contains(ControllerAllowlist.normalize(debug)))
        assertEquals(2, ControllerAllowlist.PINNED_CERTS.size)
    }
}
