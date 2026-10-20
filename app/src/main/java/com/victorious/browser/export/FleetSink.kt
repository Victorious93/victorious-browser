package com.victorious.browser.export

/**
 * A generic "fleet sink": a destination the normalized export payload can be
 * pushed to. Implementations: [HttpFleetSink] (POST JSON to a user-configured
 * fleet tool web API) and [IntentClipboardSink] (share-sheet / clipboard
 * fallback). New sinks (e.g. a bound-service sink) can implement this interface
 * without touching callers.
 */
interface FleetSink {
    val id: String
    val label: String

    data class Result(
        val ok: Boolean,
        val detail: String,
        val httpStatus: Int? = null,
    )

    /** Push the serialized payload. Blocking; call off the main thread. */
    fun send(payload: FleetExportPayload): Result
}
