package com.victorious.browser.export

import java.net.HttpURLConnection
import java.net.URL

/**
 * HTTP sink: POST the export JSON to a user-configured local/mesh endpoint
 * (intended for the HackerAI-Commander / Pentest-Swarm-AI web APIs), with an
 * optional bearer token.
 *
 * TODO(contract): The exact request path, headers and body shape expected by
 * HackerAI-Commander and Pentest-Swarm-AI are NOT confirmed. The full endpoint
 * URL and auth token are user-supplied via [ExportSettings]; this sink just
 * POSTs the generic payload as `application/json`. Confirm the real contract
 * with those tools' owners before relying on it in production.
 */
class HttpFleetSink(
    private val endpointUrl: String,
    private val bearerToken: String = "",
) : FleetSink {
    override val id = "http"
    override val label = "HTTP (fleet web API)"

    override fun send(payload: FleetExportPayload): FleetSink.Result {
        if (endpointUrl.isBlank()) {
            return FleetSink.Result(ok = false, detail = "No endpoint URL configured.")
        }
        val url = try {
            URL(endpointUrl)
        } catch (e: Exception) {
            return FleetSink.Result(ok = false, detail = "Invalid endpoint URL: ${e.message}")
        }
        val body = payload.toJsonString(indent = 0).toByteArray(Charsets.UTF_8)
        return try {
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Accept", "application/json")
            if (bearerToken.isNotBlank()) {
                conn.setRequestProperty("Authorization", "Bearer $bearerToken")
            }
            conn.setFixedLengthStreamingMode(body.size)
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val resp = stream?.readBytes()?.toString(Charsets.UTF_8)?.take(500).orEmpty()
            conn.disconnect()
            FleetSink.Result(
                ok = code in 200..299,
                detail = if (code in 200..299) "Sent (HTTP $code). $resp" else "HTTP $code. $resp",
                httpStatus = code,
            )
        } catch (t: Throwable) {
            FleetSink.Result(ok = false, detail = "Send failed: ${t.message ?: t.javaClass.simpleName}")
        }
    }
}
