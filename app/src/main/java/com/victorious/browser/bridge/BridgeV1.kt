package com.victorious.browser.bridge

import org.json.JSONArray
import org.json.JSONObject

/**
 * FROZEN remote-control envelope v1 (BRIDGE_PROTOCOL_MAJOR=1 / MINOR=1).
 *
 * Pure (Android-free) logic: envelope parse/validate, ttl clamp + expiry,
 * nonce replay cache, capabilities registry, remote gate, macro validation,
 * node status snapshot shape, config export secret-exclusion and import
 * conflict resolution. Caller identity is NOT decided here — that stays in
 * [CallerVerifier] + [ControllerAllowlist] and runs before any of this.
 */
object BridgeV1 {
    const val PROTOCOL_MINOR = 1

    const val OP_CAPABILITIES_GET = "capabilities.get"
    const val OP_REMOTE_COMMAND = "remote.command"
    const val OP_MACRO_STEP = "macro.step"
    const val OP_MACRO_RUN = "macro.run"
    const val OP_NODE_STATUS_GET = "node.status.get"
    const val OP_CONFIG_EXPORT = "config.export"
    const val OP_CONFIG_IMPORT = "config.import"

    val ALL_OPS: List<String> = listOf(
        OP_CAPABILITIES_GET,
        OP_REMOTE_COMMAND,
        OP_MACRO_STEP,
        OP_MACRO_RUN,
        OP_NODE_STATUS_GET,
        OP_CONFIG_EXPORT,
        OP_CONFIG_IMPORT,
    )

    const val TTL_DEFAULT_MS = 120_000L
    const val TTL_MAX_MS = 300_000L
    const val MAX_MACRO_STEPS = 64
    const val MAX_DETAIL_CHARS = 200
    const val NONCE_HEX_LEN = 32 // 128-bit hex

    /** actionRef targets for remote.command / macro.* — existing browser.* ops. */
    val ACTION_REFS: Set<String> = BridgeProtocol.ALL_OPERATIONS.toSet()

    /** True iff the raw JSON object looks like a v1 envelope (has requestId). */
    fun isV1Object(o: JSONObject): Boolean = o.has("requestId")

    fun clampTtl(raw: Long?): Long {
        val v = raw ?: TTL_DEFAULT_MS
        if (v <= 0L) throw V1Invalid("ttlMs must be positive")
        return minOf(v, TTL_MAX_MS)
    }

    fun isExpired(nowMs: Long, timestampMs: Long, ttlMs: Long): Boolean =
        nowMs > timestampMs + ttlMs

    fun normalizeNonce(raw: String): String {
        val n = raw.trim().lowercase()
        if (n.length != NONCE_HEX_LEN || !n.all { it in '0'..'9' || it in 'a'..'f' }) {
            throw V1Invalid("nonce must be 128-bit hex ($NONCE_HEX_LEN chars)")
        }
        return n
    }
}

class V1Invalid(msg: String) : Exception(msg)

enum class OnErrorPolicy(val wire: String) {
    ABORT("abort"),
    CONTINUE("continue");

    companion object {
        fun parse(raw: String?): OnErrorPolicy {
            if (raw == null || raw.isBlank()) return ABORT
            return entries.firstOrNull { it.wire == raw }
                ?: throw V1Invalid("onError must be abort|continue")
        }
    }
}

enum class ConflictPolicy(val wire: String) {
    SKIP("skip"),
    OVERWRITE("overwrite"),
    MERGE_BY_ID("merge-by-id");

    companion object {
        fun parse(raw: String?): ConflictPolicy {
            if (raw == null || raw.isBlank()) return SKIP
            return entries.firstOrNull { it.wire == raw }
                ?: throw V1Invalid("conflictPolicy must be skip|overwrite|merge-by-id")
        }
    }
}

data class CapMeta(
    val op: String,
    val kind: String,
    val remoteAllowed: Boolean,
    val requiresConfirm: Boolean,
)

