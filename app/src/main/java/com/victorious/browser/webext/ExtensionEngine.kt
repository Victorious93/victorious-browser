package com.victorious.browser.webext

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONArray

/**
 * Lifecycle injection for userscript extensions, called from
 * [com.victorious.browser.browser.VictoriousWebViewClient]:
 *  - document-start -> onPageStarted (best-effort; NOT guaranteed before page scripts)
 *  - document-end / document-idle -> onPageFinished (idle is deferred via setTimeout)
 */
object ExtensionEngine {
    fun inject(view: WebView, url: String?, runAt: RunAt) {
        if (url.isNullOrBlank()) return
        val store = ExtensionStore.get(view.context)
        store.forPage(url, runAt).forEach { ext -> view.evaluateJavascript(GmShim.wrap(ext), null) }
    }

    /**
     * Adds the GM storage bridge only if some enabled extension needs it — avoids
     * exposing a JS interface (a fingerprintable global) on every page otherwise.
     * Takes effect for WebViews built after the grant exists.
     */
    @SuppressLint("JavascriptInterface")
    fun attachBridgeIfNeeded(view: WebView) {
        val store = ExtensionStore.get(view.context)
        if (store.extensions.value.any { it.enabled && GmShim.needsBridge(it) }) {
            view.addJavascriptInterface(ExtensionJsBridge(store), GmShim.BRIDGE_NAME)
        }
    }
}

/** Storage-only bridge. Every call is scoped to an extension id AND checked against its @grant list. */
class ExtensionJsBridge(private val store: ExtensionStore) {
    @JavascriptInterface fun getValue(id: String, key: String): String? = store.getValue(id, key)
    @JavascriptInterface fun setValue(id: String, key: String, value: String) = store.setValue(id, key, value)
    @JavascriptInterface fun deleteValue(id: String, key: String) = store.deleteValue(id, key)
    @JavascriptInterface fun listValues(id: String): String = JSONArray(store.listValues(id)).toString()
}
