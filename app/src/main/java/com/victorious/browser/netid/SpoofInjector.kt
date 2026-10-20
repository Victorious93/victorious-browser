package com.victorious.browser.netid

import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.victorious.browser.userscript.UserScriptInjector

/**
 * Installs geolocation + fingerprint JS shims into a WebView.
 *
 * Prefer document-start (same path as user scripts). Fall back to evaluateJavascript
 * from onPageStarted when DOCUMENT_START_SCRIPT is unavailable — late, but still useful
 * for pages that call geolocation / fingerprint APIs after load.
 */
object SpoofInjector {
    fun installDocumentStart(webView: WebView, settings: NetIdSettings) {
        if (!UserScriptInjector.isDocumentStartSupported() &&
            !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        ) return
        val geoJs = GeoSpoof.shim(settings.geo.value)
        val fpJs = FingerprintShim.generate(settings.fingerprint.value)
        val combined = listOf(geoJs, fpJs).filter { it.isNotBlank() }.joinToString("\n")
        if (combined.isBlank()) return
        // "*" matches every origin (Chromium document-start rules).
        try {
            WebViewCompat.addDocumentStartJavaScript(webView, combined, setOf("*"))
        } catch (_: Throwable) {
            // Older WebView may reject — onPageStarted fallback covers it.
        }
    }

    fun injectFallbackOnPageStarted(webView: WebView, settings: NetIdSettings) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        val geoJs = GeoSpoof.shim(settings.geo.value)
        val fpJs = FingerprintShim.generate(settings.fingerprint.value)
        if (geoJs.isNotBlank()) webView.evaluateJavascript(geoJs, null)
        if (fpJs.isNotBlank()) webView.evaluateJavascript(fpJs, null)
    }

    /** Applies per-session UA override from [FingerprintConfig]; no-op when DEFAULT. */
    fun applyUserAgent(webView: WebView, config: FingerprintConfig) {
        val ua = UaPresets.resolve(config.uaPreset, config.customUa) ?: return
        webView.settings.userAgentString = ua
    }
}
