package com.victorious.browser.browser

import android.annotation.SuppressLint
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebViewFeature

/**
 * Applies recon-oriented WebView defaults.
 *
 * Mixed content: [WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE] — allow mixed content
 * only in the compatibility path so HTTPS pages can still load legacy HTTP subresources
 * during recon (common on internal / poorly-configured targets). Prefer upgrading to
 * [WebSettings.MIXED_CONTENT_ALWAYS_ALLOW] only if a specific target requires it; never
 * use that as a silent default for general browsing.
 */
@SuppressLint("SetJavaScriptEnabled")
fun applyVictoriousWebSettings(webView: WebView, desktopUserAgent: Boolean) {
    val settings = webView.settings
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.javaScriptCanOpenWindowsAutomatically = false
    settings.setSupportMultipleWindows(false)
    settings.loadWithOverviewMode = true
    settings.useWideViewPort = true
    settings.builtInZoomControls = true
    settings.displayZoomControls = false
    settings.mediaPlaybackRequiresUserGesture = true
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

    val defaultUa = settings.userAgentString.orEmpty()
    settings.userAgentString = if (desktopUserAgent) {
        // Force a desktop Chrome UA while keeping the WebView token recognizable.
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Safari/537.36 VictoriousBrowser/0.1"
    } else {
        // Strip "wv" markers that some sites treat as in-app WebView and degrade.
        val cleaned = defaultUa.replace("; wv", "").replace("Version/4.0 ", "")
        if ("VictoriousBrowser" in cleaned) cleaned else "$cleaned VictoriousBrowser/0.1"
    }

    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

    // TODO(proxy): Attach the local intercepting proxy here via androidx.webkit
    // ProxyController once the proxy library pick is approved. Feature-gate with
    // WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE) before calling
    // ProxyController.getInstance().setProxyOverride(...). Do not implement the proxy
    // in the browser-shell PR — this is the intentional hook point.
    if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
        // Intentionally empty — reserved for ProxyController integration.
        @Suppress("UNUSED_VARIABLE")
        val proxyHookReady = true
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        settings.safeBrowsingEnabled = false // recon tool: do not phone home / block targets
    }
}
