package com.victorious.browser.browser

import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView

/**
 * Reports page title, favicon and load progress (0–100) to Compose, and bridges the
 * WebView "chrome" callbacks that make real sites work:
 *
 *  - [fileChooser]: `<input type=file>` pickers (file uploads / "attach photo").
 *  - [showCustomView] / [hideCustomView]: HTML5 fullscreen video.
 *  - [permissionRequest]: camera / microphone (getUserMedia).
 *  - [geoPrompt]: the page asking for location (navigator.geolocation).
 *  - [createWindow]: target=_blank / window.open (gesture-gated pop-up → new tab).
 *
 * Every bridge defaults to the safe/legacy behaviour (deny, no-op) so hosts that don't
 * wire a handler — e.g. the single-tab [BrowserScreen] and the sandboxed embed viewer —
 * keep their previous behaviour unchanged.
 */
class VictoriousWebChromeClient(
    private val onProgressChanged: (Int) -> Unit,
    private val onTitleChanged: (String) -> Unit,
    private val onIconChanged: (Bitmap?) -> Unit = {},
    private val onConsoleMessage: (level: String, message: String, source: String, line: Int) -> Unit = { _, _, _, _ -> },
    /** Handle a file picker; return true when taken over (the callback will be invoked later). */
    private val fileChooser: (ValueCallback<Array<Uri>>?, WebChromeClient.FileChooserParams?) -> Boolean = { _, _ -> false },
    /** Enter HTML5 fullscreen with [view]; call the callback's onCustomViewHidden to exit. */
    private val showCustomView: (View?, WebChromeClient.CustomViewCallback?) -> Unit = { _, cb -> cb?.onCustomViewHidden() },
    /** Leave HTML5 fullscreen. */
    private val hideCustomView: () -> Unit = {},
    /** A page asked for camera/microphone; the handler must grant() or deny(). */
    private val permissionRequest: (PermissionRequest?) -> Unit = { it?.deny() },
    /** A page asked for location; the handler must invoke the callback. */
    private val geoPrompt: (String?, GeolocationPermissions.Callback?) -> Unit = { origin, cb -> cb?.invoke(origin, false, false) },
    /** target=_blank / window.open; return true when a new tab/window was created. */
    private val createWindow: (isUserGesture: Boolean, resultMsg: Message?) -> Boolean = { _, _ -> false },
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

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?,
    ): Boolean {
        if (fileChooser(filePathCallback, fileChooserParams)) return true
        filePathCallback?.onReceiveValue(null)
        return false
    }

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        showCustomView(view, callback)
    }

    override fun onHideCustomView() {
        hideCustomView()
    }

    override fun onPermissionRequest(request: PermissionRequest?) {
        permissionRequest(request)
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback?,
    ) {
        geoPrompt(origin, callback)
    }

    override fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message?,
    ): Boolean = createWindow(isUserGesture, resultMsg)

    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
        val msg = consoleMessage ?: return super.onConsoleMessage(consoleMessage)
        onConsoleMessage(
            ConsoleLog.normalizeLevel(msg.messageLevel()?.name),
            msg.message().orEmpty(),
            msg.sourceId().orEmpty(),
            msg.lineNumber(),
        )
        return true
    }
}
