package com.victorious.browser.inspect

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.victorious.browser.ui.theme.NeonCyan
import com.victorious.browser.ui.theme.NeonMagenta

/**
 * Compose "Network / History" screen: list of captured entries, detail view
 * (headers, decoded JWT, cookies), and an on/off switch for the inspection proxy.
 *
 * The Switch is the local UI; the fleet bridge also toggles via browser.proxy.set.
 */
@Composable
fun NetworkHistoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val exchanges by CaptureStore.exchanges.collectAsState()
    val proxyOn by LocalProxyService.isRunning.collectAsState()
    var selected by remember { mutableStateOf<CapturedExchange?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onBack) { Text("← Browser", color = NeonCyan) }
            Text(
                "Network / History",
                style = MaterialTheme.typography.titleMedium,
                color = NeonMagenta,
            )
        }

        Spacer(Modifier.height(8.dp))

        // Proxy on/off — local UI; bridge uses LocalProxyService.start/stop too.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Inspection proxy", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (proxyOn) "127.0.0.1:${LocalProxyService.port} (localhost only, no VpnService)"
                    else "Off — WebView traffic is direct",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = proxyOn,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        val ok = LocalProxyService.start(context.applicationContext)
                        if (!ok) {
                            Toast.makeText(context, "Proxy failed to start — see logcat", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        LocalProxyService.stop()
                    }
                },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { CaptureStore.clear() }) { Text("Clear") }
            Button(
                onClick = {
                    val file = CaptureExporter.writeToFile(context, exchanges)
                    Toast.makeText(context, "Exported → ${file.absolutePath}", Toast.LENGTH_LONG).show()
                },
                enabled = exchanges.isNotEmpty(),
            ) { Text("Export JSON") }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        if (selected != null) {
            DetailView(selected!!) { selected = null }
        } else {
            if (exchanges.isEmpty()) {
                Text(
                    "No captures yet. Enable the proxy and browse your own dashboards.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(exchanges.asReversed(), key = { it.id }) { ex ->
                        ExchangeRow(ex) { selected = ex }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExchangeRow(ex: CapturedExchange, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${ex.method} ${ex.statusCode ?: "…"}", color = NeonCyan, style = MaterialTheme.typography.labelLarge)
                Text(
                    "${ex.responseBytes} B · ${ex.durationMs ?: "—"} ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                ex.url,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            Text(ex.host, style = MaterialTheme.typography.labelSmall, color = NeonMagenta)
        }
    }
}

@Composable
private fun DetailView(ex: CapturedExchange, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        TextButton(onClick = onBack) { Text("← List", color = NeonCyan) }
        Section("Request") {
            Mono("${ex.method} ${ex.url}")
            Mono("Host: ${ex.host}")
            Mono("Started: ${ex.requestStartedAt}")
            Mono("Request bytes: ${ex.requestBytes}")
        }
        Section("Response") {
            Mono("Status: ${ex.statusCode ?: "—"}")
            Mono("Received: ${ex.responseReceivedAt ?: "—"}")
            Mono("Duration: ${ex.durationMs ?: "—"} ms")
            Mono("Response bytes: ${ex.responseBytes}")
        }
        Section("Request headers") {
            ex.requestHeaders.forEach { Mono("${it.name}: ${it.value}") }
            if (ex.requestHeaders.isEmpty()) Mono("(none)")
        }
        Section("Response headers") {
            ex.responseHeaders.forEach { Mono("${it.name}: ${it.value}") }
            if (ex.responseHeaders.isEmpty()) Mono("(none)")
        }

        val cookies = InspectHelpers.cookies(ex.requestHeaders + ex.responseHeaders)
        Section("Cookies") {
            if (cookies.isEmpty()) Mono("(none)")
            else cookies.forEach { Mono("${it.name} = ${it.value}") }
        }

        val bearer = InspectHelpers.bearerToken(ex.requestHeaders)
        val jwt = InspectHelpers.decodeJwt(bearer)
        Section("Authorization / JWT (base64 decode only — no signature check)") {
            Mono("Bearer: ${bearer ?: "(none)"}")
            if (jwt != null) {
                Mono("JWT header:\n${jwt.header}")
                Mono("JWT payload:\n${jwt.payload}")
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(10.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, color = NeonMagenta)
    Spacer(Modifier.height(4.dp))
    content()
}

@Composable
private fun Mono(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(vertical = 1.dp),
    )
}
