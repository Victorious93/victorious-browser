package com.victorious.browser.browser

import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.victorious.browser.inject.InjectionDialog
import com.victorious.browser.reader.ReaderModeController
import com.victorious.browser.sandbox.SandboxManager
import com.victorious.browser.ui.theme.NeonCyan
import com.victorious.browser.ui.theme.NeonMagenta
import com.victorious.browser.urltools.UrlToolsDialog
import com.victorious.browser.userscript.UserScriptInjector
import com.victorious.browser.userscript.UserScriptStore

private const val HOME_URL = "https://example.com"

@Composable
fun BrowserScreen(
    initialUrl: String = HOME_URL,
    onOpenNetwork: () -> Unit = {},
    onOpenScripts: () -> Unit = {},
    onBackToLauncher: (() -> Unit)? = null,
    /** Lite App mode: no title strip, address bar, UA/Network controls. */
    liteMode: Boolean = false,
    /** Sandbox (androidx.webkit Profile) to isolate this WebView's cookies/storage into.
     *  Applied once, before the first navigation — see [SandboxManager.applyToNewWebView]. */
    sandboxName: String? = null,
    modifier: Modifier = Modifier,
) {
    var urlBarText by remember { mutableStateOf(initialUrl) }
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var pageTitle by remember { mutableStateOf("Victorious Browser") }
    var progress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var desktopUa by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val focusManager = LocalFocusManager.current
    val appContext = LocalContext.current
    val nightSettings = NightModeSettings.get(appContext)
    val nightMode by nightSettings.enabled.collectAsState()
    var readerMode by remember { mutableStateOf(false) }
    var showUrlTools by remember { mutableStateOf(false) }
    var showInject by remember { mutableStateOf(false) }

    fun syncNav(wv: WebView?) {
        canGoBack = wv?.canGoBack() == true
        canGoForward = wv?.canGoForward() == true
    }

    fun navigateTo(raw: String) {
        val target = normalizeUrl(raw)
        urlBarText = target
        webViewRef?.loadUrl(target)
        focusManager.clearFocus()
    }

    BackHandler(enabled = canGoBack || onBackToLauncher != null) {
        if (canGoBack) {
            webViewRef?.goBack()
            syncNav(webViewRef)
        } else {
            onBackToLauncher?.invoke()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (!liteMode) {
            // Title strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = pageTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = NeonMagenta,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (sandboxName != null) {
                    Text(
                        text = "[$sandboxName]",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyan,
                    )
                }
            }

            // Address bar + Go
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = urlBarText,
                    onValueChange = { urlBarText = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onBackground,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        cursorColor = NeonCyan,
                        focusedLabelColor = NeonCyan,
                    ),
                    label = { Text("URL") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { navigateTo(urlBarText) }),
                )
                Spacer(Modifier.width(6.dp))
                TextButton(
                    onClick = { navigateTo(urlBarText) },
                    colors = ButtonDefaults.textButtonColors(contentColor = NeonCyan),
                ) {
                    Text("Go")
                }
            }
        }

        // Nav controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavButton("◀", enabled = canGoBack) {
                webViewRef?.goBack()
                syncNav(webViewRef)
            }
            NavButton("▶", enabled = canGoForward) {
                webViewRef?.goForward()
                syncNav(webViewRef)
            }
            NavButton("↻", enabled = true) {
                webViewRef?.reload()
            }
            Spacer(Modifier.weight(1f))
            if (!liteMode) {
                FilterChip(
                    selected = desktopUa,
                    onClick = {
                        desktopUa = !desktopUa
                        webViewRef?.let { wv ->
                            applyVictoriousWebSettings(wv, desktopUa)
                            wv.reload()
                        }
                    },
                    label = {
                        Text(if (desktopUa) "Desktop UA" else "Mobile UA")
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonCyan.copy(alpha = 0.25f),
                        selectedLabelColor = NeonCyan,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
                Spacer(Modifier.width(4.dp))
            }
            FilterChip(
                selected = nightMode,
                onClick = { nightSettings.setEnabled(!nightMode) },
                label = { Text("Night") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NeonMagenta.copy(alpha = 0.25f),
                    selectedLabelColor = NeonMagenta,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            Spacer(Modifier.width(4.dp))
            FilterChip(
                selected = readerMode,
                onClick = {
                    val wv = webViewRef ?: return@FilterChip
                    if (readerMode) {
                        readerMode = false
                        wv.loadUrl(currentUrl)
                    } else {
                        ReaderModeController.enter(wv, nightMode) { found ->
                            if (found) {
                                readerMode = true
                            } else {
                                Toast.makeText(appContext, "Reader mode isn't available for this page", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                label = { Text("Reader") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NeonCyan.copy(alpha = 0.25f),
                    selectedLabelColor = NeonCyan,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            if (!liteMode) {
                Spacer(Modifier.width(4.dp))
                if (onBackToLauncher != null) {
                    TextButton(
                        onClick = onBackToLauncher,
                        colors = ButtonDefaults.textButtonColors(contentColor = NeonMagenta),
                    ) {
                        Text("Panels")
                    }
                }
                TextButton(
                    onClick = onOpenNetwork,
                    colors = ButtonDefaults.textButtonColors(contentColor = NeonMagenta),
                ) {
                    Text("Network")
                }
                TextButton(
                    onClick = onOpenScripts,
                    colors = ButtonDefaults.textButtonColors(contentColor = NeonMagenta),
                ) {
                    Text("Scripts")
                }
                TextButton(
                    onClick = { showUrlTools = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = NeonMagenta),
                ) {
                    Text("URL")
                }
                TextButton(
                    onClick = { showInject = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = NeonMagenta),
                ) {
                    Text("Inject")
                }
            }
        }

        if (isLoading || (progress in 1..99)) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = NeonCyan,
                trackColor = Color.Transparent,
            )
        } else {
            Spacer(Modifier.height(3.dp))
        }

        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    // Must happen before any navigation — the Profile API doesn't
                    // support reassigning a profile mid-session. No-ops onto the
                    // default (unsandboxed) profile when sandboxName is null, invalid,
                    // or the device's WebView doesn't support multiple profiles.
                    SandboxManager.applyToNewWebView(this, sandboxName)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    applyVictoriousWebSettings(this, desktopUa)
                    applyNightMode(this, nightMode)
                    // Document-start injection is scoped to this WebView instance;
                    // scripts added/edited afterward need a fresh WebView (see
                    // UserScriptInjector's doc). Fallback path (onPageStarted) always
                    // reads the live store, so it stays current even without recreation.
                    UserScriptInjector.installAll(this, UserScriptStore.get(context).scripts.value)
                    webViewClient = VictoriousWebViewClient(
                        onUrlChanged = { url ->
                            // A real navigation away from the rendered article (e.g. a
                            // link tapped inside it) lands here with a different URL —
                            // exit reader mode so its chip doesn't lie about page state.
                            if (readerMode && url != currentUrl) readerMode = false
                            currentUrl = url
                            urlBarText = url
                            syncNav(this)
                        },
                        onLoadingChanged = { loading ->
                            isLoading = loading
                            syncNav(this)
                        },
                    )
                    webChromeClient = VictoriousWebChromeClient(
                        onProgressChanged = { p ->
                            progress = p
                            if (p >= 100) isLoading = false
                        },
                        onTitleChanged = { title -> pageTitle = title },
                    )
                    webViewRef = this
                    loadUrl(initialUrl)
                }
            },
            update = { wv ->
                // Keep ref fresh across recompositions; do not recreate the WebView.
                webViewRef = wv
                applyNightMode(wv, nightMode)
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }

    if (showUrlTools) {
        UrlToolsDialog(
            initialUrl = currentUrl,
            onOpenUrl = { url -> navigateTo(url) },
            onDismiss = { showUrlTools = false },
        )
    }
    if (showInject) {
        InjectionDialog(webView = webViewRef, onDismiss = { showInject = false })
    }
}

@Composable
private fun NavButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            contentColor = NeonCyan,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

/** Accept bare hosts / search-ish input by coercing to https:// when no scheme is given. */
internal fun normalizeUrl(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return HOME_URL
    val hasScheme = trimmed.contains("://")
    return if (hasScheme) trimmed else "https://$trimmed"
}
