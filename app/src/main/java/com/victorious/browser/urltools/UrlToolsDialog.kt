package com.victorious.browser.urltools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.victorious.browser.ui.theme.NeonCyan
import com.victorious.browser.ui.theme.NeonMagenta
import kotlinx.coroutines.launch

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
}

/**
 * URL tools: encode/decode, query-param view, link shortening, a structured URL
 * editor ("URL changer"), and malware/phishing lookups ("virus scan"). [onOpenUrl]
 * navigates the browser's WebView to a result without the caller needing to parse
 * anything back out of this dialog's state.
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
    var scanning by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf("") }

    // URL changer fields, seeded from initialUrl when it parses as an absolute URL.
    val seeded = remember { UrlTools.parseParts(initialUrl) }
    var scheme by remember { mutableStateOf(seeded?.scheme ?: "https") }
    var host by remember { mutableStateOf(seeded?.host ?: "") }
    var port by remember { mutableStateOf(seeded?.port?.toString() ?: "") }
    var path by remember { mutableStateOf(seeded?.path ?: "") }
    var query by remember { mutableStateOf(seeded?.query ?: "") }
    var fragment by remember { mutableStateOf(seeded?.fragment ?: "") }

    val vtSettings = remember { VirusTotalSettings.get(context) }
    val vtKey by vtSettings.apiKey.collectAsState()
    var vtKeyInput by remember { mutableStateOf(vtKey) }
    val urlHausSettings = remember { UrlHausSettings.get(context) }
    val urlHausKey by urlHausSettings.authKey.collectAsState()
    var urlHausKeyInput by remember { mutableStateOf(urlHausKey) }

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

                HorizontalDivider(Modifier.height(1.dp))
                Text("URL changer", style = MaterialTheme.typography.titleSmall, color = NeonMagenta)
                TextButton(onClick = { UrlTools.parseParts(input)?.let { p ->
                    scheme = p.scheme; host = p.host; port = p.port?.toString() ?: ""
                    path = p.path; query = p.query; fragment = p.fragment
                } }) { Text("Load from URL / text above") }
                OutlinedTextField(value = scheme, onValueChange = { scheme = it }, label = { Text("Scheme") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("Host") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("Port (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = path, onValueChange = { path = it }, label = { Text("Path") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Query (no leading ?)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = fragment, onValueChange = { fragment = it }, label = { Text("Fragment (no leading #)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(
                    onClick = {
                        if (host.isBlank()) {
                            output = "Host is required"
                            return@TextButton
                        }
                        output = UrlTools.buildFromParts(
                            UrlTools.UrlParts(
                                scheme = scheme.ifBlank { "https" },
                                host = host,
                                port = port.toIntOrNull(),
                                path = path,
                                query = query,
                                fragment = fragment,
                            ),
                        )
                    },
                ) { Text("Rebuild URL", color = NeonCyan) }

                HorizontalDivider(Modifier.height(1.dp))
                Text("Virus / malware scan", style = MaterialTheme.typography.titleSmall, color = NeonMagenta)
                Text(
                    "Both backends need your own free API key — neither is keyless (confirmed " +
                        "live against both APIs: a missing key gets an auth-error response, not a " +
                        "scan). A hit means the URL is specifically known-bad; a miss means only " +
                        "\"not in this database\", not \"safe\".",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = urlHausKeyInput,
                    onValueChange = { urlHausKeyInput = it },
                    label = { Text("URLhaus Auth-Key (get one at auth.abuse.ch)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { urlHausSettings.setAuthKey(urlHausKeyInput.trim()) }) { Text("Save key") }
                OutlinedTextField(
                    value = vtKeyInput,
                    onValueChange = { vtKeyInput = it },
                    label = { Text("VirusTotal API key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { vtSettings.setApiKey(vtKeyInput.trim()) }) { Text("Save key") }
                TextButton(
                    enabled = !scanning,
                    onClick = {
                        scanning = true
                        scanResult = ""
                        scope.launch {
                            scanResult += if (urlHausKey.isNotBlank()) {
                                val urlHaus = UrlScanner.checkUrlHaus(input, urlHausKey)
                                urlHaus.fold(
                                    onSuccess = { "${it.source}: ${it.detail}\n" },
                                    onFailure = { "URLhaus check failed: ${it.message}\n" },
                                )
                            } else {
                                "URLhaus: no Auth-Key set, skipped\n"
                            }
                            if (vtKey.isNotBlank()) {
                                val vt = UrlScanner.checkVirusTotal(input, vtKey)
                                scanResult += vt.fold(
                                    onSuccess = { "${it.source}: ${it.detail}" },
                                    onFailure = { "VirusTotal check failed: ${it.message}" },
                                )
                            } else {
                                scanResult += "VirusTotal: no API key set, skipped"
                            }
                            scanning = false
                        }
                    },
                ) { Text(if (scanning) "Scanning…" else "Scan URL") }
                if (scanResult.isNotBlank()) {
                    Text(
                        scanResult,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = NeonMagenta,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
