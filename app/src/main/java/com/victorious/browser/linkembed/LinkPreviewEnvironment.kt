package com.victorious.browser.linkembed

import android.content.Context
import com.victorious.browser.browser.privacy.Gpc
import com.victorious.browser.browser.privacy.PrivacyNav
import com.victorious.browser.browser.privacy.PrivacySettings
import com.victorious.browser.browser.privacy.TrackerList
import com.victorious.browser.inspect.LocalProxyService
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * Android glue: builds a [LinkPreviewService] that honors the app's current proxy and
 * privacy settings at the moment of the fetch, and owns the shared in-memory cache.
 *
 *  - Intercepting proxy running → every preview / oEmbed / image / recon request goes
 *    through 127.0.0.1:<port> (so it also appears in proxy history). Off → direct.
 *    A system VPN (e.g. WireGuard) applies to these sockets like any other app traffic.
 *  - Strip tracking params / Force HTTPS → applied to the first URL and every redirect.
 *  - GPC → `Sec-GPC: 1` request header.
 *  - Tracker blocking → preview images on listed third-party tracker hosts are skipped.
 *  - No cookies, no WebView storage, no HTTP cache are used for these fetches.
 */
object LinkPreviewEnvironment {
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android) VictoriousBrowser/0.2 LinkPreview"

    val cache = PreviewCache()

    fun service(context: Context): LinkPreviewService {
        val privacy = PrivacySettings.get(context.applicationContext)
        val proxy = if (LocalProxyService.isRunning.value) {
            Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", LocalProxyService.port))
        } else {
            null
        }
        val strip = privacy.stripParams.value
        val https = privacy.forceHttps.value
        val fetcher = BoundedFetcher(
            transport = UrlConnectionTransport(proxy),
            rewrite = { url -> PrivacyNav.rewrite(url, stripTracking = strip, forceHttps = https).url },
        )
        val headers = mapOf("User-Agent" to USER_AGENT) + Gpc.headers(privacy.gpc.value)
        val blockTrackers = privacy.blockTrackers.value
        return LinkPreviewService(
            fetcher = fetcher,
            baseHeaders = headers,
            loadImages = true,
            isBlockedHost = { host -> blockTrackers && TrackerList.match(host) != null },
        )
    }

    /** Short human summary of what the next fetch will honor (shown in the sheet). */
    fun routeSummary(context: Context): String {
        val privacy = PrivacySettings.get(context.applicationContext)
        val parts = mutableListOf<String>()
        parts += if (LocalProxyService.isRunning.value) "via local proxy :${LocalProxyService.port}" else "direct (proxy off)"
        if (privacy.forceHttps.value) parts += "HTTPS upgrade"
        if (privacy.stripParams.value) parts += "tracking params stripped"
        if (privacy.gpc.value) parts += "GPC"
        if (privacy.blockTrackers.value) parts += "tracker images skipped"
        parts += "no cookies"
        return parts.joinToString(" · ")
    }
}
