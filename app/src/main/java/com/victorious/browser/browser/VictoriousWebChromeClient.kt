package com.victorious.browser.browser

import android.graphics.Bitmap
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebView

/**
 * Reports page title, favicon and load progress (0–100) to Compose.
 *
 * Geolocation: always deny the WebView→OS geolocation permission prompt.
 * When Net ID geolocation spoofing is enabled, a document-start JS shim replaces
 * navigator.geolocation so pages never reach this prompt for spoofed coords.
 * Real device location is not requested (no ACCESS_FINE_LOCATION in the manifest).
 */
class VictoriousWebChromeClient(
    private val onProgressChanged: (Int) -> Unit,
    private val onTitleChanged: (String) -> Unit,
    private val onIconChanged: (Bitmap?) -> Unit = {},
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgressChanged(newProgress)
    }

    override fun onReceivedTitle(view: WebView?, title: String?) {
        super.onReceivedTitle(view, title)
        if (!title.isNullOrBlank()) onTitleChanged(title)
    }

    override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
        super.onReceivedIcon(view, icon)
        onIconChanged(icon)
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback?,
    ) {
        callback?.invoke(origin, false, false)
    }
}
