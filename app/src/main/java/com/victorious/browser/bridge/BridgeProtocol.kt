package com.victorious.browser.bridge

import org.json.JSONArray
import org.json.JSONObject

/**
 * EXTERNAL_NODE wire protocol — newline-delimited JSON, major = 1.
 *
 * BridgeRequest  { id, op, params }
 * BridgeResponse { id, ok, result?, error? } where error = { category, message }
 */
object BridgeProtocol {
    const val BRIDGE_PROTOCOL_MAJOR = 1

    const val MSG_WHAT_REQUEST = 1
    const val MSG_WHAT_RESPONSE = 2

    const val ACTION = "com.victorious.browser.action.BRIDGE"
    const val BUNDLE_LINE = "line"

    const val OP_CAPABILITIES_GET = "browser.capabilities.get"
    const val OP_OPEN_URL = "browser.open_url"
    const val OP_PANEL_LIST = "browser.panel.list"
    const val OP_PANEL_OPEN = "browser.panel.open"
    const val OP_RECON_SUMMARY = "browser.recon.summary"
    const val OP_PROXY_SET = "browser.proxy.set"
    const val OP_PROXY_STATUS = "browser.proxy.status"

    /** Ops that must receive exactly {}. */
    val PARAMETERLESS_OPS: Set<String> = setOf(
        OP_CAPABILITIES_GET,
        OP_PANEL_LIST,
        OP_PROXY_STATUS,
    )

    val ALL_OPERATIONS: List<String> = listOf(
        OP_CAPABILITIES_GET,
        OP_OPEN_URL,
        OP_PANEL_LIST,
        OP_PANEL_OPEN,
        OP_RECON_SUMMARY,
        OP_PROXY_SET,
        OP_PROXY_STATUS,
    )
}

enum class BridgeErrorCategory(val wire: String) {
    INVALID_PARAMETERS("invalid_parameters"),
    NOT_PAIRED("not_paired"),
    NOT_FOUND("not_found"),
    PROXY_ERROR("proxy_error"),
    INTERNAL("internal"),
}

data class BridgeRequest(
    val id: String,
    val op: String,
    val params: JSONObject,
)

data class BridgeError(
    val category: BridgeErrorCategory,
    val message: String,
)

data class BridgeResponse(
    val id: String,
    val ok: Boolean,
    val result: JSONObject? = null,
    val error: BridgeError? = null,
)

object BridgeCodec {
    fun parseRequestLine(line: String): BridgeRequest {
        val o = JSONObject(line.trim())
        val id = o.getString("id")
        val op = o.getString("op")
        val params = o.optJSONObject("params") ?: JSONObject()
        return BridgeRequest(id = id, op = op, params = params)
    }

    fun encodeResponse(resp: BridgeResponse): String {
        val o = JSONObject()
        o.put("id", resp.id)
        o.put("ok", resp.ok)
        if (resp.result != null) o.put("result", resp.result)
        if (resp.error != null) {
            o.put(
                "error",
                JSONObject()
                    .put("category", resp.error.category.wire)
                    .put("message", resp.error.message),
            )
        }
        return o.toString()
    }

    fun ok(id: String, result: JSONObject): BridgeResponse =
        BridgeResponse(id = id, ok = true, result = result)

    fun err(id: String, category: BridgeErrorCategory, message: String): BridgeResponse =
        BridgeResponse(id = id, ok = false, error = BridgeError(category, message))

    /** True iff [params] is exactly an empty object {}. */
    fun isEmptyParams(params: JSONObject): Boolean = params.length() == 0

    fun requireEmptyParams(req: BridgeRequest): BridgeResponse? {
        if (req.op in BridgeProtocol.PARAMETERLESS_OPS && !isEmptyParams(req.params)) {
            return err(
                req.id,
                BridgeErrorCategory.INVALID_PARAMETERS,
                "op ${req.op} requires exactly {}",
            )
        }
        return null
    }
}
