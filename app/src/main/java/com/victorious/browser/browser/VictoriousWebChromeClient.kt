package com.victorious.browser.browser

import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebView

/** Reports page title, favicon and load progress (0–100) to Compose. */
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
}
