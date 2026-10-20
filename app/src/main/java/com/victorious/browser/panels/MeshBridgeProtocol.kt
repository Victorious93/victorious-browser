package com.victorious.browser.panels

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure (Android-free) codec for the Victorious Mesh fleet bridge
 * (`docs/FLEET_BRIDGE.md` in Victorious93/victorious-mesh, protocol 1.1).
 *
 * SCOPE (Fleet Orchestrator, edge approval): this browser may call ONLY the
 * read ops `mesh.status` and `mesh.ip` (wire name `mesh.ip.get`). No
 * `node.status.get` (the droidcommand-AI controller is the sole status-tray
 * aggregator), no `mesh.up`/`mesh.down`, no control ops. External control stays OFF.
 *
 * SENSITIVE: `mesh.ip` results are never logged, persisted, or exported.
 */
object MeshBridgeProtocol {
    const val ACTION_BRIDGE = "com.victorious.mesh.action.BRIDGE"
    const val MSG_REQUEST = 1
    const val MSG_RESPONSE = 2
    const val KEY_REQUEST = "request"
    const val KEY_RESPONSE = "response"
    const val PROTOCOL_MAJOR = 1
    const val PROTOCOL_MINOR = 1

    const val OP_MESH_STATUS = "mesh.status"
    /** Orchestrator calls it `mesh.ip`; the Mesh wire name is `mesh.ip.get`. */
    const val OP_MESH_IP = "mesh.ip.get"

    /** The only ops this client may ever send. */
    val ALLOWED_OPS: Set<String> = setOf(OP_MESH_STATUS, OP_MESH_IP)

    const val TTL_DEFAULT_MS = 120_000L
    const val TTL_MAX_MS = 300_000L
    const val DEFAULT_TIMEOUT_MS = 5_000L

    private val NODE_ID = Regex("^mesh-[0-9a-f]{32}$")

    fun isValidNodeId(id: String?): Boolean = id != null && NODE_ID.matches(id)

    /** TTL clamp: <=0 → default; > 300 s → 300 s. */
    fun clampTtl(ttlMs: Long?): Long = when {
        ttlMs == null || ttlMs <= 0L -> TTL_DEFAULT_MS
        ttlMs > TTL_MAX_MS -> TTL_MAX_MS
        else -> ttlMs
    }

    private fun requireAllowed(op: String) {
        require(op in ALLOWED_OPS) { "op not permitted on direct Mesh edge: $op" }
    }

    /**
     * Legacy `mesh.*` BridgeRequest (snake_case, `parameters` must be `{}`).
     * [targetNodeId] is included only when known and well-formed.
     */
    fun buildLegacyRequest(op: String, requestId: String, targetNodeId: String?, timeoutMs: Long = DEFAULT_TIMEOUT_MS): JSONObject {
        requireAllowed(op)
        val o = JSONObject()
            .put("protocol_major", PROTOCOL_MAJOR)
            .put("request_id", requestId)
            .put("operation", op)
            .put("timeout_ms", timeoutMs)
            .put("parameters", JSONObject())
        if (isValidNodeId(targetNodeId)) o.put("target_node_id", targetNodeId)
        return o
    }

    /**
     * Frozen v1.1 envelope (`remote.command` with a read-only actionRef).
     * Requires a known node id (`targetNodeRef`); TTL clamped to ≤ 300 s.
     */
    fun buildEnvelope(
        actionRef: String,
        requestId: String,
        targetNodeRef: String,
        nonce: String,
        timestampMs: Long,
        ttlMs: Long? = null,
    ): JSONObject {
        requireAllowed(actionRef)
        require(isValidNodeId(targetNodeRef)) { "targetNodeRef must be mesh-<32 hex>" }
        val n = nonce.trim().lowercase().replace("-", "")
        require(n.length == 32 && n.all { it in '0'..'9' || it in 'a'..'f' }) { "nonce must be 128-bit hex" }
        return JSONObject()
            .put("protocol_major", PROTOCOL_MAJOR)
            .put("protocol_minor", PROTOCOL_MINOR)
            .put("op", "remote.command")
            .put("requestId", requestId)
            .put("targetNodeRef", targetNodeRef)
            .put("actionRef", actionRef)
            .put("params", JSONObject())
            .put("nonce", n)
            .put("timestampMs", timestampMs)
            .put("ttlMs", clampTtl(ttlMs))
            .put("destructive", false)
            .put("trigger", JSONObject())
    }

