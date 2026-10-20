package com.victorious.browser.export

import org.json.JSONArray
import org.json.JSONObject

/**
 * Normalized, generic "fleet sink" export payload.
 *
 * This is a tool-agnostic shape intended to be consumed by other fleet tools
 * (e.g. HackerAI-Commander, Pentest-Swarm-AI). It deliberately carries ONLY
 * non-secret recon data: targets, endpoints (method/url/params/headers), and
 * findings. Cookies, passwords, proxy credentials and certificate material are
 * stripped during normalization (see [SecretStripper]) and must never appear
 * here.
 *
 * NOTE: the exact wire contract expected by HackerAI-Commander / Pentest-Swarm-AI
 * is NOT confirmed. This shape is a clean generic default; the receiving endpoint
 * must be supplied/confirmed by the user. See README + [com.victorious.browser.export.HttpFleetSink].
 */
const val EXPORT_SOURCE = "com.victorious.browser"

/** Current payload schema version. */
const val EXPORT_SCHEMA_VERSION = 1

data class ExportEndpoint(
    val method: String,
    val url: String,
    val params: Map<String, String> = emptyMap(),
    val headers: Map<String, String>? = null,
    val statusCode: Int? = null,
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("method", method)
        o.put("url", url)
        val p = JSONObject()
        params.forEach { (k, v) -> p.put(k, v) }
        o.put("params", p)
        if (headers != null) {
            val h = JSONObject()
            headers.forEach { (k, v) -> h.put(k, v) }
            o.put("headers", h)
        }
        o.put("statusCode", statusCode ?: JSONObject.NULL)
        return o
    }
}

data class ExportFinding(
    val title: String,
    val category: String,
    val severity: String,
    val url: String,
    val evidence: String,
    val source: String = EXPORT_SOURCE,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("title", title)
        .put("category", category)
        .put("severity", severity)
        .put("url", url)
        .put("evidence", evidence)
        .put("source", source)
}

data class FleetExportPayload(
    val targets: List<String>,
    val endpoints: List<ExportEndpoint>,
    val findings: List<ExportFinding>,
    val source: String = EXPORT_SOURCE,
    val exportedAt: Long = System.currentTimeMillis(),
    val schemaVersion: Int = EXPORT_SCHEMA_VERSION,
) {
    fun toJson(): JSONObject {
        val root = JSONObject()
        root.put("source", source)
        root.put("schemaVersion", schemaVersion)
        root.put("exportedAt", exportedAt)
        root.put("targets", JSONArray(targets))
        root.put("endpoints", JSONArray().apply { endpoints.forEach { put(it.toJson()) } })
        root.put("findings", JSONArray().apply { findings.forEach { put(it.toJson()) } })
        val counts = JSONObject()
            .put("targets", targets.size)
            .put("endpoints", endpoints.size)
            .put("findings", findings.size)
        root.put("counts", counts)
        return root
    }

    fun toJsonString(indent: Int = 2): String =
        if (indent > 0) toJson().toString(indent) else toJson().toString()
}