object CapabilitiesRegistry {
    val ADS: List<CapMeta> = listOf(
        CapMeta(BridgeV1.OP_CAPABILITIES_GET, "read", remoteAllowed = true, requiresConfirm = false),
        CapMeta(BridgeV1.OP_REMOTE_COMMAND, "action", remoteAllowed = false, requiresConfirm = false),
        CapMeta(BridgeV1.OP_MACRO_STEP, "action", remoteAllowed = false, requiresConfirm = false),
        CapMeta(BridgeV1.OP_MACRO_RUN, "action", remoteAllowed = false, requiresConfirm = false),
        CapMeta(BridgeV1.OP_NODE_STATUS_GET, "read", remoteAllowed = true, requiresConfirm = false),
        CapMeta(BridgeV1.OP_CONFIG_EXPORT, "action", remoteAllowed = false, requiresConfirm = false),
        CapMeta(BridgeV1.OP_CONFIG_IMPORT, "action", remoteAllowed = false, requiresConfirm = true),
    )

    fun meta(op: String): CapMeta? = ADS.firstOrNull { it.op == op }

    fun requiresExternalControl(op: String): Boolean {
        val m = meta(op) ?: return true
        return !m.remoteAllowed
    }

    fun toJson(): JSONArray {
        val arr = JSONArray()
        ADS.forEach { c ->
            arr.put(
                JSONObject()
                    .put("op", c.op)
                    .put("kind", c.kind)
                    .put("remoteAllowed", c.remoteAllowed)
                    .put("requiresConfirm", c.requiresConfirm),
            )
        }
        return arr
    }
}

data class MacroStepSpec(val actionRef: String, val params: JSONObject)

data class EnvelopeV1(
    val op: String,
    val requestId: String,
    val targetNodeRef: String?,
    val actionRef: String?,
    val params: JSONObject,
    val nonce: String,
    val timestampMs: Long,
    val ttlMs: Long,
    val destructive: Boolean,
    val trigger: JSONObject,
    val macroId: String? = null,
    val stepIndex: Int? = null,
    val onError: OnErrorPolicy? = null,
    val steps: List<MacroStepSpec>? = null,
    val dryRun: Boolean = false,
    val conflictPolicy: ConflictPolicy? = null,
)

object EnvelopeParser {
    fun parse(line: String): EnvelopeV1 = parse(JSONObject(line.trim()))

