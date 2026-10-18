package com.victorious.browser.inspect

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Exports captured endpoints/requests to a JSON file for later handoff to the
 * owner's other first-party analysis tools. This just produces the export file;
 * no upload/handoff is wired here.
 */
object CaptureExporter {

    fun toJson(exchanges: List<CapturedExchange>): String {
        val root = JSONObject()
        root.put("tool", "victorious-browser.network-inspection")
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        val arr = JSONArray()
        for (ex in exchanges) {
            arr.put(exchangeToJson(ex))
        }
        root.put("entries", arr)
        return root.toString(2)
    }

    /** Write the export to the app's external files dir and return the file. */
    fun writeToFile(context: Context, exchanges: List<CapturedExchange>): File {
        val dir = context.getExternalFilesDir("exports") ?: context.filesDir
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "network-capture-${System.currentTimeMillis()}.json")
        file.writeText(toJson(exchanges))
        return file
    }

    private fun exchangeToJson(ex: CapturedExchange): JSONObject {
        val o = JSONObject()
        o.put("id", ex.id)
        o.put("method", ex.method)
        o.put("url", ex.url)
        o.put("host", ex.host)
        o.put("scheme", ex.scheme)
        o.put("statusCode", ex.statusCode ?: JSONObject.NULL)
        o.put("requestStartedAt", ex.requestStartedAt)
        o.put("responseReceivedAt", ex.responseReceivedAt ?: JSONObject.NULL)
        o.put("durationMs", ex.durationMs ?: JSONObject.NULL)
        o.put("requestBytes", ex.requestBytes)
        o.put("responseBytes", ex.responseBytes)
        o.put("requestHeaders", headersToJson(ex.requestHeaders))
        o.put("responseHeaders", headersToJson(ex.responseHeaders))

        // Inspection helpers baked into the export so downstream tools get them free.
        val inspect = JSONObject()
        val cookies = JSONArray()
        InspectHelpers.cookies(ex.requestHeaders + ex.responseHeaders).forEach { c ->
            cookies.put(JSONObject().put("name", c.name).put("value", c.value))
        }
        inspect.put("cookies", cookies)
        val bearer = InspectHelpers.bearerToken(ex.requestHeaders)
        inspect.put("bearer", bearer ?: JSONObject.NULL)
        val jwt = InspectHelpers.decodeJwt(bearer)
        if (jwt != null) {
            inspect.put(
                "jwt",
                JSONObject()
                    .put("header", jwt.header ?: JSONObject.NULL)
                    .put("payload", jwt.payload ?: JSONObject.NULL)
                    .put("valid", jwt.valid),
            )
        }
        o.put("inspection", inspect)
        return o
    }

    private fun headersToJson(headers: List<CapturedHeader>): JSONArray {
        val a = JSONArray()
        headers.forEach { h -> a.put(JSONObject().put("name", h.name).put("value", h.value)) }
        return a
    }
}
