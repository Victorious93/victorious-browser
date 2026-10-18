package com.victorious.browser.inspect

import java.net.URI

/**
 * Pure request-builder / edit-merge helpers for intercept-and-edit and replay.
 *
 * Kept free of Netty so [RequestEditMergeTest] can run as JVM unit tests.
 */
object RequestEditMerge {

    private val HOP_BY_HOP = setOf(
        "connection",
        "keep-alive",
        "proxy-authenticate",
        "proxy-authorization",
        "te",
        "trailers",
        "transfer-encoding",
        "upgrade",
        "proxy-connection",
    )

    /** Build an editable snapshot from a captured history entry. */
    fun fromExchange(ex: CapturedExchange): EditableRequest {
        return EditableRequest(
            method = ex.method,
            url = ex.url,
            headers = stripHopByHop(ex.requestHeaders),
            body = ex.requestBody?.copyOf(),
        )
    }

    /**
     * Merge UI edits into [base]. Empty optional strings mean "leave unchanged"
     * except [headersText] / [bodyText], which always replace when non-null.
     *
     * @param headersText newline-separated `Name: Value` lines; null keeps base headers
     * @param bodyText UTF-8 body; null keeps base body; empty string clears body
     */
    fun merge(
        base: EditableRequest,
        method: String? = null,
        url: String? = null,
        headersText: String? = null,
        bodyText: String? = null,
    ): EditableRequest {
        val newMethod = method?.trim()?.takeIf { it.isNotEmpty() }?.uppercase() ?: base.method
        val newUrl = url?.trim()?.takeIf { it.isNotEmpty() } ?: base.url
        val newHeaders = if (headersText != null) {
            ensureHostHeader(parseHeadersText(headersText), newUrl)
        } else {
            ensureHostHeader(base.headers, newUrl)
        }
        val newBody: ByteArray? = when {
            bodyText == null -> base.body
            bodyText.isEmpty() -> null
            else -> bodyText.toByteArray(Charsets.UTF_8)
        }
        val withLength = syncContentLength(newHeaders, newBody)
        return EditableRequest(
            method = newMethod,
            url = newUrl,
            headers = withLength,
            body = newBody,
        )
    }

    /** Parse `Name: Value` lines (blank lines ignored; lines without `:` skipped). */
    fun parseHeadersText(text: String): List<CapturedHeader> {
        val out = mutableListOf<CapturedHeader>()
        for (raw in text.split('\n')) {
            val line = raw.trimEnd('\r').trim()
            if (line.isEmpty()) continue
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val name = line.substring(0, colon).trim()
            val value = line.substring(colon + 1).trim()
            if (name.isNotEmpty()) {
                out += CapturedHeader(name, value)
            }
        }
        return stripHopByHop(out)
    }

    fun formatHeaders(headers: List<CapturedHeader>): String =
        headers.joinToString("\n") { "${it.name}: ${it.value}" }

    fun stripHopByHop(headers: List<CapturedHeader>): List<CapturedHeader> =
        headers.filterNot { it.name.lowercase() in HOP_BY_HOP }

    /** Drop any existing Host header and set one derived from [url]. */
    fun ensureHostHeader(headers: List<CapturedHeader>, url: String): List<CapturedHeader> {
        val host = hostPortFromUrl(url) ?: return stripHopByHop(headers)
        val without = stripHopByHop(headers).filterNot { it.name.equals("Host", ignoreCase = true) }
        return listOf(CapturedHeader("Host", host)) + without
    }

    /**
     * Set or remove Content-Length to match [body]. Also drops Transfer-Encoding
     * so a replay/edit cannot leave a chunked framing mismatch.
     */
    fun syncContentLength(headers: List<CapturedHeader>, body: ByteArray?): List<CapturedHeader> {
        val cleaned = stripHopByHop(headers).filterNot {
            it.name.equals("Content-Length", ignoreCase = true) ||
                it.name.equals("Transfer-Encoding", ignoreCase = true)
        }
        return if (body != null) {
            cleaned + CapturedHeader("Content-Length", body.size.toString())
        } else {
            cleaned
        }
    }

    fun hostPortFromUrl(url: String): String? {
        return try {
            val uri = URI(url)
            val host = uri.host ?: return null
            val port = uri.port
            if (port > 0) "$host:$port" else host
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Origin-form path+query for proxy forwarding when the upstream expects
     * origin-form (typical after MITM decrypt). Absolute-form when [preferAbsolute].
     */
    fun requestUriForProxy(url: String, preferAbsolute: Boolean): String {
        if (preferAbsolute) return url
        return try {
            val uri = URI(url)
            val path = uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/"
            val q = uri.rawQuery
            if (q.isNullOrEmpty()) path else "$path?$q"
        } catch (_: Exception) {
            url
        }
    }

    fun isConnect(method: String): Boolean = method.equals("CONNECT", ignoreCase = true)
}
