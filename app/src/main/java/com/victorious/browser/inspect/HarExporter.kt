package com.victorious.browser.inspect

import android.content.Context
import com.victorious.browser.export.SecretStripper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

/**
 * HAR 1.2 export of proxy capture history with secrets stripped.
 *
 * Cookies, Authorization and other credential-bearing headers are dropped;
 * sensitive query params are removed; request/response bodies are omitted
 * (size + MIME only). This is the "leave the device" path — the on-device
 * JSON export in [CaptureExporter] still keeps full bodies for local analysis.
 */
object HarExporter {
    const val COMMENT =
        "Victorious Browser secret-stripped HAR: cookies/Authorization/tokens removed; " +
            "request and response bodies omitted."

    fun toHar(exchanges: List<CapturedExchange>, exportedAt: Long = System.currentTimeMillis()): String {
        val entries = JSONArray()
        exchanges.forEach { entries.put(entry(it)) }
        val log = JSONObject()
            .put("version", "1.2")
            .put(
                "creator",
                JSONObject()
                    .put("name", "Victorious Browser")
                    .put("version", "0.2.0"),
            )
            .put("comment", COMMENT)
            .put("pages", JSONArray())
            .put("entries", entries)
        return JSONObject()
            .put("log", log)
            .put("exportedAt", exportedAt)
            .toString(2)
    }

    fun writeToFile(context: Context, exchanges: List<CapturedExchange>): File {
        val dir = context.getExternalFilesDir("exports") ?: context.filesDir
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "network-capture-${System.currentTimeMillis()}.har")
        file.writeText(toHar(exchanges))
        return file
    }

    private fun entry(ex: CapturedExchange): JSONObject {
        val started = Instant.ofEpochMilli(ex.requestStartedAt).toString()
        val time = (ex.durationMs ?: 0L).coerceAtLeast(0L)
        val reqHeaders = strip(ex.requestHeaders)
        val resHeaders = strip(ex.responseHeaders)
        val cleanUrl = SecretStripper.stripUrlQuery(ex.url)
        val query = queryString(ex.url)
        val mime = ex.responseContentType()?.substringBefore(';')?.trim().orEmpty()
        val reqMime = ex.requestContentType()?.substringBefore(';')?.trim().orEmpty()

        val request = JSONObject()
            .put("method", ex.method)
            .put("url", cleanUrl)
            .put("httpVersion", "HTTP/1.1")
            .put("cookies", JSONArray())
            .put("headers", headersJson(reqHeaders))
            .put("queryString", query)
            .put("headersSize", -1)
            .put("bodySize", ex.requestBytes)
            .put(
                "postData",
                JSONObject()
                    .put("mimeType", reqMime)
                    .put("text", "")
                    .put("comment", "body omitted (secret-stripped HAR)"),
            )

        val response = JSONObject()
            .put("status", ex.statusCode ?: 0)
            .put("statusText", if (ex.blocked != null) "BLOCKED" else "")
            .put("httpVersion", "HTTP/1.1")
            .put("cookies", JSONArray())
            .put("headers", headersJson(resHeaders))
            .put(
                "content",
                JSONObject()
                    .put("size", ex.responseBytes)
                    .put("mimeType", mime)
                    .put("text", "")
                    .put("comment", "body omitted (secret-stripped HAR)"),
            )
            .put("redirectURL", "")
            .put("headersSize", -1)
            .put("bodySize", ex.responseBytes)

        return JSONObject()
            .put("startedDateTime", started)
            .put("time", time)
            .put("request", request)
            .put("response", response)
            .put("cache", JSONObject())
            .put(
                "timings",
                JSONObject()
                    .put("blocked", -1)
                    .put("dns", -1)
                    .put("connect", -1)
                    .put("ssl", -1)
                    .put("send", 0)
                    .put("wait", time)
                    .put("receive", 0),
            )
            .put("_victoriousId", ex.id)
            .put("_host", ex.host)
            .put("_blocked", ex.blocked?.name ?: JSONObject.NULL)
    }

    private fun strip(headers: List<CapturedHeader>): List<CapturedHeader> =
        headers.filterNot { SecretStripper.isSensitiveHeader(it.name) }

    private fun headersJson(headers: List<CapturedHeader>): JSONArray {
        val arr = JSONArray()
        headers.forEach { h ->
            arr.put(JSONObject().put("name", h.name).put("value", h.value))
        }
        return arr
    }

    private fun queryString(url: String): JSONArray {
        val arr = JSONArray()
        SecretStripper.parseQuery(url).forEach { (name, value) ->
            if (SecretStripper.isSensitiveParam(name)) return@forEach
            arr.put(
                JSONObject()
                    .put("name", name)
                    .put("value", value),
            )
        }
        return arr
    }
}
