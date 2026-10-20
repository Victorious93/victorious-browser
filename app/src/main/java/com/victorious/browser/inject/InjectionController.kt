package com.victorious.browser.inject

import android.webkit.WebView

/** Android-side glue for [InjectionTools]: runs code against a live [WebView]. */
object InjectionController {
    /** Runs [code] as-is in the page and hands the raw `evaluateJavascript` callback value back. */
    fun runJavaScript(webView: WebView, code: String, onResult: (String?) -> Unit) {
        webView.evaluateJavascript(code, onResult)
    }

    /** Inserts [html] into the current page and reports the outcome. */
    fun injectHtml(webView: WebView, html: String, onResult: (InjectionTools.InjectionOutcome) -> Unit) {
        webView.evaluateJavascript(InjectionTools.buildHtmlInjectionScript(html)) { raw ->
            onResult(InjectionTools.parseInjectionResult(raw))
        }
    }
}
