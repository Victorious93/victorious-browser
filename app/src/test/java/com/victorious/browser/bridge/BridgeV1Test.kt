package com.victorious.browser.bridge

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class BridgeV1Test {

    @Before
    fun resetGate() {
        ConfigImportGate.reset()
    }

    private fun nonce(seed: Int = 1): String =
        "%032x".format(seed.toLong())

    private fun baseEnv(
        op: String,
        nonceSeed: Int = 1,
        ts: Long = 1_700_000_000_000L,
        ttl: Long? = BridgeV1.TTL_DEFAULT_MS,
        extra: JSONObject.() -> Unit = {},
    ): JSONObject {
        val o = JSONObject()
            .put("op", op)
            .put("requestId", "req-$nonceSeed")
            .put("nonce", nonce(nonceSeed))
            .put("timestampMs", ts)
            .put("params", JSONObject())
            .put("destructive", false)
            .put("trigger", JSONObject())
        if (ttl != null) o.put("ttlMs", ttl)
        o.extra()
        return o
    }

    // --- ttl / expiry ---

    @Test
    fun ttlDefaultsAndClamps() {
        assertEquals(BridgeV1.TTL_DEFAULT_MS, BridgeV1.clampTtl(null))
        assertEquals(60_000L, BridgeV1.clampTtl(60_000L))
        assertEquals(BridgeV1.TTL_MAX_MS, BridgeV1.clampTtl(999_999_999L))
        try {
            BridgeV1.clampTtl(0)
            fail("expected V1Invalid")
        } catch (_: V1Invalid) {
        }
        try {
            BridgeV1.clampTtl(-1)
            fail("expected V1Invalid")
        } catch (_: V1Invalid) {
        }
    }

    @Test
    fun expiryCheck() {
        val ts = 1000L
        val ttl = 100L
        assertFalse(BridgeV1.isExpired(1099, ts, ttl))
        assertFalse(BridgeV1.isExpired(1100, ts, ttl))
        assertTrue(BridgeV1.isExpired(1101, ts, ttl))
    }

    // --- nonce ---

    @Test
    fun nonceNormalizeAccepts128BitHex() {
        val n = "aabbccddeeff00112233445566778899"
        assertEquals(n, BridgeV1.normalizeNonce(n.uppercase()))
    }

    @Test
    fun nonceRejectsBadLengthOrChars() {
        try {
            BridgeV1.normalizeNonce("abcd")
            fail()
        } catch (_: V1Invalid) {
        }
        try {
            BridgeV1.normalizeNonce("z".repeat(32))
            fail()
        } catch (_: V1Invalid) {
        }
    }

    @Test
    fun nonceCacheRejectsReplay() {
        var now = 1_000L
        val cache = NonceCache { now }
        val n = nonce(7)
        assertTrue(cache.checkAndRemember(n, timestampMs = 900, ttlMs = 500))
        assertFalse(cache.checkAndRemember(n, timestampMs = 900, ttlMs = 500))
        // After expiry, purged — same nonce can be reused once expired.
        now = 1_500
        assertTrue(cache.checkAndRemember(n, timestampMs = 1_400, ttlMs = 200))
    }

    @Test
    fun nonceCacheRejectsAlreadyExpired() {
        val now = 5_000L
        val cache = NonceCache { now }
        assertFalse(cache.checkAndRemember(nonce(3), timestampMs = 1_000, ttlMs = 100))
    }

    // --- envelope parse ---

    @Test
    fun parseCapabilitiesGet() {
        val env = EnvelopeParser.parse(baseEnv(BridgeV1.OP_CAPABILITIES_GET))
        assertEquals(BridgeV1.OP_CAPABILITIES_GET, env.op)
        assertEquals("req-1", env.requestId)
        assertEquals(BridgeV1.TTL_DEFAULT_MS, env.ttlMs)
    }

    @Test
    fun parseRemoteCommandRequiresActionRef() {
        try {
            EnvelopeParser.parse(baseEnv(BridgeV1.OP_REMOTE_COMMAND))
            fail()
        } catch (e: V1Invalid) {
            assertTrue(e.message!!.contains("actionRef"))
        }
        val env = EnvelopeParser.parse(
            baseEnv(BridgeV1.OP_REMOTE_COMMAND) {
                put("actionRef", BridgeProtocol.OP_PROXY_STATUS)
            },
        )
        assertEquals(BridgeProtocol.OP_PROXY_STATUS, env.actionRef)
    }

    @Test
    fun parseRemoteCommandRejectsUnknownActionRef() {
        try {
            EnvelopeParser.parse(
                baseEnv(BridgeV1.OP_REMOTE_COMMAND) {
                    put("actionRef", "browser.nope")
                },
            )
            fail()
        } catch (_: V1Invalid) {
        }
    }

    @Test
    fun parseMacroStep() {
        val env = EnvelopeParser.parse(
            baseEnv(BridgeV1.OP_MACRO_STEP) {
                put("actionRef", BridgeProtocol.OP_OPEN_URL)
                put("macroId", "m1")
                put("stepIndex", 2)
                put("onError", "continue")
                put("params", JSONObject().put("url", "http://127.0.0.1/"))
            },
        )
        assertEquals("m1", env.macroId)
        assertEquals(2, env.stepIndex)
        assertEquals(OnErrorPolicy.CONTINUE, env.onError)
    }

    @Test
    fun parseMacroRunDryRunAndSteps() {
        val steps = JSONArray()
            .put(JSONObject().put("actionRef", BridgeProtocol.OP_CAPABILITIES_GET).put("params", JSONObject()))
            .put(JSONObject().put("actionRef", BridgeProtocol.OP_PROXY_STATUS))
        val env = EnvelopeParser.parse(
            baseEnv(BridgeV1.OP_MACRO_RUN, nonceSeed = 9) {
                put("macroId", "run-a")
                put("steps", steps)
                put("dryRun", true)
            },
        )
        assertTrue(env.dryRun)
        assertEquals(2, env.steps!!.size)
        assertEquals(BridgeProtocol.OP_PROXY_STATUS, env.steps!![1].actionRef)
    }

    @Test
    fun parseMacroRunRejectsTooManySteps() {
        val steps = JSONArray()
        repeat(BridgeV1.MAX_MACRO_STEPS + 1) {
            steps.put(JSONObject().put("actionRef", BridgeProtocol.OP_PROXY_STATUS))
        }
        try {
            EnvelopeParser.parse(
                baseEnv(BridgeV1.OP_MACRO_RUN) {
                    put("macroId", "big")
                    put("steps", steps)
                },
            )
            fail()
        } catch (_: V1Invalid) {
        }
    }

    @Test
    fun parseMissingFieldsInvalid() {
        for (field in listOf("op", "requestId", "nonce", "timestampMs")) {
            val o = baseEnv(BridgeV1.OP_NODE_STATUS_GET)
            o.remove(field)
            try {
                EnvelopeParser.parse(o)
                fail("expected invalid for missing $field")
            } catch (_: V1Invalid) {
            }
        }
    }

    @Test
    fun parseUnknownOp() {
        try {
            EnvelopeParser.parse(baseEnv("nope.op"))
            fail()
        } catch (_: V1Invalid) {
        }
    }

    @Test
    fun parseConfigImportDefaults() {
        val env = EnvelopeParser.parse(baseEnv(BridgeV1.OP_CONFIG_IMPORT))
        assertFalse(env.dryRun)
        assertEquals(ConflictPolicy.SKIP, env.conflictPolicy)
    }

    @Test
    fun parseConfigImportConflictPolicies() {
        for (p in listOf("skip", "overwrite", "merge-by-id")) {
            val env = EnvelopeParser.parse(
                baseEnv(BridgeV1.OP_CONFIG_IMPORT, nonceSeed = p.hashCode()) {
                    put("conflictPolicy", p)
                },
            )
            assertEquals(p, env.conflictPolicy!!.wire)
        }
        try {
            EnvelopeParser.parse(
                baseEnv(BridgeV1.OP_CONFIG_IMPORT) {
                    put("conflictPolicy", "explode")
                },
            )
            fail()
        } catch (_: V1Invalid) {
        }
    }

    @Test
    fun ttlOmittedUsesDefault() {
        val o = baseEnv(BridgeV1.OP_NODE_STATUS_GET, ttl = null)
        o.remove("ttlMs")
        val env = EnvelopeParser.parse(o)
        assertEquals(BridgeV1.TTL_DEFAULT_MS, env.ttlMs)
    }

    @Test
    fun ttlAboveMaxClamped() {
        val env = EnvelopeParser.parse(
            baseEnv(BridgeV1.OP_NODE_STATUS_GET, ttl = 999_999L),
        )
        assertEquals(BridgeV1.TTL_MAX_MS, env.ttlMs)
    }

    // --- capabilities registry ---

    @Test
    fun capabilitiesAdvertiseFrozenOps() {
        val ops = CapabilitiesRegistry.ADS.map { it.op }.toSet()
        assertEquals(BridgeV1.ALL_OPS.toSet(), ops)
        val status = CapabilitiesRegistry.meta(BridgeV1.OP_NODE_STATUS_GET)!!
        assertEquals("read", status.kind)
        assertTrue(status.remoteAllowed)
        assertFalse(status.requiresConfirm)
        val export = CapabilitiesRegistry.meta(BridgeV1.OP_CONFIG_EXPORT)!!
        assertEquals("action", export.kind)
        assertFalse(export.remoteAllowed)
        assertFalse(export.requiresConfirm)
        val import = CapabilitiesRegistry.meta(BridgeV1.OP_CONFIG_IMPORT)!!
        assertFalse(import.remoteAllowed)
        assertTrue(import.requiresConfirm)
        assertFalse(CapabilitiesRegistry.requiresExternalControl(BridgeV1.OP_CAPABILITIES_GET))
        assertFalse(CapabilitiesRegistry.requiresExternalControl(BridgeV1.OP_NODE_STATUS_GET))
        assertTrue(CapabilitiesRegistry.requiresExternalControl(BridgeV1.OP_REMOTE_COMMAND))
        assertTrue(CapabilitiesRegistry.requiresExternalControl(BridgeV1.OP_MACRO_RUN))
        assertTrue(CapabilitiesRegistry.requiresExternalControl(BridgeV1.OP_CONFIG_EXPORT))
    }

    @Test
    fun capabilitiesJsonShape() {
        val arr = CapabilitiesRegistry.toJson()
        assertEquals(7, arr.length())
        val first = arr.getJSONObject(0)
        assertTrue(first.has("op"))
        assertTrue(first.has("kind"))
        assertTrue(first.has("remoteAllowed"))
        assertTrue(first.has("requiresConfirm"))
    }

    // --- node status ---

    @Test
    fun nodeStatusSnapshotShape() {
        val snap = NodeStatusBuilder.snapshot(
            nodeId = "browser-" + "ab".repeat(16),
            packageName = "com.victorious.browser",
            connectionClass = "EXTERNAL_NODE",
            updatedAtMs = 42L,
            domains = listOf(
                StatusDomain("browser", "idle", "webview ready"),
                StatusDomain("node_health", "ok", "bridge major=1"),
            ),
        )
        assertEquals("EXTERNAL_NODE", snap.getString("connectionClass"))
        assertEquals(2, snap.getJSONArray("domains").length())
        assertEquals("browser", snap.getJSONArray("domains").getJSONObject(0).getString("name"))
        assertEquals("node_health", snap.getJSONArray("domains").getJSONObject(1).getString("name"))
    }

    @Test
    fun sanitizeDetailStripsUrlsAndSecrets() {
        assertNull(NodeStatusBuilder.sanitizeDetail("see https://evil.example/x"))
        assertNull(NodeStatusBuilder.sanitizeDetail("cookie=abc"))
        assertNull(NodeStatusBuilder.sanitizeDetail("user@host"))
        assertEquals("ok", NodeStatusBuilder.sanitizeDetail("ok"))
        val long = "x".repeat(500)
        assertEquals(200, NodeStatusBuilder.sanitizeDetail(long)!!.length)
    }

    // --- config backup ---

    @Test
    fun refuseSecretExportRequests() {
        assertNotNull(ConfigBackup.refuseIfSecretRequested(listOf("cookies")))
        assertNotNull(ConfigBackup.refuseIfSecretRequested(listOf("msf_rpc_config")))
        assertNotNull(ConfigBackup.refuseIfSecretRequested(listOf("passwords")))
        assertNotNull(ConfigBackup.refuseIfSecretRequested(listOf("certs")))
        assertNull(ConfigBackup.refuseIfSecretRequested(listOf("fleet_bridge", "browser_night_mode")))
    }

    @Test
    fun stripSecretsDropsSecretPrefsAndKeys() {
        assertNull(ConfigBackup.stripSecrets("msf_rpc_config", mapOf("host" to "x")))
        val stripped = ConfigBackup.stripSecrets(
            "fleet_bridge",
            mapOf(
                "node_id" to "browser-aa",
                "external_control_enabled" to false,
                "api_key" to "SECRET",
                "bearer_token" to "t",
            ),
        )!!
        assertTrue("node_id" in stripped)
        assertFalse("api_key" in stripped)
        assertFalse("bearer_token" in stripped)
    }

    @Test
    fun conflictSkipOverwriteMerge() {
        val existing = mapOf("a" to 1, "b" to 2)
        val incoming = mapOf("b" to 9, "c" to 3)
        assertEquals(
            mapOf("a" to 1, "b" to 2, "c" to 3),
            ConfigBackup.resolveConflict(existing, incoming, ConflictPolicy.SKIP),
        )
        assertEquals(
            mapOf("a" to 1, "b" to 9, "c" to 3),
            ConfigBackup.resolveConflict(existing, incoming, ConflictPolicy.OVERWRITE),
        )
        assertEquals(
            mapOf("a" to 1, "b" to 9, "c" to 3),
            ConfigBackup.resolveConflict(existing, incoming, ConflictPolicy.MERGE_BY_ID),
        )
    }

    @Test
    fun importNeverEnablesExternalControl() {
        val existing = mapOf(ConfigBackup.EXTERNAL_CONTROL_KEY to false)
        val incoming = mapOf(ConfigBackup.EXTERNAL_CONTROL_KEY to true, "node_id" to "x")
        for (p in ConflictPolicy.entries) {
            val out = ConfigBackup.resolveConflict(existing, incoming, p)
            assertEquals(false, out[ConfigBackup.EXTERNAL_CONTROL_KEY])
        }
    }

    @Test
    fun exportEntryPreferencesOnly() {
        val entry = ConfigBackup.exportEntry(
            ConfigBackup.PACKAGE,
            mapOf(
                "fleet_bridge" to mapOf("node_id" to "browser-x", "api_key" to "nope"),
                "msf_rpc_config" to mapOf("pass" to "secret"),
            ),
        )
        assertEquals("preferences", entry.getString("kind"))
        assertEquals(ConfigBackup.PACKAGE, entry.getString("packageName"))
        val prefs = entry.getJSONArray("preferences")
        assertEquals(1, prefs.length())
        val entries = prefs.getJSONObject(0).getJSONObject("entries")
        assertTrue(entries.has("node_id"))
        assertFalse(entries.has("api_key"))
    }

    @Test
    fun exportWrongPackageRejected() {
        try {
            ConfigBackup.exportEntry("com.other", emptyMap())
            fail()
        } catch (_: V1Invalid) {
        }
    }

    // --- import gate ---

    @Test
    fun configImportGateApproveConsume() {
        ConfigImportGate.submitPending("r1", ConflictPolicy.SKIP, "preferences entries=1")
        assertNotNull(ConfigImportGate.pending.value)
        assertFalse(ConfigImportGate.consumeApproval("r1"))
        ConfigImportGate.approve("r1")
        assertTrue(ConfigImportGate.consumeApproval("r1"))
        assertFalse(ConfigImportGate.consumeApproval("r1")) // one-shot
    }

    // --- protocol constants ---

    @Test
    fun protocolMinorIsOne() {
        assertEquals(1, BridgeProtocol.BRIDGE_PROTOCOL_MAJOR)
        assertEquals(1, BridgeProtocol.BRIDGE_PROTOCOL_MINOR)
        assertEquals(1, BridgeV1.PROTOCOL_MINOR)
    }

    @Test
    fun actionRefsMatchLegacyOps() {
        assertEquals(BridgeProtocol.ALL_OPERATIONS.toSet(), BridgeV1.ACTION_REFS)
    }

    @Test
    fun isV1ObjectDetection() {
        assertTrue(BridgeV1.isV1Object(JSONObject().put("requestId", "x")))
        assertFalse(BridgeV1.isV1Object(JSONObject().put("id", "x").put("op", "browser.open_url")))
    }

    @Test
    fun requiresConfirmCategoryWire() {
        assertEquals("requires_confirm", BridgeErrorCategory.REQUIRES_CONFIRM.wire)
    }

    @Test
    fun certPinsUnchangedSourceOfTruth() {
        // Reuse ControllerAllowlist constants — do not invent digits.
        assertEquals(
            "88:00:51:A6:D9:5D:38:DD:8D:81:E1:93:1E:17:05:48:03:87:D5:93:C3:09:43:53:99:68:E6:EF:11:A0:C8:4D",
            ControllerAllowlist.DEBUG_CERT_SHA256,
        )
        assertEquals(
            "9E:A7:B3:14:94:5B:0A:62:97:15:6F:F7:06:E7:0C:0A:B1:B7:24:DE:06:D5:09:9C:A5:12:8A:B4:43:F7:2D:8D",
            ControllerAllowlist.RELEASE_CERT_SHA256,
        )
        assertFalse(ControllerAllowlist.isAllowedPackage("ai.droidcommand.app.debug"))
    }
}
