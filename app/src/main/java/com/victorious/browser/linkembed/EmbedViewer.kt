package com.victorious.browser.linkembed

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.webkit.ProfileStore
import com.victorious.browser.sandbox.SandboxManager
import com.victorious.browser.ui.components.NeonButton
import com.victorious.browser.ui.components.NeonStatus
import com.victorious.browser.ui.components.StatusChip
import com.victorious.browser.ui.theme.NeonCyan
import com.victorious.browser.ui.theme.NeonMagenta
import java.io.ByteArrayInputStream

/** WebView profile used only by the embed viewer (wiped when the viewer closes). */
const val EMBED_VIEWER_PROFILE = "embed-viewer"

private val ViewerBg = Color(0xFF0B0F1A)

/**
 * Full-screen sandboxed viewer for oEmbed / embeddable pages.
 *
 * Isolation of the viewer WebView (none of the browser's tab plumbing is attached):
 *  - no `addJavascriptInterface`, no user scripts, extensions, privacy/spoof injectors;
 *  - no file or content access, no file-URL universal access, no geolocation, all
 *    permission requests (camera/mic/MIDI…) denied, no multiple windows;
 *  - its own `embed-viewer` WebView Profile when the device supports multi-profile
 *    (cookies + storage wiped on close); third-party cookies are off either way;
 *  - every main-frame navigation away from the wrapper is cancelled and offered as
 *    "Open in tab" instead — the viewer never silently becomes a browser;
 *  - non-http(s) sub-resource requests (file:, content:, intent:…) are refused;
 *  - remote content only ever runs inside the sandboxed iframe built by [EmbedViewerHtml];
 *  - HTML5 fullscreen (a player's ⛶ button) is honored via onShowCustomView: the custom
 *    view is shown over the viewer with system bars hidden; Back or the player's own
 *    exit leaves fullscreen. It is the same sandboxed content, just bigger.
 * The process-wide proxy override (local inspection proxy) applies here like any WebView.
 */
