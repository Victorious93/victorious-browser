package com.victorious.browser.browser

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.net.toUri
import com.victorious.browser.inspect.CaptureStore
import com.victorious.browser.inspect.CapturedExchange
import com.victorious.browser.inspect.CapturedHeader
import com.victorious.browser.inspect.ContentBlocker
import com.victorious.browser.inspect.ContentBlockerSettings
import com.victorious.browser.inspect.LocalProxyService
import com.victorious.browser.userscript.UserScriptInjector
import com.victorious.browser.userscript.UserScriptStore
import com.victorious.browser.webext.ExtensionEngine
import com.victorious.browser.webext.RunAt
import com.victorious.browser.netid.NetIdSettings
import com.victorious.browser.netid.SpoofInjector
import com.victorious.browser.antiphish.AntiPhishController

/**
 * Keeps http/https navigation inside the WebView (no external browser kick-out)
 * and reports URL / loading state back to Compose.
 *
 * Content-blocker fallback: [CaptureFiltersSource][com.victorious.browser.inspect.CaptureFiltersSource]
 * blocks at the proxy when it is running. [shouldInterceptRequest] mirrors the same
 * check here so blocking still works with the proxy off — recording a blocked
 * [CapturedExchange] either way, never a silent drop.
 *
 * User-script fallback: [UserScriptInjector.installAll] handles document-start injection
 * when the WebView supports it. [onPageStarted] covers WebViews that don't — see
 * [UserScriptInjector]'s doc for why that path runs later than true document-start.
 */
class VictoriousWebViewClient(
    private val onUrlChanged: (String) -> Unit,
    private val onLoadingChanged: (Boolean) -> Unit,
) : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        if (LocalProxyService.isRunning.value) return null // proxy already handles blocking
        val host = request.url?.host ?: return null
        val settings = ContentBlockerSettings.get(view.context.applicationContext)
        val category = ContentBlocker.isBlocked(host, settings.activeCategories()) ?: return null

        CaptureStore.record(
            CapturedExchange(
                id = CaptureStore.nextId(),
                method = request.method ?: "GET",
                url = request.url.toString(),
                host = host,
                scheme = request.url?.scheme ?: "https",
                requestHeaders = request.requestHeaders.orEmpty().map { (k, v) -> CapturedHeader(k, v) },
                requestStartedAt = System.currentTimeMillis(),
                statusCode = 403,
                blocked = category,
            ),
        )
        return WebResourceResponse("text/plain", "utf-8", null)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url ?: return false
        val scheme = uri.scheme?.lowercase()
        return if (scheme == "http" || scheme == "https") {
            // Load inside this WebView.
            false
        } else {
            // Non-http(s) schemes (intent:, market:, tel:, …) — ignore for now.
            true
        }
    }

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        val lower = url.lowercase()
        return if (lower.startsWith("http://") || lower.startsWith("https://")) {
            false
        } else {
            true
        }
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onLoadingChanged(true)
        if (!url.isNullOrBlank()) onUrlChanged(url)

        val host = url?.toUri()?.host
        val appCtx = view.context.applicationContext
        val store = UserScriptStore.get(appCtx)
        UserScriptInjector.injectFallbackOnPageStarted(view, host, store.scripts.value)
        SpoofInjector.injectFallbackOnPageStarted(view, NetIdSettings.get(appCtx))
        // Userscript-style extensions: document-start best-effort (see ExtensionEngine).
        ExtensionEngine.inject(view, url, RunAt.DOCUMENT_START)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        onLoadingChanged(false)
        if (!url.isNullOrBlank()) onUrlChanged(url)
        // Passive anti-phish scoring (no-op when detection is disabled).
        AntiPhishController.get(view.context).onPageFinished(view, url)
        ExtensionEngine.inject(view, url, RunAt.DOCUMENT_END)
        ExtensionEngine.inject(view, url, RunAt.DOCUMENT_IDLE)
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        if (!url.isNullOrBlank()) onUrlChanged(url)
    }
}
