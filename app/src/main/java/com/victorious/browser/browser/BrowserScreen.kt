package com.victorious.browser.browser

import android.view.ViewGroup
import android.webkit.WebView
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.victorious.browser.ui.theme.NeonCyan
import com.victorious.browser.ui.theme.NeonMagenta

private const val HOME_URL = "https://example.com"

@Composable
fun BrowserScreen(modifier: Modifier = Modifier) {
    var urlBarText by remember { mutableStateOf(HOME_URL) }
    var currentUrl by remember { mutableStateOf(HOME_URL) }
    var pageTitle by remember { mutableStateOf("Victorious Browser") }
    var progress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var desktopUa by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val focusManager = LocalFocusManager.current

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

    BackHandler(enabled = canGoBack) {
        webViewRef?.goBack()
        syncNav(webViewRef)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Title strip
        Text(
            text = pageTitle,
            style = MaterialTheme.typography.labelLarge,
            color = NeonMagenta,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )

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
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    applyVictoriousWebSettings(this, desktopUa)
                    webViewClient = VictoriousWebViewClient(
                        onUrlChanged = { url ->
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
                    loadUrl(HOME_URL)
                }
            },
            update = { wv ->
                // Keep ref fresh across recompositions; do not recreate the WebView.
                webViewRef = wv
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
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
