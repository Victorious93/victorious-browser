package com.victorious.browser.export

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent

/**
 * Fallback sink: share the export JSON via the Android share-sheet, or copy it to
 * the clipboard. Useful when no fleet web API endpoint is configured yet, or to
 * hand the payload to another app manually.
 */
class IntentClipboardSink(private val context: Context) : FleetSink {
    override val id = "intent"
    override val label = "Share / clipboard"

    override fun send(payload: FleetExportPayload): FleetSink.Result {
        // Default action for the generic send() path is clipboard copy; the UI
        // offers an explicit "Share" button that calls [share].
        return copyToClipboard(payload)
    }

    fun copyToClipboard(payload: FleetExportPayload): FleetSink.Result {
        return try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("fleet-export", payload.toJsonString()))
            FleetSink.Result(ok = true, detail = "Copied export JSON to clipboard.")
        } catch (t: Throwable) {
            FleetSink.Result(ok = false, detail = "Clipboard copy failed: ${t.message}")
        }
    }

    fun shareIntent(payload: FleetExportPayload): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "Victorious Browser fleet export")
            putExtra(Intent.EXTRA_TEXT, payload.toJsonString())
        }
        return Intent.createChooser(send, "Send to fleet tool")
    }
}
