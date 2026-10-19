package com.victorious.browser.reader

import android.content.Context
import android.webkit.WebView
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Android-side glue for [ReaderMode]: loads the vendored Readability.js once per
 * process, runs extraction on a live [WebView], and swaps the WebView's content for
 * the rendered article (or restores the original URL to exit).
 */
object ReaderModeController {
    private const val ASSET_PATH = "readability.js"

    @Volatile private var cachedSource: String? = null

    private fun readabilitySource(context: Context): String {
        cachedSource?.let { return it }
        val text = context.assets.open(ASSET_PATH).use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
        }
        cachedSource = text
        return text
    }

    /**
     * Extracts and renders the article from [webView]'s current page, replacing it in
     * place via `loadDataWithBaseURL`. [onResult] is called with `true` if an article
     * was found and rendered, `false` if extraction found nothing (caller should tell
     * the user reader mode isn't available for this page, not toggle silently).
     */
    fun enter(webView: WebView, dark: Boolean, onResult: (Boolean) -> Unit) {
        val context = webView.context.applicationContext
        val script = ReaderMode.buildExtractionScript(readabilitySource(context))
        val baseUrl = webView.url
        webView.evaluateJavascript(script) { rawResult ->
            val article = ReaderMode.parseExtractionResult(rawResult)
            if (article == null) {
                onResult(false)
                return@evaluateJavascript
            }
            val html = ReaderMode.renderDocument(article, dark)
            webView.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", baseUrl)
            onResult(true)
        }
    }
}
