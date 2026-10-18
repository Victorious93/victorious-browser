package com.victorious.browser.bridge

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.victorious.browser.MainActivity
import com.victorious.browser.inspect.CaptureStore
import com.victorious.browser.inspect.LocalProxyService
import com.victorious.browser.panels.PanelStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * Dispatches bridge ops after identity + external-control checks have passed.
 * Wires panel ops to [PanelStore], recon to [CaptureStore], proxy to [LocalProxyService].
 */
class BridgeDispatcher(private val context: Context) {
    private val settings = BridgeSettings.get(context)

    fun dispatch(req: BridgeRequest): BridgeResponse {
        BridgeCodec.requireEmptyParams(req)?.let { return it }
        return try {
            when (req.op) {
                BridgeProtocol.OP_CAPABILITIES_GET -> capabilities(req)
                BridgeProtocol.OP_OPEN_URL -> openUrl(req)
                BridgeProtocol.OP_PANEL_LIST -> panelList(req)
                BridgeProtocol.OP_PANEL_OPEN -> panelOpen(req)
                BridgeProtocol.OP_RECON_SUMMARY -> reconSummary(req)
                BridgeProtocol.OP_PROXY_SET -> proxySet(req)
                BridgeProtocol.OP_PROXY_STATUS -> proxyStatus(req)
                else -> BridgeCodec.err(
                    req.id,
                    BridgeErrorCategory.INVALID_PARAMETERS,
                    "unknown op: ${req.op}",
                )
            }
        } catch (t: Throwable) {
            Log.e(TAG, "dispatch ${req.op}", t)
            BridgeCodec.err(req.id, BridgeErrorCategory.INTERNAL, t.message ?: "internal error")
        }
    }

    private fun capabilities(req: BridgeRequest): BridgeResponse {
        val result = JSONObject()
            .put("protocol_major", BridgeProtocol.BRIDGE_PROTOCOL_MAJOR)
            .put("package", context.packageName)
            .put("node_id", settings.nodeId)
            .put("operations", JSONArray(BridgeProtocol.ALL_OPERATIONS))
            .put("proxy_supported", true)
        return BridgeCodec.ok(req.id, result)
    }

    private fun openUrl(req: BridgeRequest): BridgeResponse {
        val url = req.params.optString("url", "").trim()
        if (url.isEmpty() || !isValidHttpUrl(url)) {
            return BridgeCodec.err(
                req.id,
                BridgeErrorCategory.INVALID_PARAMETERS,
                "missing or invalid url",
            )
        }
        // Extra keys beyond {url} are tolerated; only url is required.
        BridgeCommandBus.openUrl(url)
        // Also bring the UI forward so a cold start still opens the page.
        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_URL, url)
        }
        context.startActivity(launch)
        return BridgeCodec.ok(req.id, JSONObject().put("status", "opened"))
    }

    private fun panelList(req: BridgeRequest): BridgeResponse {
        val panels = PanelStore.get(context).panels.value
        val arr = JSONArray()
        panels.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("url", p.url)
                    .put("kind", p.kind.name),
            )
        }
        return BridgeCodec.ok(req.id, JSONObject().put("panels", arr))
    }

    private fun panelOpen(req: BridgeRequest): BridgeResponse {
        val panelId = req.params.optString("panel_id", "").trim()
        if (panelId.isEmpty()) {
            return BridgeCodec.err(
                req.id,
                BridgeErrorCategory.INVALID_PARAMETERS,
                "missing panel_id",
            )
        }
        val panel = PanelStore.get(context).get(panelId)
            ?: return BridgeCodec.err(
                req.id,
                BridgeErrorCategory.NOT_FOUND,
                "unknown panel_id: $panelId",
            )
        BridgeCommandBus.openUrl(panel.url)
        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_URL, panel.url)
        }
        context.startActivity(launch)
        return BridgeCodec.ok(req.id, JSONObject().put("status", "opened"))
    }

    private fun reconSummary(req: BridgeRequest): BridgeResponse {
        val limit = req.params.optInt("limit", 50).coerceIn(1, 500)
        val since = if (req.params.has("since")) req.params.optLong("since") else null
        var entries = CaptureStore.exchanges.value
        if (since != null) {
            entries = entries.filter { it.requestStartedAt >= since }
        }
        val sliced = entries.takeLast(limit)
        val arr = JSONArray()
        sliced.forEach { ex ->
            arr.put(
                JSONObject()
                    .put("method", ex.method)
                    .put("url", ex.url)
                    .put("status", ex.statusCode ?: JSONObject.NULL)
                    .put("host", ex.host)
                    .put("ts", ex.requestStartedAt),
            )
        }
        return BridgeCodec.ok(
            req.id,
            JSONObject().put("count", sliced.size).put("entries", arr),
        )
    }

    private fun proxySet(req: BridgeRequest): BridgeResponse {
        if (!req.params.has("enabled") || req.params.get("enabled") !is Boolean) {
            return BridgeCodec.err(
                req.id,
                BridgeErrorCategory.INVALID_PARAMETERS,
                "enabled:bool required",
            )
        }
        val enabled = req.params.getBoolean("enabled")
        return try {
            if (enabled) {
                val ok = LocalProxyService.start(context.applicationContext)
                if (!ok) {
                    return BridgeCodec.err(
                        req.id,
                        BridgeErrorCategory.PROXY_ERROR,
                        "proxy failed to start",
                    )
                }
            } else {
                LocalProxyService.stop()
            }
            // Fire-and-forget ack — status is pollable via browser.proxy.status.
            BridgeCodec.ok(req.id, JSONObject().put("accepted", true).put("enabled", enabled))
        } catch (t: Throwable) {
            BridgeCodec.err(req.id, BridgeErrorCategory.PROXY_ERROR, t.message ?: "proxy error")
        }
    }

    private fun proxyStatus(req: BridgeRequest): BridgeResponse {
        val running = LocalProxyService.isRunning.value
        val result = JSONObject()
            .put("running", running)
            .put("port", LocalProxyService.port)
            // CA is packaged in-app (res/raw + network_security_config); always installed for this app.
            .put("ca_installed", true)
        return BridgeCodec.ok(req.id, result)
    }

    private fun isValidHttpUrl(url: String): Boolean {
        return try {
            val u = Uri.parse(url)
            val scheme = u.scheme?.lowercase()
            (scheme == "http" || scheme == "https") && !u.host.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val TAG = "BridgeDispatcher"
        const val EXTRA_OPEN_URL = "com.victorious.browser.extra.OPEN_URL"
    }
}
