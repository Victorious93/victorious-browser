package com.victorious.browser.browser.privacy

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import com.victorious.browser.browser.BrowserRecon
import com.victorious.browser.browser.HistoryStore
import com.victorious.browser.browser.SiteData
import com.victorious.browser.browser.TabSessionStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Executes a Fire run on the Android side using [FirePlan] decisions.
 *
 * Never touches: bookmarks, downloads, the proxy capture/HAR store (recon evidence),
 * user scripts / extensions, or 1st-party cookies + storage of fireproofed sites.
 *
 * Android limits (honest): CookieManager has no per-domain enumeration, so an
 * "everywhere" burn saves the fireproofed hosts' cookie values, wipes the jar, and
 * writes them back as host-only, Path=/ cookies (HttpOnly/Secure/expiry attributes
 * are not readable and are not preserved). The HTTP cache is app-global.
 */
object FireBurner {
    /** Emitted when the UI should close tabs (scope EVERYWHERE → all tabs). */
    private val _events = MutableSharedFlow<FireOptions>(replay = 1, extraBufferCapacity = 4)
    val events: SharedFlow<FireOptions> = _events.asSharedFlow()

    fun consumeEvents() { _events.resetReplayCache() }

    /**
     * Burn data. [tabUrl]/[tabWebView] are used for [FireScope.THIS_TAB].
     * Must run on the main thread (WebView APIs). [onDone] is posted when cookies finished.
     */
    fun burn(
        context: Context,
        opts: FireOptions,
        fireproof: Set<String>,
        tabUrl: String? = null,
        tabWebView: WebView? = null,
        closeTabs: Boolean = true,
        onDone: () -> Unit = {},
    ) {
        val app = context.applicationContext
        val tabHost = HostUtil.hostOf(tabUrl)

        // History (bookmarks + downloads untouched).
        val history = HistoryStore.get(app)
        val keep = FirePlan.historyToKeep(history.entries.value.map { it.url }, fireproof, opts, tabHost).toSet()
        history.retain { it.url in keep }

        // WebStorage (localStorage / IndexedDB / etc. per origin).
        val storage = if (opts.scope == FireScope.THIS_TAB) BrowserRecon.webStorageFor(tabWebView) else WebStorage.getInstance()
        runCatching {
            storage.getOrigins { map ->
                val origins = (map as? Map<*, *>)?.keys?.mapNotNull { it?.toString() }.orEmpty()
                FirePlan.originsToBurn(origins, fireproof, opts, tabHost).forEach { storage.deleteOrigin(it) }
            }
        }
        if (opts.scope == FireScope.THIS_TAB && FirePlan.burnSiteData(tabHost, fireproof, opts)) {
            tabWebView?.evaluateJavascript(SiteData.clearStorageScript(), null)
        }

        // Site permissions (geolocation is the only one WebView persists itself).
        runCatching {
            val geo = GeolocationPermissions.getInstance()
            geo.getOrigins { set ->
                set?.map { it.toString() }?.let { FirePlan.originsToBurn(it, fireproof, opts, tabHost) }
                    ?.forEach { geo.clear(it) }
            }
        }

        if (opts.scope == FireScope.EVERYWHERE) {
            runCatching { (tabWebView ?: WebView(app)).let { it.clearCache(true); if (it !== tabWebView) it.destroy() } }
            runCatching { WebViewDatabase.getInstance(app).clearHttpAuthUsernamePassword() }
            PrivacyStats.clearAll()
            if (closeTabs) TabSessionStore.get(app).clear()
        }

        // Cookies last (async).
        when {
            opts.keepCookies -> finish(opts, closeTabs, onDone)
            opts.scope == FireScope.THIS_TAB -> {
                if (FirePlan.burnSiteData(tabHost, fireproof, opts) && tabUrl != null) {
                    BrowserRecon.clearCookiesForUrl(tabWebView, tabUrl)
                }
                finish(opts, closeTabs, onDone)
            }
            else -> burnAllCookiesKeeping(fireproof) { finish(opts, closeTabs, onDone) }
        }
    }

    private fun finish(opts: FireOptions, closeTabs: Boolean, onDone: () -> Unit) {
        if (closeTabs) _events.tryEmit(opts)
        onDone()
    }

    private fun burnAllCookiesKeeping(fireproof: Set<String>, onDone: () -> Unit) {
        val cm = CookieManager.getInstance()
        val saved = fireproof.flatMap { host ->
            listOf("https://$host", "https://www.$host", "http://$host").mapNotNull { url ->
                cm.getCookie(url)?.takeIf { it.isNotBlank() }?.let { url to it }
            }
        }
        cm.removeAllCookies {
            saved.forEach { (url, header) ->
                header.split(';').map { it.trim() }.filter { '=' in it }.forEach { pair ->
                    val secure = if (url.startsWith("https")) "; Secure" else ""
                    cm.setCookie(url, "$pair; Path=/; Max-Age=31536000$secure")
                }
            }
            cm.flush()
            Handler(Looper.getMainLooper()).post(onDone)
        }
    }
}
