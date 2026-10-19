package com.victorious.browser.inspect

import android.util.Log
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.concurrent.Executors

/**
 * Rebuilds an HTTP(S) request from an [EditableRequest] (typically derived from
 * a [CaptureStore] entry), fires it, and ensures the exchange is recorded.
 *
 * When the local inspection proxy is running and [throughProxy] is true, the
 * request is sent via `127.0.0.1:<port>` so [CaptureFiltersSource] records it
 * like any other capture. A marker header skips the intercept gate so replay
 * never deadlocks the UI. In that mode this client does **not** pre-record
 * (avoids duplicate history rows).
 *
 * When the proxy is off (or [throughProxy] is false), this client records the
 * exchange directly into [CaptureStore].
 */
object ReplayClient {
    private const val TAG = "ReplayClient"
    private val executor = Executors.newCachedThreadPool()

    data class ReplayResult(
        val ok: Boolean,
        val statusCode: Int? = null,
        val responseHeaders: List<CapturedHeader> = emptyList(),
        val responseBody: ByteArray? = null,
        val error: String? = null,
        val recordedExchangeId: Long? = null,
    )

    /**
     * Fire [edited] on a background thread and invoke [onDone] on that same
     * worker thread (caller should hop to main for UI).
     */
    fun replayAsync(
        edited: EditableRequest,
        throughProxy: Boolean = true,
        onDone: (ReplayResult) -> Unit,
    ) {
        executor.execute {
            onDone(replay(edited, throughProxy))
        }
    }

    fun replay(edited: EditableRequest, throughProxy: Boolean = true): ReplayResult {
        val prepared = prepareForWire(edited)
        val useProxy = throughProxy && LocalProxyService.isRunning.value
        val exchangeId = if (!useProxy) {
            val id = CaptureStore.nextId()
            val started = System.currentTimeMillis()
            CaptureStore.record(
                CapturedExchange(
                    id = id,
                    method = prepared.method,
                    url = prepared.url,
                    host = RequestEditMerge.hostPortFromUrl(prepared.url)?.substringBefore(':')
                        ?: "",
                    scheme = schemeOf(prepared.url),
                    requestHeaders = prepared.headers.filterNot {
                        it.name.equals(InterceptController.REPLAY_MARKER_HEADER, ignoreCase = true)
                    },
                    requestStartedAt = started,
                    requestBytes = prepared.body?.size?.toLong() ?: 0,
                    requestBody = prepared.body?.copyOf(),
                ),
            )
            id
        } else {
            null
        }

        return try {
            val conn = openConnection(prepared.url, useProxy)
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.requestMethod = prepared.method
            conn.doInput = true
            for (h in prepared.headers) {
                if (h.name.equals("Host", ignoreCase = true)) continue
                if (h.name.equals("Content-Length", ignoreCase = true)) continue
                conn.setRequestProperty(h.name, h.value)
            }
            val body = prepared.body
            if (body != null &&
                !prepared.method.equals("GET", true) &&
                !prepared.method.equals("HEAD", true)
            ) {
                conn.doOutput = true
                conn.setFixedLengthStreamingMode(body.size)
                conn.outputStream.use { it.write(body) }
            }
            val code = conn.responseCode
            val respHeaders = conn.headerFields
                ?.filterKeys { it != null }
                ?.flatMap { (name, values) ->
                    values.map { CapturedHeader(name!!, it) }
                }
                .orEmpty()
            val respBytes = try {
                val stream = if (code >= 400) conn.errorStream else conn.inputStream
                stream?.readBytes()
            } catch (_: IOException) {
                null
            }
            conn.disconnect()

            if (exchangeId != null) {
                CaptureStore.update(exchangeId) { ex ->
                    ex.statusCode = code
                    ex.responseHeaders = respHeaders
                    ex.responseReceivedAt = System.currentTimeMillis()
                    ex.responseBytes = respBytes?.size?.toLong()
                        ?: respHeaders.firstOrNull { it.name.equals("Content-Length", true) }
                            ?.value?.toLongOrNull()
                        ?: 0
                    ex.responseBody = respBytes
                }
            }
            ReplayResult(
                ok = true,
                statusCode = code,
                responseHeaders = respHeaders,
                responseBody = respBytes,
                recordedExchangeId = exchangeId,
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Replay failed for ${prepared.method} ${prepared.url}", t)
            if (exchangeId != null) {
                CaptureStore.update(exchangeId) { ex ->
                    ex.statusCode = -1
                    ex.responseReceivedAt = System.currentTimeMillis()
                    ex.responseHeaders = listOf(
                        CapturedHeader("X-Victorious-Error", t.message ?: t.javaClass.simpleName),
                    )
                }
            }
            ReplayResult(
                ok = false,
                error = t.message ?: t.javaClass.simpleName,
                recordedExchangeId = exchangeId,
            )
        }
    }

    /** Attach replay marker + sync Content-Length for the wire request. */
    fun prepareForWire(edited: EditableRequest): EditableRequest {
        val headers = RequestEditMerge.ensureHostHeader(edited.headers, edited.url)
            .filterNot { it.name.equals(InterceptController.REPLAY_MARKER_HEADER, ignoreCase = true) }
            .let { RequestEditMerge.syncContentLength(it, edited.body) } +
            CapturedHeader(InterceptController.REPLAY_MARKER_HEADER, "1")
        return edited.copy(headers = headers, method = edited.method.uppercase())
    }

    private fun openConnection(url: String, useProxy: Boolean): HttpURLConnection {
        val u = URL(url)
        if (useProxy) {
            val proxy = Proxy(
                Proxy.Type.HTTP,
                InetSocketAddress("127.0.0.1", LocalProxyService.port),
            )
            return u.openConnection(proxy) as HttpURLConnection
        }
        return u.openConnection() as HttpURLConnection
    }

    private fun schemeOf(url: String): String =
        try {
            URL(url).protocol ?: "http"
        } catch (_: Exception) {
            if (url.startsWith("https", true)) "https" else "http"
        }
}
