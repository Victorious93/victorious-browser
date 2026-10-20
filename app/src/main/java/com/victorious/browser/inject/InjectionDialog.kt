package com.victorious.browser.inject

import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.victorious.browser.ui.theme.NeonCyan
import com.victorious.browser.ui.theme.NeonMagenta

/**
 * Ad-hoc JS/HTML injection against [webView]'s current page — the operator's own
 * browsing session. Unlike the "Scripts" feature, nothing here is persisted: each
 * run/inject is one-off and gone after the next navigation.
 */
@Composable
fun InjectionDialog(webView: WebView?, onDismiss: () -> Unit) {
    var jsCode by remember { mutableStateOf("") }
    var htmlCode by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Inject") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("JavaScript — runs once in the current page", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = jsCode,
                    onValueChange = { jsCode = it },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                )
                TextButton(
                    onClick = {
                        val wv = webView ?: return@TextButton
                        InjectionController.runJavaScript(wv, jsCode) { raw ->
                            resultText = "JS result: ${raw ?: "(none)"}"
                        }
                    },
                ) { Text("Run JS", color = NeonCyan) }

                Text("HTML — inserted at the end of <body>, unmodified", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = htmlCode,
                    onValueChange = { htmlCode = it },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                )
                TextButton(
                    onClick = {
                        val wv = webView ?: return@TextButton
                        InjectionController.injectHtml(wv, htmlCode) { outcome ->
                            resultText = if (outcome.ok) "HTML injected" else "Failed: ${outcome.error ?: "unknown error"}"
                        }
                    },
                ) { Text("Inject HTML", color = NeonCyan) }

                resultText?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = NeonMagenta)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
