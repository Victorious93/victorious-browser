package com.victorious.browser.export

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.victorious.browser.inspect.CaptureStore
import com.victorious.browser.ui.theme.NeonCyan
import com.victorious.browser.ui.theme.NeonMagenta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Export settings + "Send to fleet" screen. Builds the normalized, secret-stripped
 * payload from captured endpoints (live from [CaptureStore]) plus any findings
 * staged by other screens ([FleetExportStaging]), offers a dry-run JSON preview,
 * and pushes via the HTTP sink or the share/clipboard fallback.
 *
 * The HTTP endpoint + bearer token are user-configured ([ExportSettings]) and
 * empty by default. The exact HackerAI-Commander / Pentest-Swarm-AI API contract
 * is NOT bundled — the user supplies the endpoint URL.
 */
@Composable
fun ExportScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settings = remember { ExportSettings.get(context) }
    val cfg by settings.config.collectAsState()
    val exchanges by CaptureStore.exchanges.collectAsState()
    val stagedFindings by FleetExportStaging.findings.collectAsState()
    val stagedTargets by FleetExportStaging.targets.collectAsState()
    val scope = rememberCoroutineScope()

    var url by remember(cfg.endpointUrl) { mutableStateOf(cfg.endpointUrl) }
    var token by remember(cfg.bearerToken) { mutableStateOf(cfg.bearerToken) }
    var status by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }

    fun payload() = ExportNormalizer.buildPayload(
        exchanges = exchanges,
        findings = stagedFindings,
        extraTargets = stagedTargets,
    )

    val preview = remember(exchanges, stagedFindings, stagedTargets) {
        payload().toJsonString()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Export to fleet", style = MaterialTheme.typography.titleLarge, color = NeonMagenta)
            TextButton(onClick = onBack) { Text("Back", color = NeonCyan) }
        }
        Text(
            "Push captured endpoints / recon / scanner findings to other fleet tools " +
                "(HackerAI-Commander, Pentest-Swarm-AI). Cookies, passwords, proxy creds " +
                "and certs are stripped from exports.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(12.dp)) {
                Text("HTTP sink", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
                Text(
                    "Endpoint URL + optional bearer token. Empty by default — the exact " +
                        "API path/auth must be confirmed with the receiving tool's owner.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Endpoint URL (e.g. http://127.0.0.1:PORT/ingest)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Bearer token (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Button(onClick = { settings.set(url, token); status = "Saved sink config." }) {
                    Text("Save config")
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        val p = payload()
        Text(
            "Payload: ${p.endpoints.size} endpoints, ${p.findings.size} findings, " +
                "${p.targets.size} targets.",
            style = MaterialTheme.typography.bodyMedium,
            color = NeonCyan,
        )

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !sending,
                onClick = {
                    sending = true
                    status = "Sending…"
                    val snapshot = payload()
                    val endpoint = url.trim()
                    val bearer = token.trim()
                    scope.launch {
                        val res = withContext(Dispatchers.IO) {
                            HttpFleetSink(endpoint, bearer).send(snapshot)
                        }
                        sending = false
                        status = res.detail
                    }
                },
            ) { Text("Send (HTTP)") }
            OutlinedButton(onClick = {
                val res = IntentClipboardSink(context).copyToClipboard(payload())
                status = res.detail
            }) { Text("Copy JSON") }
            OutlinedButton(onClick = {
                runCatching {
                    context.startActivity(IntentClipboardSink(context).shareIntent(payload()))
                }.onFailure { status = "Share failed: ${it.message}" }
            }) { Text("Share") }
        }

        status?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = NeonMagenta)
        }

        Spacer(Modifier.height(12.dp))
        Text("Dry-run preview (JSON)", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Text(
                preview,
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}
