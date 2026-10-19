package com.victorious.browser.userscript

import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Injects [UserScript]s into a WebView.
 *
 * Preferred path: [WebViewCompat.addDocumentStartJavaScript] (WebView 126+, gated by
 * [WebViewFeature.DOCUMENT_START_SCRIPT]) — runs before the page's own scripts, scoped
 * to an allow-list of origins built from each script's host pattern. Installed once per
 * WebView instance; a script added or edited after that point takes effect on the next
 * time the browser screen (and its WebView) is recreated, not immediately.
 *
 * Fallback path (older WebViews): [injectFallbackOnPageStarted] runs matching scripts via
 * `evaluateJavascript` from [com.victorious.browser.browser.VictoriousWebViewClient.onPageStarted].
 * This is NOT document-start — the page's own scripts may already be running — so a script
 * that assumes it executes first (e.g. to patch an API before the page calls it) can be too
 * late on this path. There is no earlier hook available without document-start support.
 */
object UserScriptInjector {
    fun isDocumentStartSupported(): Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    /** Origin-match rules (scheme://host, both with and without a wildcard subdomain). */
    fun originRulesFor(hostPattern: String): Set<String> {
        val bare = UserScriptMatcher.bareHost(hostPattern)
        if (bare.isBlank()) return emptySet()
        return setOf(
            "https://$bare",
            "https://*.$bare",
            "http://$bare",
            "http://*.$bare",
        )
    }

    /**
     * Installs every enabled script via document-start injection. No-op (returns an
     * empty list) when [isDocumentStartSupported] is false — callers should rely on
     * [injectFallbackOnPageStarted] instead in that case.
     */
    fun installAll(webView: WebView, scripts: List<UserScript>) {
        if (!isDocumentStartSupported()) return
        scripts.filter { it.enabled }.forEach { script ->
            val rules = originRulesFor(script.hostPattern)
            if (rules.isNotEmpty()) {
                WebViewCompat.addDocumentStartJavaScript(webView, script.code, rules)
            }
        }
    }

    /** Fallback for WebViews without document-start support; call from `onPageStarted`. */
    fun injectFallbackOnPageStarted(webView: WebView, host: String?, scripts: List<UserScript>) {
        if (isDocumentStartSupported()) return
        scripts.filter { it.enabled && UserScriptMatcher.matches(host, it.hostPattern) }
            .forEach { script -> webView.evaluateJavascript(script.code, null) }
    }
}
