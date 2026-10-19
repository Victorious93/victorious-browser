package com.victorious.browser.inspect

/**
 * A single captured HTTP(S) exchange from the app's own in-app WebView.
 *
 * Modeled so request/response *bodies* can be added later without a schema change:
 * [requestBody]/[responseBody] are nullable placeholders that the current capture
 * path leaves null (only metadata + headers are captured in this layer).
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
    // Deferred: body capture depth. Reserved so bodies can be added later.
    var requestBody: ByteArray? = null,
    var responseBody: ByteArray? = null,
    // Set when ContentBlocker matched this host; the request never reached upstream.
    // Recorded here (rather than silently dropped) so the content blocker never hides
    // traffic from the inspection history it shares a code path with.
    var blocked: BlockCategory? = null,
) {
    val durationMs: Long?
        get() = responseReceivedAt?.let { it - requestStartedAt }
}