    fun parse(o: JSONObject): EnvelopeV1 {
        val op = o.optString("op", "").trim()
        if (op !in BridgeV1.ALL_OPS) throw V1Invalid("unknown or missing op")

        val requestId = o.optString("requestId", "").trim()
        if (requestId.isEmpty()) throw V1Invalid("requestId required")

        if (!o.has("nonce")) throw V1Invalid("nonce required")
        val nonce = BridgeV1.normalizeNonce(o.getString("nonce"))

        if (!o.has("timestampMs") || o.get("timestampMs") !is Number) {
            throw V1Invalid("timestampMs required")
        }
        val timestampMs = o.getLong("timestampMs")

        val ttlRaw = if (o.has("ttlMs")) {
            if (o.get("ttlMs") !is Number) throw V1Invalid("ttlMs must be number")
            o.getLong("ttlMs")
        } else null
        val ttlMs = BridgeV1.clampTtl(ttlRaw)

        val params = o.optJSONObject("params") ?: JSONObject()
        val trigger = o.optJSONObject("trigger") ?: JSONObject()
        val destructive = if (o.has("destructive")) {
            if (o.get("destructive") !is Boolean) throw V1Invalid("destructive must be bool")
            o.getBoolean("destructive")
        } else false

        val targetNodeRef = o.optString("targetNodeRef", "").trim().ifEmpty { null }
        val actionRefRaw = o.optString("actionRef", "").trim().ifEmpty { null }

        return when (op) {
            BridgeV1.OP_CAPABILITIES_GET, BridgeV1.OP_NODE_STATUS_GET -> {
                EnvelopeV1(
                    op = op, requestId = requestId, targetNodeRef = targetNodeRef,
                    actionRef = null, params = params, nonce = nonce,
                    timestampMs = timestampMs, ttlMs = ttlMs, destructive = destructive,
                    trigger = trigger,
                )
            }
            BridgeV1.OP_REMOTE_COMMAND -> {
                val actionRef = actionRefRaw ?: throw V1Invalid("actionRef required")
                if (actionRef !in BridgeV1.ACTION_REFS) {
                    throw V1Invalid("unknown actionRef: $actionRef")
                }
                EnvelopeV1(
                    op = op, requestId = requestId, targetNodeRef = targetNodeRef,
                    actionRef = actionRef, params = params, nonce = nonce,
                    timestampMs = timestampMs, ttlMs = ttlMs, destructive = destructive,
                    trigger = trigger,
                )
            }
            BridgeV1.OP_MACRO_STEP -> {
                val actionRef = actionRefRaw ?: throw V1Invalid("actionRef required")
                if (actionRef !in BridgeV1.ACTION_REFS) {
                    throw V1Invalid("unknown actionRef: $actionRef")
                }
                val macroId = o.optString("macroId", "").trim()
                if (macroId.isEmpty()) throw V1Invalid("macroId required")
                if (!o.has("stepIndex") || o.get("stepIndex") !is Number) {
                    throw V1Invalid("stepIndex required")
                }
                val stepIndex = o.getInt("stepIndex")
                if (stepIndex < 0) throw V1Invalid("stepIndex must be >= 0")
                val onError = OnErrorPolicy.parse(o.optString("onError", "abort"))
                EnvelopeV1(
                    op = op, requestId = requestId, targetNodeRef = targetNodeRef,
                    actionRef = actionRef, params = params, nonce = nonce,
                    timestampMs = timestampMs, ttlMs = ttlMs, destructive = destructive,
                    trigger = trigger, macroId = macroId, stepIndex = stepIndex, onError = onError,
                )
            }
            BridgeV1.OP_MACRO_RUN -> {
                val macroId = o.optString("macroId", "").trim()
                if (macroId.isEmpty()) throw V1Invalid("macroId required")
                val stepsArr = o.optJSONArray("steps") ?: throw V1Invalid("steps required")
                if (stepsArr.length() > BridgeV1.MAX_MACRO_STEPS) {
                    throw V1Invalid("steps exceeds max ${BridgeV1.MAX_MACRO_STEPS}")
                }
                val steps = mutableListOf<MacroStepSpec>()
                for (i in 0 until stepsArr.length()) {
                    val s = stepsArr.optJSONObject(i) ?: throw V1Invalid("steps[$i] must be object")
                    val ar = s.optString("actionRef", "").trim()
                    if (ar.isEmpty() || ar !in BridgeV1.ACTION_REFS) {
                        throw V1Invalid("steps[$i].actionRef invalid")
                    }
                    steps.add(MacroStepSpec(ar, s.optJSONObject("params") ?: JSONObject()))
                }
                val dryRun = if (o.has("dryRun")) {
                    if (o.get("dryRun") !is Boolean) throw V1Invalid("dryRun must be bool")
                    o.getBoolean("dryRun")
                } else false
                val onError = OnErrorPolicy.parse(o.optString("onError", "abort"))
                EnvelopeV1(
                    op = op, requestId = requestId, targetNodeRef = targetNodeRef,
                    actionRef = null, params = params, nonce = nonce,
                    timestampMs = timestampMs, ttlMs = ttlMs, destructive = destructive,
                    trigger = trigger, macroId = macroId, steps = steps, dryRun = dryRun,
                    onError = onError,
                )
            }
            BridgeV1.OP_CONFIG_EXPORT -> {
                EnvelopeV1(
                    op = op, requestId = requestId, targetNodeRef = targetNodeRef,
                    actionRef = null, params = params, nonce = nonce,
                    timestampMs = timestampMs, ttlMs = ttlMs, destructive = destructive,
                    trigger = trigger,
                )
            }
            BridgeV1.OP_CONFIG_IMPORT -> {
                val dryRun = if (o.has("dryRun")) {
                    if (o.get("dryRun") !is Boolean) throw V1Invalid("dryRun must be bool")
                    o.getBoolean("dryRun")
                } else false
                val conflictPolicy = ConflictPolicy.parse(
                    if (o.has("conflictPolicy")) o.optString("conflictPolicy") else null,
                )
                EnvelopeV1(
                    op = op, requestId = requestId, targetNodeRef = targetNodeRef,
                    actionRef = null, params = params, nonce = nonce,
                    timestampMs = timestampMs, ttlMs = ttlMs, destructive = destructive,
                    trigger = trigger, dryRun = dryRun, conflictPolicy = conflictPolicy,
                )
            }
            else -> throw V1Invalid("unknown op")
        }
    }
}

