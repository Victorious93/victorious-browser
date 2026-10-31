package com.victorious.browser.browser.privacy

import android.content.Context
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Document-start scripts for GPC + referrer policy, and the cookie pop-up helper.
 * Scripts are installed per WebView at creation; toggling GPC / referrer trimming
 * takes effect for new tabs (and immediately via the onPageStarted fallback path).
 */
object PrivacyInjector {
    private const val COOKIE_ASSET = "userscripts/cookie-popups.user.js"
    @Volatile private var cookieScript: String? = null

    private fun combined(s: PrivacySettings): String = buildString {
        if (s.gpc.value) append(Gpc.script()).append('\n')
        if (s.trimReferrer.value) append(ReferrerTrim.script()).append('\n')
    }

    /** Install at creation; returns the installed JS ("" if none) so the fallback can tell when toggles changed. */
    fun installDocumentStart(webView: WebView, s: PrivacySettings): String {
        val js = combined(s)
        if (js.isBlank() || !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return ""
        WebViewCompat.addDocumentStartJavaScript(webView, js, setOf("*"))
        return js
    }

    /** Fallback (late) injection when document-start scripts are unsupported or toggled on after tab creation. */
    fun injectOnPageStarted(webView: WebView, s: PrivacySettings, installedJs: String) {
        val js = combined(s)
        if (js.isNotBlank() && js != installedJs) webView.evaluateJavascript(js, null)
    }

    /** Run the cookie pop-up helper; [onHandled] fires if the first pass rejected/hid a banner. */
    fun runCookiePopups(webView: WebView, context: Context, onHandled: () -> Unit) {
        val js = cookieScript ?: runCatching {
            context.assets.open(COOKIE_ASSET).bufferedReader().use { it.readText() }
        }.getOrNull()?.also { cookieScript = it } ?: return
        webView.evaluateJavascript(js) { result ->
            if (result != null && ("rejected" in result || "hidden" in result)) onHandled()
        }
    }
}
