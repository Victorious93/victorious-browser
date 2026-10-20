package com.victorious.browser.evidence

import android.graphics.Bitmap
import android.graphics.Canvas
import android.webkit.WebView

/**
 * Captures the currently-rendered area of a [WebView] into a [Bitmap] by drawing
 * the view onto a software canvas. No extra permissions, no MediaProjection — it
 * captures only our own WebView content, which is what recon evidence needs.
 */
object WebViewCapture {

    /** Capture the visible WebView viewport as an ARGB_8888 bitmap, or null. */
    fun capture(webView: WebView): Bitmap? {
        val width = webView.width
        val height = webView.height
        if (width <= 0 || height <= 0) return null
        return try {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            webView.draw(canvas)
            bitmap
        } catch (t: Throwable) {
            null
        }
    }
}
