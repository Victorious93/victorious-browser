package com.victorious.browser.inspect

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Base64

/**
 * Exports captured endpoints/requests to a JSON file for later handoff to the
 * owner's other first-party analysis tools. This just produces the export file;
 * no upload/handoff is wired here.
 */
object CaptureExporter {

    fun toJson(exchanges: List<CapturedExchange>): String {
        val root = JSONObject()
        root.put("tool", "victorious-browser.network-inspection")
        root.put("version", 2)
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
        o.put("requestContentType", ex.requestContentType() ?: JSONObject.NULL)
        o.put("responseContentType", ex.responseContentType() ?: JSONObject.NULL)
        o.put("requestBodyTruncated", ex.requestBodyTruncated)
        o.put("responseBodyTruncated", ex.responseBodyTruncated)
        o.put("requestHeaders", headersToJson(ex.requestHeaders))
        o.put("responseHeaders", headersToJson(ex.responseHeaders))
        o.put("requestBodyBase64", bodyB64(ex.requestBody))
        o.put("responseBodyBase64", bodyB64(ex.responseBody))

        val inspect = JSONObject()
        val cookies = JSONArray()
        InspectHelpers.parseCookies(ex.requestHeaders + ex.responseHeaders).forEach { c ->
            val attrs = JSONObject()
            c.attributes.forEach { (k, v) -> attrs.put(k, v) }
            cookies.put(
                JSONObject()
                    .put("name", c.name)
                    .put("value", c.value)
                    .put("source", c.source)
                    .put("attributes", attrs),
            )
        }
        inspect.put("cookies", cookies)

        val jwts = JSONArray()
        InspectHelpers.findJwts(
            requestHeaders = ex.requestHeaders,
            responseHeaders = ex.responseHeaders,
            requestBody = ex.requestBody,
            responseBody = ex.responseBody,
            requestContentType = ex.requestContentType(),
            responseContentType = ex.responseContentType(),
        ).forEach { jwt ->
            val claims = JSONObject()
            jwt.claims.forEach { (k, v) -> claims.put(k, v) }
            jwts.put(
                JSONObject()
                    .put("source", jwt.source)
                    .put("header", jwt.header ?: JSONObject.NULL)
                    .put("payload", jwt.payload ?: JSONObject.NULL)
                    .put("decodedOk", jwt.decodedOk)
                    .put("signatureVerified", false)
                    .put("note", "decoded-not-verified")
                    .put("claims", claims),
            )
        }
        inspect.put("jwts", jwts)
        // Back-compat single bearer field
        val bearer = InspectHelpers.bearerToken(ex.requestHeaders)
        inspect.put("bearer", bearer ?: JSONObject.NULL)
        o.put("inspection", inspect)
        return o
    }

    private fun bodyB64(body: ByteArray?): Any =
        if (body == null) JSONObject.NULL else Base64.getEncoder().encodeToString(body)

    private fun headersToJson(headers: List<CapturedHeader>): JSONArray {
        val a = JSONArray()
        headers.forEach { h -> a.put(JSONObject().put("name", h.name).put("value", h.value)) }
        return a
    }
}