/**
 * In-memory nonce replay cache. Rejects reused nonces until their natural
 * expiry (timestampMs + ttlMs). Thread-safe.
 */
class NonceCache(private val clock: () -> Long = { System.currentTimeMillis() }) {
    private data class Entry(val expiresAtMs: Long)
    private val seen = LinkedHashMap<String, Entry>()
    private val lock = Any()

    fun checkAndRemember(nonce: String, timestampMs: Long, ttlMs: Long): Boolean {
        val now = clock()
        val expires = timestampMs + ttlMs
        synchronized(lock) {
            purgeLocked(now)
            if (nonce in seen) return false
            if (now > expires) return false
            seen[nonce] = Entry(expires)
            return true
        }
    }

    fun size(): Int = synchronized(lock) { seen.size }

    private fun purgeLocked(now: Long) {
        val it = seen.entries.iterator()
        while (it.hasNext()) {
            if (it.next().value.expiresAtMs < now) it.remove()
        }
    }
}

data class StatusDomain(val name: String, val state: String, val detail: String? = null)

object NodeStatusBuilder {
    fun snapshot(
        nodeId: String,
        packageName: String,
        connectionClass: String,
        updatedAtMs: Long,
        domains: List<StatusDomain>,
    ): JSONObject {
        val arr = JSONArray()
        domains.forEach { d ->
            val o = JSONObject().put("name", d.name).put("state", d.state)
            val detail = d.detail?.take(BridgeV1.MAX_DETAIL_CHARS)
            if (detail != null && detail.isNotEmpty()) o.put("detail", detail)
            arr.put(o)
        }
        return JSONObject()
            .put("nodeId", nodeId)
            .put("packageName", packageName)
            .put("connectionClass", connectionClass)
            .put("updatedAtMs", updatedAtMs)
            .put("domains", arr)
    }

    /** Reject detail that looks like a URL or cookie/personal leak. */
    fun sanitizeDetail(raw: String?): String? {
        if (raw == null) return null
        val t = raw.trim().take(BridgeV1.MAX_DETAIL_CHARS)
        if (t.isEmpty()) return null
        val lower = t.lowercase()
        if (lower.contains("http://") || lower.contains("https://") ||
            lower.contains("cookie") || lower.contains("password") ||
            lower.contains("@")
        ) {
            return null
        }
        return t
    }
}

/**
 * Preferences-only config export/import for package com.victorious.browser.
 * NEVER exports cookies, passwords, proxy credentials, or certs.
 */
object ConfigBackup {
    const val PACKAGE = "com.victorious.browser"

    /** Prefs names that may contain secrets — refused if requested, stripped on export. */
    val SECRET_PREFS: Set<String> = setOf(
        "msf_rpc_config",
        "fleet_export_config",
        "virustotal_settings",
        "urlhaus_settings",
    )

