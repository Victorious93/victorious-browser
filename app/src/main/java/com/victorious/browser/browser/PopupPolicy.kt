package com.victorious.browser.browser

/**
 * Pure decision for `target=_blank` / `window.open` requests ([PopupPolicyTest]).
 *
 * Mirrors Chrome's model: a pop-up that comes straight from a user tap opens as a
 * real new tab (this is what OAuth "Sign in with…" windows need). A pop-up the page
 * fires on its own (no user gesture) is blocked and surfaced as a "Pop-up blocked"
 * chip — unless the user has allowed pop-ups for that site (always) or tapped
 * "allow once".
 */
object PopupPolicy {

    sealed interface Decision {
        /** Open as a new tab. */
        data object Allow : Decision
        /** Refuse and show the "Pop-up blocked" chip for [host]. */
        data class Block(val host: String?) : Decision
    }

    /**
     * @param userGesture true when WebView reports the request came from a user tap.
     * @param siteSetting the stored per-site pop-up decision for the opener's host.
     * @param allowedOnce true when the user tapped "allow once" for this host.
     */
    fun decide(
        userGesture: Boolean,
        siteSetting: PermissionDecision,
        allowedOnce: Boolean,
        host: String?,
    ): Decision = when {
        siteSetting == PermissionDecision.BLOCK -> Decision.Block(host) // explicit block beats a tap
        siteSetting == PermissionDecision.ALLOW -> Decision.Allow
        allowedOnce -> Decision.Allow
        userGesture -> Decision.Allow
        else -> Decision.Block(host)
    }
}

/**
 * Pure interpretation of a WebView file-chooser request ([FileChooserLogicTest]).
 * WebChromeClient.FileChooserParams is Android-only, so the glue passes its plain
 * values (accept types, mode, capture flag) here.
 */
object FileChooserLogic {
    /** FileChooserParams.MODE_OPEN_MULTIPLE. */
    const val MODE_OPEN_MULTIPLE = 1

    data class Request(val mimeType: String, val allowMultiple: Boolean, val preferCapture: Boolean)

    /**
     * Collapse the page's `accept` list into one MIME filter for a GET_CONTENT intent.
     * - Nothing / blank → any file.
     * - All one top-level type (image/png + image/jpeg) → that wildcard (image/star).
     * - Bare extensions (.pdf) → any file (MIME unknown from extension alone).
     * - A mix of types → any file (GET_CONTENT takes one type; we widen rather than drop).
     */
    fun mimeFor(acceptTypes: Array<String>?): String {
        val types = acceptTypes.orEmpty()
            .flatMap { it.split(',') }
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() && it.contains('/') }
        if (types.isEmpty()) return "*/*"
        if (types.size == 1) return types.first()
        val tops = types.map { it.substringBefore('/') }.toSet()
        return if (tops.size == 1) "${tops.first()}/*" else "*/*"
    }

    fun interpret(acceptTypes: Array<String>?, mode: Int, captureEnabled: Boolean): Request {
        val mime = mimeFor(acceptTypes)
        // Capture only makes sense for a single image/video pick from the camera.
        val capturable = mime.startsWith("image/") || mime.startsWith("video/")
        return Request(
            mimeType = mime,
            allowMultiple = mode == MODE_OPEN_MULTIPLE,
            preferCapture = captureEnabled && capturable && mode != MODE_OPEN_MULTIPLE,
        )
    }
}

/**
 * Pure helper for renderer-crash recovery ([RenderGoneLogicTest]).
 *
 * When a tab's renderer dies, WebView calls onRenderProcessGone. Returning true tells
 * WebView we've handled it so the whole app does NOT crash; we must then destroy that
 * WebView (it's unusable) and show an in-tab "page crashed" state with Reload.
 */
object RenderGoneLogic {
    /** Always true: we always take ownership so the app survives. */
    const val HANDLED = true

    fun message(didCrash: Boolean): String =
        if (didCrash) "This page crashed." else "This page was closed to free up memory."
}