    /** Raw response → typed result. Never throws. */
    fun parseResponse(raw: String?): BridgeReply {
        if (raw.isNullOrBlank()) return BridgeReply.Failure(BridgeErrorKind.MALFORMED, "empty response")
        val o = try { JSONObject(raw) } catch (_: Exception) {
            return BridgeReply.Failure(BridgeErrorKind.MALFORMED, "not JSON")
        }
        val nodeId = o.optString("node_id", "").takeIf { isValidNodeId(it) }
        val ok = o.optBoolean("ok", false)
        if (!ok) {
            val category = errorCategory(o.opt("error"))
            return BridgeReply.Failure(classify(category), category ?: "error", nodeId)
        }
        val result = o.optJSONObject("result") ?: JSONObject()
        return BridgeReply.Success(result, nodeId, o.optString("request_id", "").ifEmpty { null })
    }

    private fun errorCategory(err: Any?): String? = when (err) {
        is String -> err.ifBlank { null }
        is JSONObject -> listOf("category", "code", "error", "type").firstNotNullOfOrNull {
            err.optString(it, "").ifBlank { null }
        }
        else -> null
    }

    fun classify(category: String?): BridgeErrorKind = when (category) {
        "unauthorized", "unauthorized_caller" -> BridgeErrorKind.NOT_AUTHORIZED
        "external_control_disabled" -> BridgeErrorKind.DISABLED
        "expired", "replay_detected", "invalid_parameters", "malformed_request",
        "protocol_mismatch", "node_mismatch", "not_found", "not_paired" -> BridgeErrorKind.PROTOCOL
        "unsupported_operation", "unsupported_op", "capability_off" -> BridgeErrorKind.UNSUPPORTED
        else -> BridgeErrorKind.OTHER
    }

    /** `mesh.status` result. Missing fields default conservatively. */
    fun parseStatus(result: JSONObject): MeshBridgeStatus {
        val ips = result.optJSONArray("ips") ?: JSONArray()
        return MeshBridgeStatus(
            backendState = result.optString("backend_state", "").ifEmpty { null },
            connected = result.optBoolean("connected", false),
            ipCount = ips.length(),
        )
    }

    /** `mesh.ip.get` result. SENSITIVE — keep in memory only. */
    fun parseIp(result: JSONObject): MeshBridgeIp {
        fun s(k: String) = if (result.isNull(k)) null else result.optString(k, "").ifBlank { null }
        return MeshBridgeIp(ipv4 = s("ipv4"), ipv6 = s("ipv6"))
    }
}

enum class BridgeErrorKind { NOT_INSTALLED, NOT_AUTHORIZED, DISABLED, UNAVAILABLE, TIMEOUT, PROTOCOL, UNSUPPORTED, MALFORMED, OTHER }

sealed class BridgeReply {
    data class Success(val result: JSONObject, val nodeId: String?, val requestId: String?) : BridgeReply()
    data class Failure(val kind: BridgeErrorKind, val detail: String, val nodeId: String? = null) : BridgeReply()
}

/** Status summary. Deliberately keeps only the IP COUNT, never the addresses. */
data class MeshBridgeStatus(val backendState: String?, val connected: Boolean, val ipCount: Int)

/** SENSITIVE. toString() is redacted so it can never leak via logs. */
data class MeshBridgeIp(val ipv4: String?, val ipv6: String?) {
    override fun toString(): String = "MeshBridgeIp(<redacted>)"
}

/** UI-facing bridge state. */
sealed class MeshBridgeState {
    object Off : MeshBridgeState()
    data class Connected(val status: MeshBridgeStatus) : MeshBridgeState()
    data class Unavailable(val kind: BridgeErrorKind) : MeshBridgeState()

    fun label(): String = when (this) {
        Off -> "mesh bridge: off (passive detection)"
        is Connected -> "mesh bridge: ${if (status.connected) "connected" else "mesh down"}" +
            (status.backendState?.let { " ($it)" } ?: "")
        is Unavailable -> when (kind) {
            BridgeErrorKind.NOT_AUTHORIZED -> "mesh bridge: not authorized yet — using passive detection"
            BridgeErrorKind.DISABLED -> "mesh bridge: external control off in Mesh — using passive detection"
            BridgeErrorKind.NOT_INSTALLED -> "mesh bridge: Victorious Mesh not installed"
            else -> "mesh bridge: unavailable — using passive detection"
        }
    }
}
