package com.victorious.browser.browser

import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Android-facing cookie / WebStorage helpers for the recon inspectors.
 * Profile-aware when MULTI_PROFILE is present so private tabs inspect their own jar.
 */
object BrowserRecon {
    fun cookieManagerFor(webView: WebView?): CookieManager {
        if (webView != null && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            runCatching { return WebViewCompat.getProfile(webView).cookieManager }
        }
        return CookieManager.getInstance()
    }

    fun webStorageFor(webView: WebView?): WebStorage {
        if (webView != null && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            runCatching { return WebViewCompat.getProfile(webView).webStorage }
        }
        return WebStorage.getInstance()
    }

    fun cookiesForUrl(webView: WebView?, url: String): List<CookieJarLogic.JarCookie> {
        if (url.isBlank()) return emptyList()
        return CookieJarLogic.parseJar(cookieManagerFor(webView).getCookie(url))
    }

    fun setCookie(webView: WebView?, url: String, name: String, value: String) {
        val cm = cookieManagerFor(webView)
        cm.setCookie(url, CookieJarLogic.setCookieLine(name, value))
        cm.flush()
    }

    fun deleteCookie(webView: WebView?, url: String, name: String) {
        val cm = cookieManagerFor(webView)
        cm.setCookie(url, CookieJarLogic.expireCookieLine(name))
        cm.flush()
    }

    fun clearCookiesForUrl(webView: WebView?, url: String) {
        if (url.isBlank()) return
        val cm = cookieManagerFor(webView)
        CookieJarLogic.parseJar(cm.getCookie(url)).forEach { c ->
            cm.setCookie(url, CookieJarLogic.expireCookieLine(c.name))
        }
        cm.flush()
    }

    fun clearOriginStorage(webView: WebView?, origin: String?) {
        if (origin.isNullOrBlank()) return
        runCatching { webStorageFor(webView).deleteOrigin(origin) }
    }
}