@Composable
fun EmbedViewerDialog(
    plan: EmbedViewerHtml.Plan,
    title: String,
    pageUrl: String,
    onDismiss: () -> Unit,
    onOpenInTab: (String) -> Unit,
) {
    var blockedNav by remember { mutableStateOf<String?>(null) }
    val isolated = remember { SandboxManager.isSupported() }
    // HTML5 fullscreen (custom view) state; fsView mirrors the session for recomposition.
    var fsView by remember { mutableStateOf<View?>(null) }
    val fullscreen = remember {
        FullscreenSession<View, WebChromeClient.CustomViewCallback> { cb -> runCatching { cb.onCustomViewHidden() } }
    }
    fun exitFullscreen() { fullscreen.exit(); fsView = null }
    DisposableEffect(Unit) { onDispose { fullscreen.exit() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        val dialogView = LocalView.current
        DisposableEffect(fsView != null) {
            val w = dialogWindow
            if (w != null) {
                val ctl = WindowCompat.getInsetsController(w, dialogView)
                if (fsView != null) {
                    ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    ctl.hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    ctl.show(WindowInsetsCompat.Type.systemBars())
                }
            }
            onDispose { if (w != null) WindowCompat.getInsetsController(w, dialogView).show(WindowInsetsCompat.Type.systemBars()) }
        }
        BackHandler(enabled = fsView != null) { exitFullscreen() }
        Box(Modifier.fillMaxSize().background(ViewerBg)) {
        Column(Modifier.fillMaxSize().background(ViewerBg)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = NeonCyan, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusChip(if (isolated) "SANDBOXED · ISOLATED PROFILE" else "SANDBOXED · SHARED PROFILE", if (isolated) NeonStatus.ON else NeonStatus.WARN)
                    }
                }
                TextButton(onClick = { onOpenInTab(pageUrl) }) { Text("Open in tab", color = NeonCyan) }
                TextButton(onClick = onDismiss) { Text("Close", color = NeonMagenta) }
            }
            blockedNav?.let { nav ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Blocked navigation out of the viewer: $nav",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonMagenta,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onOpenInTab(nav) }) { Text("Open in tab", color = NeonCyan) }
                }
            }
            val doc = EmbedViewerHtml.document(plan, title)
            if (doc == null) {
                val reason = (plan as? EmbedViewerHtml.Plan.NotEmbeddable)?.reason ?: "Not embeddable."
                Column(Modifier.fillMaxSize().padding(24.dp)) {
                    StatusChip("NOT EMBEDDED", NeonStatus.RISK)
                    Spacer(Modifier.height(12.dp))
                    Text(reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(16.dp))
                    NeonButton("Open as a normal tab", onClick = { onOpenInTab(pageUrl) })
                }
            } else {
                SandboxedWebView(
                    doc,
                    onBlockedNavigation = { blockedNav = it },
                    onEnterFullscreen = { view, cb -> if (fullscreen.enter(view, cb)) fsView = view },
                    onPageExitedFullscreen = { fullscreen.onHiddenByPage(); fsView = null },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                Text(
                    if (plan is EmbedViewerHtml.Plan.ProviderHtml) {
                        "Provider HTML runs in an opaque-origin sandbox (no cookies/storage). Inline scripts are blocked; some widgets may not render."
                    } else {
                        "Remote content is untrusted and framed in a sandbox. Playback may still be refused by the provider."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
        fsView?.let { v ->
            androidx.compose.runtime.key(v) {
                AndroidView(
                    factory = { (v.parent as? ViewGroup)?.removeView(v); v },
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                )
            }
        }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SandboxedWebView(
    document: String,
    onBlockedNavigation: (String) -> Unit,
    onEnterFullscreen: (View, WebChromeClient.CustomViewCallback) -> Unit,
    onPageExitedFullscreen: () -> Unit,
    modifier: Modifier,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            webView?.let { wv ->
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.stopLoading()
                wv.loadUrl("about:blank")
                wv.destroy()
            }
            webView = null
            wipeViewerProfile()
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx.applicationContext).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                // Must happen before the first load (Profile API constraint).
                SandboxManager.applyToNewWebView(this, EMBED_VIEWER_PROFILE)
                setBackgroundColor(0xFF0B0F1A.toInt())
                settings.apply {
                    javaScriptEnabled = true // needed by video players inside the sandboxed iframe; the wrapper itself has no script
                    domStorageEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                    @Suppress("DEPRECATION")
                    allowFileAccessFromFileURLs = false
                    @Suppress("DEPRECATION")
                    allowUniversalAccessFromFileURLs = false
                    setGeolocationEnabled(false)
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                    mediaPlaybackRequiresUserGesture = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    safeBrowsingEnabled = true
                    @Suppress("DEPRECATION")
                    saveFormData = false
                }
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                webChromeClient = object : WebChromeClient() {
                    override fun onPermissionRequest(request: PermissionRequest) = request.deny()
                    override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                        callback?.invoke(origin, false, false)
                    }
                    // HTML5 fullscreen from inside the sandboxed iframe (allow="fullscreen").
                    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                        if (view == null || callback == null) { callback?.onCustomViewHidden(); return }
                        onEnterFullscreen(view, callback)
                    }
                    override fun onHideCustomView() = onPageExitedFullscreen()
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        // Any top-level navigation (link with target=_top/_blank, window.open, redirects
                        // initiated by script) leaves the sandbox: cancel it and offer a normal tab instead.
                        if (!request.isForMainFrame) return false
                        val u = request.url?.toString().orEmpty()
                        UrlSafety.sanitize(u)?.let(onBlockedNavigation)
                        return true
                    }
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        val scheme = request.url?.scheme?.lowercase()
                        val host = request.url?.host?.lowercase()
                        if (host == EmbedViewerHtml.VIEWER_HOST) return empty(204)
                        if (scheme != "http" && scheme != "https" && scheme != "about" && scheme != "data" && scheme != "blob") return empty(403)
                        return null
                    }
                }
                loadDataWithBaseURL(EmbedViewerHtml.BASE_URL, document, "text/html", "utf-8", null)
                webView = this
            }
        },
    )
}

private fun empty(code: Int) = WebResourceResponse("text/plain", "utf-8", code, if (code == 204) "No Content" else "Forbidden", emptyMap(), ByteArrayInputStream(ByteArray(0)))

/** Clears the viewer profile's cookies and web storage (best effort; no-op without multi-profile). */
private fun wipeViewerProfile() {
    if (!SandboxManager.isSupported()) return
    runCatching {
        val profile = ProfileStore.getInstance().getProfile(EMBED_VIEWER_PROFILE) ?: return
        profile.cookieManager.removeAllCookies(null)
        profile.webStorage.deleteAllData()
    }
}
