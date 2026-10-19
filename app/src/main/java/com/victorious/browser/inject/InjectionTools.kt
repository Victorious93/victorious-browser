package com.victorious.browser.inject

import org.json.JSONObject

/**
 * Ad-hoc JavaScript/HTML injection into the *current* page of the operator's own
 * browsing session — a one-off "run now" action (via [com.victorious.browser.inject.InjectionController]),
 * not persisted or re-applied on reload. Distinct from
 * [com.victorious.browser.userscript.UserScript], which is a per-host script that
 * reinstalls on every matching page load.
 *
 * Unlike [com.victorious.browser.reader.ReaderMode], this intentionally does NOT
 * sanitize the HTML it injects: the whole point of an HTML-injection tool is testing
 * how the current page (typically one of the operator's own) renders and sanitizes
 * arbitrary markup, so stripping anything here would defeat the tool.
 */
object InjectionTools {
    /**
     * Builds a script that inserts [html] verbatim at the end of `document.body` and
     * reports success/failure as a JSON string, the same double-encoding shape
     * `evaluateJavascript`'s callback uses elsewhere in this codebase (see
     * [com.victorious.browser.reader.ReaderMode.parseExtractionResult]'s doc).
     */
    fun buildHtmlInjectionScript(html: String): String {
        // JSONObject.quote returns a properly escaped JS/JSON string literal, including
        // its own surrounding quotes — safe to splice directly into the script text.
        val encoded = JSONObject.quote(html)
        return """
            (function() {
                try {
                    document.body.insertAdjacentHTML("beforeend", $encoded);
                    return JSON.stringify({ ok: true });
                } catch (e) {
                    return JSON.stringify({ ok: false, error: String(e) });
                }
            })();
        """.trimIndent()
    }

    data class InjectionOutcome(val ok: Boolean, val error: String?)

    /** Decodes the double-encoded result from [buildHtmlInjectionScript]'s callback value. */
    fun parseInjectionResult(callbackValue: String?): InjectionOutcome {
        if (callbackValue.isNullOrBlank() || callbackValue == "null") {
            return InjectionOutcome(ok = false, error = "No response from page")
        }
        return try {
            val jsonString = JSONObject("{\"v\":$callbackValue}").getString("v")
            val obj = JSONObject(jsonString)
            val error = if (obj.has("error") && !obj.isNull("error")) obj.optString("error") else null
            InjectionOutcome(ok = obj.optBoolean("ok", false), error = error)
        } catch (e: Exception) {
            InjectionOutcome(ok = false, error = e.message)
        }
    }
}
