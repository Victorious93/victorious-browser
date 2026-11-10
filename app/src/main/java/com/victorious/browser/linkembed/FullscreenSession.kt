package com.victorious.browser.linkembed

/**
 * Bookkeeping for WebChromeClient custom-view (HTML5 fullscreen) requests in the
 * sandboxed embed viewer. Pure Kotlin so the contract is unit-tested:
 *
 *  - [enter] while nothing is showing → the view becomes current; returns true.
 *  - [enter] while a view is already showing → the NEW request is refused and its
 *    callback is told to hide immediately (WebView's documented contract); returns false.
 *  - [exit] (back press, player exit, viewer dismissed) → notifies the current callback
 *    exactly once and clears; further [exit] calls are no-ops.
 *  - [onHiddenByPage] (WebView's own onHideCustomView) → clears WITHOUT calling the
 *    callback back (the page already left fullscreen).
 *
 * [V] is the custom view type (android.view.View on device), [C] its callback.
 */
class FullscreenSession<V : Any, C : Any>(private val notifyHidden: (C) -> Unit) {
    var view: V? = null
        private set
    private var callback: C? = null

    val isActive: Boolean get() = view != null

    fun enter(newView: V, newCallback: C): Boolean {
        if (view != null) {
            notifyHidden(newCallback)
            return false
        }
        view = newView
        callback = newCallback
        return true
    }

    /** User/app-initiated exit: tells the page once, then clears. */
    fun exit() {
        val cb = callback
        view = null
        callback = null
        if (cb != null) notifyHidden(cb)
    }

    /** Page-initiated exit (onHideCustomView): just clear. */
    fun onHiddenByPage() {
        view = null
        callback = null
    }
}
