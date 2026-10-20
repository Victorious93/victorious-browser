package com.victorious.browser.antiphish

import android.content.Context
import android.webkit.WebView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Coordinates passive page analysis after each navigation.
 * Off by default ([AntiPhishSettings.enabled]); when on, probes the DOM and
 * publishes an advisory [lastResult] for UI banners. Never blocks navigation.
 */
class AntiPhishController private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val settings = AntiPhishSettings.get(appContext)
    private val log = AntiPhishReportLog.get(appContext)

    private val _lastResult = MutableStateFlow<PhishingScorer.Result?>(null)
    val lastResult: StateFlow<PhishingScorer.Result?> = _lastResult.asStateFlow()

    private val _bannerVisible = MutableStateFlow(false)
    val bannerVisible: StateFlow<Boolean> = _bannerVisible.asStateFlow()

    /** Dismissed hosts for this process session (advisory — user can dismiss). */
    private val dismissedHosts = mutableSetOf<String>()

    fun dismissCurrent() {
        _lastResult.value?.host?.let { dismissedHosts += it }
        _bannerVisible.value = false
    }

    fun clearBanner() {
        _bannerVisible.value = false
        _lastResult.value = null
    }

    fun reportCurrent() {
        _lastResult.value?.let { log.record(it) }
    }

    /**
     * Called from WebViewClient.onPageFinished. No-ops when detection is disabled.
     * Always non-blocking: probe + score happen asynchronously via evaluateJavascript.
     */
    fun onPageFinished(webView: WebView, url: String?) {
        if (!settings.enabled.value) {
            clearBanner()
            return
        }
        if (url.isNullOrBlank()) return
        val host = AntiPhishReportLog.hostOnly(url)
        if (host.isEmpty()) return
        if (host in dismissedHosts) return

        PageProbe.evaluate(webView) { snap ->
            val result = PhishingScorer.score(
                pageUrl = url,
                https = snap?.https ?: url.startsWith("https://", ignoreCase = true),
                pageTitle = snap?.title,
                forms = snap?.forms.orEmpty(),
                bodyBrandHints = snap?.brandHints.orEmpty(),
                knownGood = settings.knownGood.value,
                brandKeywords = settings.keywords.value,
            )
            _lastResult.value = result
            if (result.isRisky && result.host !in dismissedHosts) {
                _bannerVisible.value = true
                // Auto-record risky detections (host-only, no secrets).
                log.record(result)
            } else {
                _bannerVisible.value = false
            }
        }
    }

    companion object {
        @Volatile private var instance: AntiPhishController? = null

        fun get(context: Context): AntiPhishController =
            instance ?: synchronized(this) {
                instance ?: AntiPhishController(context).also { instance = it }
            }
    }
}
