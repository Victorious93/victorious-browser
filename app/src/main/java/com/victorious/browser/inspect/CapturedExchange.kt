package com.victorious.browser.inspect

/**
 * A single captured HTTP(S) exchange from the app's own in-app WebView.
 *
 * Request and response bodies are aggregated by the proxy up to
 * [CaptureConfig.maxBodyBytes]; when the wire body is larger the captured
 * bytes are truncated and the matching `*BodyTruncated` flag is set.
 */
data class CapturedHeader(val name: String, val value: String)

data class CapturedExchange(
    val id: Long,
    val method: String,
    val url: String,
    val host: String,
    val scheme: String,
    val requestHeaders: List<CapturedHeader>,
    val requestStartedAt: Long,
    // Filled in when the response is seen.
    var statusCode: Int? = null,
    var responseHeaders: List<CapturedHeader> = emptyList(),
    var responseReceivedAt: Long? = null,
    var requestBytes: Long = 0,
    var responseBytes: Long = 0,
    var requestBody: ByteArray? = null,
    var responseBody: ByteArray? = null,
    /** True when [requestBody] was cut to the capture size cap. */
    var requestBodyTruncated: Boolean = false,
    /** True when [responseBody] was cut to the capture size cap. */
    var responseBodyTruncated: Boolean = false,
) {
    val durationMs: Long?
        get() = responseReceivedAt?.let { it - requestStartedAt }

    fun requestContentType(): String? = contentTypeOf(requestHeaders)

    fun responseContentType(): String? = contentTypeOf(responseHeaders)

    companion object {
        fun contentTypeOf(headers: List<CapturedHeader>): String? =
            headers.firstOrNull { it.name.equals("Content-Type", ignoreCase = true) }?.value
    }
}
