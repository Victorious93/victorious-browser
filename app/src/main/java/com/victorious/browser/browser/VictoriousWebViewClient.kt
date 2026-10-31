package com.victorious.browser.browser

import android.graphics.Bitmap
import android.webkit.WebResourceError
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
import android.net.http.SslError
import android.webkit.SslErrorHandler
import com.victorious.browser.browser.privacy.Gpc
import com.victorious.browser.browser.privacy.HostUtil
import com.victorious.browser.browser.privacy.PrivacyInjector
import com.victorious.browser.browser.privacy.PrivacyNav
import com.victorious.browser.browser.privacy.PrivacySettings
import com.victorious.browser.browser.privacy.PrivacyStats
import com.victorious.browser.browser.privacy.ReferrerTrim
import com.victorious.browser.browser.privacy.TrackerCategory
import com.victorious.browser.browser.privacy.TrackerHit
import com.victorious.browser.browser.privacy.TrackerList
import com.victorious.browser.inspect.BlockCategory
import java.util.Collections

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
    private val onPageError: (url: String, description: String, errorCode: Int) -> Unit = { _, _, _ -> },
    /** Tab id for the Privacy Dashboard; null disables privacy protections (legacy single-tab screen). */
    private val tabId: String? = null,
    private val privacy: PrivacySettings? = null,
    /** JS installed at document start by [PrivacyInjector.installDocumentStart]. */
    private val privacyStartJs: String = "",
) : WebViewClient() {

    /** Host of the top-level page, read from the network thread in [shouldInterceptRequest]. */
    @Volatile private var pageHost: String? = null
    /** Host we just force-upgraded to https; on failure we fall back to http once. */
    @Volatile private var upgradedHost: String? = null

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val host = request.url?.host ?: return null
        val p = privacy
        if (p != null && tabId != null && !request.isForMainFrame) {
            // Third-party tracker: always counted for the dashboard; blocked only when the
            // protection is ON and the page isn't on the per-site exception list.
            val page = pageHost
            val cat = TrackerList.shouldBlock(host, page, emptySet())
            if (cat != null) {
                val block = p.blockTrackers.value && !p.isAllowed(page)
                PrivacyStats.tracker(tabId, TrackerHit(request.url.toString(), host.lowercase(), cat, block))
                if (block) return blockedResponse(request, host, if (cat == TrackerCategory.CRYPTOMINING) BlockCategory.COIN_MINERS else BlockCategory.TRACKERS)
            }
        }
        if (LocalProxyService.isRunning.value) return null // proxy already handles blocking
        val settings = ContentBlockerSettings.get(view.context.applicationContext)
        val category = ContentBlocker.isBlocked(host, settings.activeCategories()) ?: return null
        return blockedResponse(request, host, category)
    }

    /** Record a blocked exchange (never a silent drop) and return an empty body. */
    private fun blockedResponse(request: WebResourceRequest, host: String, category: BlockCategory): WebResourceResponse {
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
            // Privacy protections: re-issue top-level GET navigations that need a rewrite
            // (tracking params / https) or extra headers (GPC / trimmed Referer).
            val p = privacy
            if (p != null && tabId != null && request.isForMainFrame && !request.isRedirect &&
                (request.method ?: "GET").equals("GET", ignoreCase = true)
            ) {
                val url = uri.toString()
                val rw = PrivacyNav.rewrite(url, p.stripParams.value, p.forceHttps.value, failedHttpsHosts)
                val wantHeaders = p.gpc.value || p.trimReferrer.value
                if (rw.changed || wantHeaders) {
                    PrivacyStats.navRewrite(tabId, rw.httpsUpgraded, rw.strippedParams)
                    if (rw.httpsUpgraded) upgradedHost = HostUtil.hostOf(rw.url)
                    view.loadUrl(rw.url, headersFor(p, view.url, rw.url))
                    return true
                }
            }
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
        pageHost = host?.lowercase()
        if (tabId != null && privacy != null && !url.isNullOrBlank()) {
            PrivacyStats.newPage(tabId, url)
            PrivacyInjector.injectOnPageStarted(view, privacy, privacyStartJs)
        }
        if (upgradedHost != null && upgradedHost != pageHost) upgradedHost = null
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
        val p = privacy
        if (p != null && tabId != null && p.cookiePopups.value && !url.isNullOrBlank() && url.startsWith("http")) {
            PrivacyInjector.runCookiePopups(view, view.context) { PrivacyStats.cookiePopup(tabId) }
        }
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        if (!url.isNullOrBlank()) onUrlChanged(url)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest?,
        error: WebResourceError?,
    ) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame != true) return
        if (fallBackToHttp(view, request.url?.toString())) return
        onPageError(
            request.url?.toString().orEmpty(),
            error?.description?.toString() ?: "error",
            error?.errorCode ?: 0,
        )
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        // Only intervene for a page WE upgraded: cancel and fall back to plain http.
        if (fallBackToHttp(view, error.url)) {
            handler.cancel()
            return
        }
        super.onReceivedSslError(view, handler, error)
    }

    /** If [url] is an https page we force-upgraded and it failed, remember the host and load http. */
    private fun fallBackToHttp(view: WebView, url: String?): Boolean {
        val host = HostUtil.hostOf(url) ?: return false
        if (host != upgradedHost || url == null || !url.startsWith("https://")) return false
        upgradedHost = null
        failedHttpsHosts.add(host)
        view.post { view.loadUrl("http://" + url.removePrefix("https://")) }
        return true
    }

    companion object {
        /** Hosts whose forced https upgrade failed this session (not persisted). */
        val failedHttpsHosts: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

        /** Extra headers for a privacy-protected top-level load. */
        fun headersFor(p: PrivacySettings, fromUrl: String?, toUrl: String): Map<String, String> {
            val h = LinkedHashMap(Gpc.headers(p.gpc.value))
            if (p.trimReferrer.value) ReferrerTrim.trim(fromUrl, toUrl)?.let { h["Referer"] = it }
            return h
        }

        /**
         * Address-bar / app-initiated load with protections applied (bangs are resolved
         * by the caller). Returns the URL actually loaded.
         */
        fun privacyLoad(view: WebView, url: String, tabId: String, p: PrivacySettings): String {
            val rw = PrivacyNav.rewrite(url, p.stripParams.value, p.forceHttps.value, failedHttpsHosts)
            PrivacyStats.navRewrite(tabId, rw.httpsUpgraded, rw.strippedParams)
            (view.webViewClient as? VictoriousWebViewClient)?.let { if (rw.httpsUpgraded) it.upgradedHost = HostUtil.hostOf(rw.url) }
            // Typed navigations carry no Referer, so only GPC applies here.
            view.loadUrl(rw.url, Gpc.headers(p.gpc.value))
            return rw.url
        }
    }
}
