package com.victorious.browser.scanner

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pluggable out-of-band (OOB) interaction-callback client.
 *
 * This bundles NO server. The user configures their own collaborator /
 * interactsh-style domain plus an HTTP polling endpoint (see [ScannerConfig]).
 * When nothing is configured the whole feature is inert: [isConfigured] is false,
 * no OOB payloads are generated, and polling is never attempted.
 *
 * Pure logic (payload-domain generation, poll-response parsing) is unit-tested in
 * [CollaboratorTest]. Network polling lives in [CollaboratorPoller].
 */
object Collaborator {

    data class Config(
        val domain: String = "",
        val pollUrl: String = "",
    ) {
        val configured: Boolean get() = domain.isNotBlank() && pollUrl.isNotBlank()
    }

    data class Interaction(
        val type: String,
        val sourceHost: String,
        val timestamp: String,
        val raw: String,
    )

    fun isConfigured(config: Config): Boolean = config.configured

    /**
     * Generate a unique callback hostname under the configured domain:
     * `<token>.<domain>`. Token is caller-supplied (e.g. a random hex) so the
     * same probe can be correlated to its interaction.
     */
    fun payloadHost(config: Config, token: String): String? {
        if (!config.configured) return null
        val t = token.lowercase().filter { it.isLetterOrDigit() }.ifEmpty { "probe" }
        return "$t.${config.domain.trim().trimEnd('.')}"
    }

    /** Full http URL payload for SSRF/XXE style embedding. */
    fun payloadUrl(config: Config, token: String): String? =
        payloadHost(config, token)?.let { "http://$it/" }

    /**
     * Parse a poll response. Accepts either an interactsh-like JSON object with an
     * "aggregation"/"data" array, or a plain JSON array of interaction objects.
     * Unknown shapes yield an empty list rather than throwing.
     */
    fun parseInteractions(json: String): List<Interaction> {
        if (json.isBlank()) return emptyList()
        return try {
            val trimmed = json.trim()
            val arr: JSONArray = when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> {
                    val obj = JSONObject(trimmed)
                    obj.optJSONArray("data")
                        ?: obj.optJSONArray("interactions")
                        ?: obj.optJSONArray("aggregation")
                        ?: JSONArray()
                }
                else -> JSONArray()
            }
            val out = mutableListOf<Interaction>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out += Interaction(
                    type = o.optString("protocol", o.optString("type", "unknown")),
                    sourceHost = o.optString("remote-address", o.optString("source", o.optString("sourceHost", ""))),
                    timestamp = o.optString("timestamp", o.optString("time", "")),
                    raw = o.toString(),
                )
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }
}
