package com.victorious.browser.inspect

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.victorious.browser.ui.theme.NeonCyan
import com.victorious.browser.ui.theme.NeonMagenta

/**
 * Compose screen for Burp-lite intercept + edit-and-replay.
 *
 * - Intercept toggle parks in-flight proxy requests for edit / forward / drop
 * - Editor is driven from a [CaptureStore] entry (history replay) or from the
 *   current [InterceptController.pending] original request
 */
@Composable
fun ModifyReplayScreen(
    seedExchange: CapturedExchange?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val interceptOn by InterceptController.enabled.collectAsState()
    val pending by InterceptController.pending.collectAsState()
    val proxyOn by LocalProxyService.isRunning.collectAsState()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var mode by remember { mutableStateOf(EditorMode.Replay) }
    var method by remember { mutableStateOf("GET") }
    var url by remember { mutableStateOf("") }
    var headersText by remember { mutableStateOf("") }
    var bodyText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var lastStatus by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(seedExchange?.id) {
        val ex = seedExchange ?: return@LaunchedEffect
        val editable = RequestEditMerge.fromExchange(ex)
        mode = EditorMode.Replay
        method = editable.method
        url = editable.url
        headersText = RequestEditMerge.formatHeaders(editable.headers)
        bodyText = editable.bodyAsUtf8()
        lastStatus = null
    }

    LaunchedEffect(pending?.exchangeId) {
        val p = pending ?: return@LaunchedEffect
        mode = EditorMode.Intercept
        method = p.original.method
        url = p.original.url
        headersText = RequestEditMerge.formatHeaders(p.original.headers)
        bodyText = p.original.bodyAsUtf8()
        lastStatus = "Intercepted #${p.exchangeId} — edit, then Forward or Drop"
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
            TextButton(onClick = onBack) { Text("← Network", color = NeonCyan) }
            Text(
                "Modify / Replay",
                style = MaterialTheme.typography.titleMedium,
                color = NeonMagenta,
            )
        }

        Spacer(Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Intercept in-flight", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (interceptOn) {
                                "ON — non-CONNECT requests pause for edit (proxy must be running)"
                            } else {
                                "Off — traffic flows without pausing"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = interceptOn,
                        onCheckedChange = { on ->
                            if (on && !proxyOn) {
                                Toast.makeText(
                                    context,
                                    "Enable the inspection proxy first",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                            InterceptController.setEnabled(on)
                        },
                    )
                }
                if (pending != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Paused request #${pending!!.exchangeId}",
                        color = NeonCyan,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            when (mode) {
                EditorMode.Intercept -> "Edit intercepted request"
                EditorMode.Replay -> "Edit & replay from history"
            },
            style = MaterialTheme.typography.titleSmall,
            color = NeonMagenta,
        )
        Spacer(modifier.height(8.dp))

        OutlinedTextField(
            value = method,
            onValueChange = { method = it },
            label = { Text("Method") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier.height(8.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier.height(8.dp))
        OutlinedTextField(
            value = headersText,
            onValueChange = { headersText = it },
            label = { Text("Headers (Name: Value per line)") },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp),
        )
        Spacer(modifier.height(8.dp))
        OutlinedTextField(
            value = bodyText,
            onValueChange = { bodyText = it },
            label = { Text("Body (UTF-8)") },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp),
        )

        Spacer(modifier.height(12.dp))
        HorizontalDivider()
        Spacer(modifier.height(12.dp))

        when (mode) {
            EditorMode.Intercept -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            val merged = RequestEditMerge.merge(
                                base = pending?.original ?: EditableRequest(method, url, emptyList()),
                                method = method,
                                url = url,
                                headersText = headersText,
                                bodyText = bodyText,
                            )
                            InterceptController.forward(merged)
                            lastStatus = "Forwarded with edits"
                            mode = EditorMode.Replay
                        },
                        enabled = pending != null && !busy,
                        modifier = Modifier.weight(1f),
                    ) { Text("Forward") }
                    Button(
                        onClick = {
                            InterceptController.drop()
                            lastStatus = "Dropped"
                            mode = EditorMode.Replay
                        },
                        enabled = pending != null && !busy,
                        modifier = Modifier.weight(1f),
                    ) { Text("Drop") }
                }
            }
            EditorMode.Replay -> {
                Button(
                    onClick = {
                        if (url.isBlank()) {
                            Toast.makeText(context, "URL required", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        busy = true
                        lastStatus = "Sending…"
                        val base = seedExchange?.let { RequestEditMerge.fromExchange(it) }
                            ?: EditableRequest(method, url, RequestEditMerge.parseHeadersText(headersText))
                        val merged = RequestEditMerge.merge(
                            base = base,
                            method = method,
                            url = url,
                            headersText = headersText,
                            bodyText = bodyText,
                        )
                        ReplayClient.replayAsync(merged, throughProxy = true) { result ->
                            mainHandler.post {
                                busy = false
                                lastStatus = if (result.ok) {
                                    "Replay → HTTP ${result.statusCode}" +
                                        (result.recordedExchangeId?.let { " (id=$it)" } ?: " (via proxy history)")
                                } else {
                                    "Replay failed: ${result.error}"
                                }
                                Toast.makeText(context, lastStatus, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !busy && url.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (busy) "Replaying…" else "Replay request")
                }
                Text(
                    if (proxyOn) {
                        "Will send through 127.0.0.1:${LocalProxyService.port} (recorded in history)."
                    } else {
                        "Proxy off — replay goes direct and is recorded locally."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        lastStatus?.let {
            Spacer(modifier.height(10.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = NeonCyan,
            )
        }
    }
}

private enum class EditorMode { Intercept, Replay }
