package com.victorious.browser.urltools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
}

/**
 * URL tools: encode/decode, query-param view, and link shortening (via
 * [LinkShortener], the one network call in this dialog). [onOpenUrl] navigates the
 * browser's WebView to a result (e.g. a freshly shortened link) without the caller
 * needing to parse anything back out of this dialog's state.
 */
@Composable
fun UrlToolsDialog(
    initialUrl: String,
    onOpenUrl: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf(initialUrl) }
    var output by remember { mutableStateOf("") }
    var shortening by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("URL tools") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("URL / text") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row {
                    TextButton(onClick = { output = UrlTools.encode(input) }) { Text("Encode") }
                    TextButton(onClick = { output = UrlTools.decode(input) }) { Text("Decode") }
                    TextButton(
                        onClick = {
                            val params = UrlTools.parseQueryParams(input)
                            output = if (params.isEmpty()) {
                                "(no query params)"
                            } else {
                                params.joinToString("\n") { "${it.key} = ${it.value}" }
                            }
                        },
                    ) { Text("Params") }
                }
                TextButton(
                    enabled = !shortening,
                    onClick = {
                        shortening = true
                        scope.launch {
                            val result = LinkShortener.shorten(input)
                            shortening = false
                            output = result.fold(
                                onSuccess = { it },
                                onFailure = { "Shorten failed: ${it.message}" },
                            )
                        }
                    },
                ) { Text(if (shortening) "Shortening…" else "Shorten") }

                if (output.isNotBlank()) {
                    Text(
                        output,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { copyToClipboard(context, "url-tools", output) }) { Text("Copy") }
                        TextButton(onClick = { onOpenUrl(output); onDismiss() }) { Text("Open") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
