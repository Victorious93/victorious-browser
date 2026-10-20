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
 * Also handles frozen remote-control envelope v1 ops ([dispatchV1]).
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

    fun dispatchV1(env: EnvelopeV1): BridgeResponse {
        return try {
            when (env.op) {
                BridgeV1.OP_CAPABILITIES_GET -> v1Capabilities(env)
                BridgeV1.OP_REMOTE_COMMAND -> v1RemoteCommand(env)
                BridgeV1.OP_MACRO_STEP -> v1MacroStep(env)
                BridgeV1.OP_MACRO_RUN -> v1MacroRun(env)
                BridgeV1.OP_NODE_STATUS_GET -> v1NodeStatus(env)
                BridgeV1.OP_CONFIG_EXPORT -> v1ConfigExport(env)
                BridgeV1.OP_CONFIG_IMPORT -> v1ConfigImport(env)
                else -> BridgeCodec.err(
                    env.requestId,
                    BridgeErrorCategory.INVALID_PARAMETERS,
                    "unknown op: ${env.op}",
                )
            }
        } catch (t: V1Invalid) {
            BridgeCodec.err(
                env.requestId,
                BridgeErrorCategory.INVALID_PARAMETERS,
                t.message ?: "invalid_parameters",
            )
        } catch (t: Throwable) {
            Log.e(TAG, "dispatchV1 ${env.op}", t)
            BridgeCodec.err(env.requestId, BridgeErrorCategory.INTERNAL, t.message ?: "internal error")
        }
    }

    private fun v1Capabilities(env: EnvelopeV1): BridgeResponse {
        val result = JSONObject()
            .put("protocol_major", BridgeProtocol.BRIDGE_PROTOCOL_MAJOR)
            .put("protocol_minor", BridgeProtocol.BRIDGE_PROTOCOL_MINOR)
            .put("package", context.packageName)
            .put("node_id", settings.nodeId)
            .put("operations", CapabilitiesRegistry.toJson())
            .put("action_refs", JSONArray(BridgeProtocol.ALL_OPERATIONS))
            .put("proxy_supported", true)
        return BridgeCodec.ok(env.requestId, result)
    }

    private fun v1RemoteCommand(env: EnvelopeV1): BridgeResponse {
        val actionRef = env.actionRef
            ?: return BridgeCodec.err(
                env.requestId,
                BridgeErrorCategory.INVALID_PARAMETERS,
                "actionRef required",
            )
        val inner = BridgeRequest(id = env.requestId, op = actionRef, params = env.params)
        return dispatch(inner)
    }

    private fun v1MacroStep(env: EnvelopeV1): BridgeResponse {
        val actionRef = env.actionRef
            ?: return BridgeCodec.err(
                env.requestId,
                BridgeErrorCategory.INVALID_PARAMETERS,
                "actionRef required",
            )
        val stepResult = dispatch(
            BridgeRequest(id = env.requestId, op = actionRef, params = env.params),
        )
        val result = JSONObject()
            .put("macroId", env.macroId)
            .put("stepIndex", env.stepIndex)
            .put("actionRef", actionRef)
            .put("ok", stepResult.ok)
        if (stepResult.result != null) result.put("result", stepResult.result)
        if (stepResult.error != null) {
            result.put(
                "error",
                JSONObject()
                    .put("category", stepResult.error.category.wire)
                    .put("message", stepResult.error.message),
            )
        }
        return if (stepResult.ok) {
            BridgeCodec.ok(env.requestId, result)
        } else {
            // Surface step failure; onError is informational for single-step.
            BridgeResponse(
                id = env.requestId,
                ok = false,
                result = result,
                error = stepResult.error,
            )
        }
    }

    private fun v1MacroRun(env: EnvelopeV1): BridgeResponse {
        val steps = env.steps.orEmpty()
        val onError = env.onError ?: OnErrorPolicy.ABORT
        val stepResults = JSONArray()
        if (env.dryRun) {
            // Validate only — do not execute.
            steps.forEachIndexed { i, s ->
                stepResults.put(
                    JSONObject()
                        .put("index", i)
                        .put("actionRef", s.actionRef)
                        .put("ok", true)
                        .put("dryRun", true),
                )
            }
            return BridgeCodec.ok(
                env.requestId,
                JSONObject()
                    .put("macroId", env.macroId)
                    .put("dryRun", true)
                    .put("count", steps.size)
                    .put("steps", stepResults),
            )
        }
        var aborted = false
        steps.forEachIndexed { i, s ->
            if (aborted) {
                stepResults.put(
                    JSONObject()
                        .put("index", i)
                        .put("actionRef", s.actionRef)
                        .put("ok", false)
                        .put("skipped", true),
                )
                return@forEachIndexed
            }
            val r = dispatch(BridgeRequest(id = "${env.requestId}#$i", op = s.actionRef, params = s.params))
            val entry = JSONObject()
                .put("index", i)
                .put("actionRef", s.actionRef)
                .put("ok", r.ok)
            if (r.error != null) {
                entry.put(
                    "error",
                    JSONObject()
                        .put("category", r.error.category.wire)
                        .put("message", r.error.message),
                )
            }
            stepResults.put(entry)
            if (!r.ok && onError == OnErrorPolicy.ABORT) {
                aborted = true
            }
        }
        val allOk = (0 until stepResults.length()).all { stepResults.getJSONObject(it).optBoolean("ok") }
        val result = JSONObject()
            .put("macroId", env.macroId)
            .put("dryRun", false)
            .put("count", steps.size)
            .put("aborted", aborted)
            .put("steps", stepResults)
        return if (allOk) {
            BridgeCodec.ok(env.requestId, result)
        } else {
            BridgeCodec.err(
                env.requestId,
                BridgeErrorCategory.INTERNAL,
                if (aborted) "macro aborted on step error" else "macro completed with errors",
            ).copy(result = result)
        }
    }

    private fun v1NodeStatus(env: EnvelopeV1): BridgeResponse {
        val proxyRunning = LocalProxyService.isRunning.value
        val browserState = if (proxyRunning) "proxy_on" else "idle"
        val healthState = "ok"
        val domains = listOf(
            StatusDomain(
                name = "browser",
                state = browserState,
                detail = NodeStatusBuilder.sanitizeDetail(
                    "webview ready; proxy=${if (proxyRunning) "on" else "off"}",
                ),
            ),
            StatusDomain(
                name = "node_health",
                state = healthState,
                detail = NodeStatusBuilder.sanitizeDetail("bridge major=1 minor=1"),
            ),
        )
        val snap = NodeStatusBuilder.snapshot(
            nodeId = settings.nodeId,
            packageName = context.packageName,
            connectionClass = "EXTERNAL_NODE",
            updatedAtMs = System.currentTimeMillis(),
            domains = domains,
        )
        return BridgeCodec.ok(env.requestId, snap)
    }

    private fun v1ConfigExport(env: EnvelopeV1): BridgeResponse {
        // Refuse secret includes if caller asked for them.
        val include = mutableListOf<String>()
        val arr = env.params.optJSONArray("include")
        if (arr != null) {
            for (i in 0 until arr.length()) include.add(arr.optString(i))
        }
        ConfigBackup.refuseIfSecretRequested(include)?.let { reason ->
            return BridgeCodec.err(env.requestId, BridgeErrorCategory.INVALID_PARAMETERS, reason)
        }
        // Also refuse if params ask for cookies/passwords/certs explicitly.
        for (key in listOf("cookies", "passwords", "certs", "credentials", "proxy_credentials")) {
            if (env.params.has(key) && env.params.optBoolean(key, false)) {
                return BridgeCodec.err(
                    env.requestId,
                    BridgeErrorCategory.INVALID_PARAMETERS,
                    "refusing secret export: $key",
                )
            }
        }

        val preferences = collectExportablePreferences()
        val entry = ConfigBackup.exportEntry(ConfigBackup.PACKAGE, preferences)
        return BridgeCodec.ok(
            env.requestId,
            JSONObject().put("entries", JSONArray().put(entry)),
        )
    }

    private fun collectExportablePreferences(): Map<String, Map<String, Any?>> {
        // Preferences ONLY — never cookies / WebView storage / keystores.
        val out = linkedMapOf<String, Map<String, Any?>>()
        val safeNames = listOf(
            "fleet_bridge",
            "browser_night_mode",
            "content_blocker",
            "fleet_panels",
            "netid_spoof",
            "scanner_config",
            "evidence_store",
            "mesh_config", // ranges/base_domain/toggle only; mesh IPs never stored + stripped
        )
        for (name in safeNames) {
            val prefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
            val raw = prefs.all
            val stripped = ConfigBackup.stripSecrets(name, raw) ?: continue
            // Force-export external_control as false so restore never enables remote.
            val safe = stripped.toMutableMap()
            if (name == "fleet_bridge" && ConfigBackup.EXTERNAL_CONTROL_KEY in safe) {
                safe[ConfigBackup.EXTERNAL_CONTROL_KEY] = false
            }
            if (safe.isNotEmpty()) out[name] = safe
        }
        return out
    }

    private fun v1ConfigImport(env: EnvelopeV1): BridgeResponse {
        val policy = env.conflictPolicy ?: ConflictPolicy.SKIP
        val prefsArr = env.params.optJSONArray("preferences")
            ?: return BridgeCodec.err(
                env.requestId,
                BridgeErrorCategory.INVALID_PARAMETERS,
                "preferences array required",
            )
        // Refuse secret prefs/keys in the payload.
        for (i in 0 until prefsArr.length()) {
            val p = prefsArr.optJSONObject(i) ?: continue
            val name = p.optString("name", "")
            ConfigBackup.refuseIfSecretRequested(listOf(name))?.let { reason ->
                return BridgeCodec.err(env.requestId, BridgeErrorCategory.INVALID_PARAMETERS, reason)
            }
            val entries = p.optJSONObject("entries")
            if (entries != null) {
                val keys = entries.keys().asSequence().toList()
                ConfigBackup.refuseIfSecretRequested(keys)?.let { reason ->
                    return BridgeCodec.err(env.requestId, BridgeErrorCategory.INVALID_PARAMETERS, reason)
                }
            }
        }

        if (env.dryRun) {
            return BridgeCodec.ok(
                env.requestId,
                JSONObject()
                    .put("dryRun", true)
                    .put("conflictPolicy", policy.wire)
                    .put("wouldApply", prefsArr.length())
                    .put("requiresConfirm", true),
            )
        }

        // Require on-device confirmation.
        if (!ConfigImportGate.consumeApproval(env.requestId)) {
            ConfigImportGate.submitPending(
                env.requestId,
                policy,
                ConfigImportGate.summarizePayload(env.params),
            )
            return BridgeCodec.err(
                env.requestId,
                BridgeErrorCategory.REQUIRES_CONFIRM,
                "on-device confirmation required for config.import",
            )
        }

        var applied = 0
        for (i in 0 until prefsArr.length()) {
            val p = prefsArr.optJSONObject(i) ?: continue
            val name = p.optString("name", "").trim()
            if (name.isEmpty() || name in ConfigBackup.SECRET_PREFS) continue
            val entriesObj = p.optJSONObject("entries") ?: continue
            val incoming = mutableMapOf<String, Any?>()
            val keys = entriesObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (k in ConfigBackup.SECRET_KEYS) continue
                incoming[k] = entriesObj.get(k)
            }
            val prefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
            val existing = prefs.all.toMap()
            val merged = ConfigBackup.resolveConflict(existing, incoming, policy)
            val editor = prefs.edit()
            // Clear keys not in merged only for overwrite; skip/merge keep others.
            if (policy == ConflictPolicy.OVERWRITE) {
                editor.clear()
            }
            for ((k, v) in merged) {
                when (v) {
                    null, JSONObject.NULL -> editor.remove(k)
                    is Boolean -> editor.putBoolean(k, v)
                    is Int -> editor.putInt(k, v)
                    is Long -> editor.putLong(k, v)
                    is Float -> editor.putFloat(k, v)
                    is String -> editor.putString(k, v)
                    is Set<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        editor.putStringSet(k, v as Set<String>)
                    }
                    else -> editor.putString(k, v.toString())
                }
            }
            // NEVER auto-enable external control via import.
            if (name == "fleet_bridge") {
                editor.putBoolean(ConfigBackup.EXTERNAL_CONTROL_KEY, false)
            }
            editor.apply()
            applied++
        }
        return BridgeCodec.ok(
            env.requestId,
            JSONObject()
                .put("applied", applied)
                .put("conflictPolicy", policy.wire)
                .put("externalControlForcedOff", true),
        )
    }

    private fun capabilities(req: BridgeRequest): BridgeResponse {
        val result = JSONObject()
            .put("protocol_major", BridgeProtocol.BRIDGE_PROTOCOL_MAJOR)
            .put("protocol_minor", BridgeProtocol.BRIDGE_PROTOCOL_MINOR)
            .put("package", context.packageName)
            .put("node_id", settings.nodeId)
            .put("operations", JSONArray(BridgeProtocol.ALL_OPERATIONS))
            .put("v1_operations", CapabilitiesRegistry.toJson())
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
        BridgeCommandBus.openUrl(url)
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
