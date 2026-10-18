package com.victorious.browser.browser

import android.webkit.WebChromeClient
import android.webkit.WebView

/** Reports page title and load progress (0–100) to Compose. */
class VictoriousWebChromeClient(
    private val onProgressChanged: (Int) -> Unit,
    private val onTitleChanged: (String) -> Unit,
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgressChanged(newProgress)
    }

    override fun onReceivedTitle(view: WebView?, title: String?) {
        super.onReceivedTitle(view, title)
        if (!title.isNullOrBlank()) onTitleChanged(title)
    }
}