    /** Keys inside otherwise-safe prefs that must never leave the device. */
    val SECRET_KEYS: Set<String> = setOf(
        "pass", "bearer_token", "api_key", "auth_key",
        "password", "cookie", "cookies", "proxy_password", "cert", "certs",
    )

    /**
     * SENSITIVE mesh-address keys (Victorious Mesh `mesh.ip`): never exported,
     * matched case-insensitively by substring.
     */
    val SENSITIVE_KEY_FRAGMENTS: Set<String> = setOf("mesh_ip", "meship", "mesh.ip")

    fun isSensitiveMeshKey(k: String): Boolean {
        val l = k.lowercase()
        return SENSITIVE_KEY_FRAGMENTS.any { l.contains(it) }
    }

    /** Keys that must never be imported as enabling remote control. */
    const val EXTERNAL_CONTROL_KEY = "external_control_enabled"

    fun refuseIfSecretRequested(include: Collection<String>?): String? {
        if (include == null) return null
        for (name in include) {
            if (name in SECRET_PREFS || name in SECRET_KEYS || isSensitiveMeshKey(name) ||
                name.contains("cookie", ignoreCase = true) ||
                name.contains("password", ignoreCase = true) ||
                name.contains("cert", ignoreCase = true) ||
                name.contains("credential", ignoreCase = true)
            ) {
                return "refusing secret export: $name"
            }
        }
        return null
    }

    fun stripSecrets(prefsName: String, entries: Map<String, Any?>): Map<String, Any?>? {
        if (prefsName in SECRET_PREFS) return null
        return entries.filterKeys { k ->
            k !in SECRET_KEYS &&
                !isSensitiveMeshKey(k) &&
                !k.contains("cookie", ignoreCase = true) &&
                !k.contains("password", ignoreCase = true) &&
                !k.contains("cert", ignoreCase = true)
        }
    }

    /**
     * Merge [incoming] into [existing] per [policy].
     * Never allows EXTERNAL_CONTROL_KEY to become true via import.
     */
    fun resolveConflict(
        existing: Map<String, Any?>,
        incoming: Map<String, Any?>,
        policy: ConflictPolicy,
    ): Map<String, Any?> {
        val safeIncoming = incoming.toMutableMap()
        // NEVER auto-enable remote.command / external control via import.
        if (safeIncoming[EXTERNAL_CONTROL_KEY] == true) {
            safeIncoming[EXTERNAL_CONTROL_KEY] = false
        }
        return when (policy) {
            ConflictPolicy.SKIP -> {
                val out = existing.toMutableMap()
                for ((k, v) in safeIncoming) {
                    if (k !in out) out[k] = v
                }
                out
            }
            ConflictPolicy.OVERWRITE -> {
                val out = existing.toMutableMap()
                out.putAll(safeIncoming)
                out
            }
            ConflictPolicy.MERGE_BY_ID -> {
                // For flat prefs, merge-by-id == key-level merge (incoming wins on overlap).
                val out = existing.toMutableMap()
                out.putAll(safeIncoming)
                out
            }
        }
    }

    fun exportEntry(packageName: String, preferences: Map<String, Map<String, Any?>>): JSONObject {
        if (packageName != PACKAGE) throw V1Invalid("only $PACKAGE supported")
        val prefsArr = JSONArray()
        preferences.forEach { (name, entries) ->
            val stripped = stripSecrets(name, entries) ?: return@forEach
            val kv = JSONObject()
            stripped.forEach { (k, v) -> kv.put(k, v ?: JSONObject.NULL) }
            prefsArr.put(JSONObject().put("name", name).put("entries", kv))
        }
        return JSONObject()
            .put("packageName", PACKAGE)
            .put("kind", "preferences")
            .put("preferences", prefsArr)
    }
}
