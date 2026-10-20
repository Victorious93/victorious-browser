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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
 * Compose "Network / History" screen: searchable/filterable list of captured
 * entries, deep detail view (bodies with content-type, cookies with attributes,
 * decoded-not-verified JWTs), proxy on/off, intercept toggle, and entry into
 * Modify / Replay.
 */
@Composable
fun NetworkHistoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val exchanges by CaptureStore.exchanges.collectAsState()
    val proxyOn by LocalProxyService.isRunning.collectAsState()
    val interceptOn by InterceptController.enabled.collectAsState()
    val pendingIntercept by InterceptController.pending.collectAsState()
    var selected by remember { mutableStateOf<CapturedExchange?>(null) }
    var modifySeed by remember { mutableStateOf<CapturedExchange?>(null) }
    var showModify by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var filterMethod by remember { mutableStateOf("") }
    var filterStatus by remember { mutableStateOf("") }
    var filterContentType by remember { mutableStateOf("") }
    var filterHost by remember { mutableStateOf("") }
    var showFilters by remember { mutableStateOf(false) }

    val criteria = HistoryFilter.Criteria(
        query = searchQuery,
        method = filterMethod,
        status = filterStatus,
        contentType = filterContentType,
        host = filterHost,
    )
    val filtered = remember(exchanges, criteria) {
        HistoryFilter.apply(exchanges, criteria)
    }

    if (showModify) {
        ModifyReplayScreen(
            seedExchange = modifySeed,
            onBack = {
                showModify = false
                modifySeed = null
            },
            modifier = modifier,
        )
        return
    }

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

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Intercept (modify)", style = MaterialTheme.typography.titleSmall)
                Text(
                    when {
                        pendingIntercept != null ->
                            "Paused #${pendingIntercept!!.exchangeId} — open Modify / Replay"
                        interceptOn -> "ON — in-flight requests pause for edit"
                        else -> "Off"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = interceptOn,
                onCheckedChange = { on ->
                    if (on && !proxyOn) {
                        Toast.makeText(context, "Enable the inspection proxy first", Toast.LENGTH_LONG).show()
                    }
                    InterceptController.setEnabled(on)
                },
            )
        }

        val blockerSettings = remember { ContentBlockerSettings.get(context.applicationContext) }
        val blockerOn by blockerSettings.enabled.collectAsState()
        val blockerCategories by blockerSettings.categories.collectAsState()

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Content blocker", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (blockerOn) "Blocking ${blockerCategories.size} categor${if (blockerCategories.size == 1) "y" else "ies"}"
                    else "Off",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = blockerOn, onCheckedChange = { blockerSettings.setEnabled(it) })
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BlockCategory.entries.forEach { category ->
                FilterChip(
                    selected = category in blockerCategories,
                    onClick = { blockerSettings.setCategoryEnabled(category, category !in blockerCategories) },
                    label = { Text(category.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonCyan.copy(alpha = 0.25f),
                        selectedLabelColor = NeonCyan,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { CaptureStore.clear() }) { Text("Clear") }
            Button(
                onClick = {
                    val file = CaptureExporter.writeToFile(context, filtered)
                    Toast.makeText(context, "Exported → ${file.absolutePath}", Toast.LENGTH_LONG).show()
                },
                enabled = filtered.isNotEmpty(),
            ) { Text("Export JSON") }
            Button(
                onClick = {
                    modifySeed = null
                    showModify = true
                },
            ) { Text("Modify / Replay") }
        }

        Spacer(Modifier.height(8.dp))

        if (selected == null) {
            SearchFilterBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                showFilters = showFilters,
                onToggleFilters = { showFilters = !showFilters },
                method = filterMethod,
                onMethodChange = { filterMethod = it },
                status = filterStatus,
                onStatusChange = { filterStatus = it },
                contentType = filterContentType,
                onContentTypeChange = { filterContentType = it },
                host = filterHost,
                onHostChange = { filterHost = it },
                resultCount = filtered.size,
                totalCount = exchanges.size,
            )
            Spacer(Modifier.height(8.dp))
        }

        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        if (selected != null) {
            DetailView(
                ex = selected!!,
                onBack = { selected = null },
                onEditReplay = {
                    modifySeed = selected
                    showModify = true
                },
            )
        } else {
            if (exchanges.isEmpty()) {
                Text(
                    "No captures yet. Enable the proxy and browse your own dashboards.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (filtered.isEmpty()) {
                Text(
                    "No matches for current search / filters.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filtered.asReversed(), key = { it.id }) { ex ->
                        ExchangeRow(ex) { selected = ex }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchFilterBar(
    query: String,
    onQueryChange: (String) -> Unit,
    showFilters: Boolean,
    onToggleFilters: () -> Unit,
    method: String,
    onMethodChange: (String) -> Unit,
    status: String,
    onStatusChange: (String) -> Unit,
    contentType: String,
    onContentTypeChange: (String) -> Unit,
    host: String,
    onHostChange: (String) -> Unit,
    resultCount: Int,
    totalCount: Int,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search URL / headers / body / JWT claims") },
            placeholder = { Text("e.g. Authorization, sub=, application/json") },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Showing $resultCount of $totalCount",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onToggleFilters) {
                Text(if (showFilters) "Hide filters" else "Filters", color = NeonCyan)
            }
        }
        if (showFilters) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterField("Method", method, onMethodChange, Modifier.weight(1f))
                FilterField("Status", status, onStatusChange, Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterField("Host", host, onHostChange, Modifier.weight(1f))
                FilterField("Content-Type", contentType, onContentTypeChange, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FilterField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        singleLine = true,
        label = { Text(label) },
    )
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
        Column(modifier = Modifier.padding(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val blocked = ex.blocked
                Text(
                    if (blocked != null) "${ex.method} BLOCKED (${blocked.label})" else "${ex.method} ${ex.statusCode ?: "…"}",
                    color = if (blocked != null) NeonMagenta else NeonCyan,
                    style = MaterialTheme.typography.labelLarge,
                )
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
            Row {
                Text(ex.host, style = MaterialTheme.typography.labelSmall, color = NeonMagenta)
                val ct = ex.responseContentType() ?: ex.requestContentType()
                if (ct != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        ct.substringBefore(';').trim(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailView(
    ex: CapturedExchange,
    onBack: () -> Unit,
    onEditReplay: () -> Unit,
) {
    val cookies = remember(ex) {
        InspectHelpers.parseCookies(ex.requestHeaders + ex.responseHeaders)
    }
    val jwts = remember(ex) {
        InspectHelpers.findJwts(
            requestHeaders = ex.requestHeaders,
            responseHeaders = ex.responseHeaders,
            requestBody = ex.requestBody,
            responseBody = ex.responseBody,
            requestContentType = ex.requestContentType(),
            responseContentType = ex.responseContentType(),
        )
    }
    val reqPreview = remember(ex) {
        InspectHelpers.bodyPreview(ex.requestBody, ex.requestContentType(), ex.requestBodyTruncated)
    }
    val resPreview = remember(ex) {
        InspectHelpers.bodyPreview(ex.responseBody, ex.responseContentType(), ex.responseBodyTruncated)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← List", color = NeonCyan) }
            Button(onClick = onEditReplay) { Text("Edit & Replay") }
        }
        Section("Request") {
            Mono("${ex.method} ${ex.url}")
            Mono("Host: ${ex.host}")
            Mono("Started: ${ex.requestStartedAt}")
            Mono("Request bytes: ${ex.requestBytes}")
            Mono("Content-Type: ${ex.requestContentType() ?: "(none)"}")
        }
        Section("Response") {
            Mono("Status: ${ex.statusCode ?: "—"}")
            Mono("Received: ${ex.responseReceivedAt ?: "—"}")
            Mono("Duration: ${ex.durationMs ?: "—"} ms")
            Mono("Response bytes: ${ex.responseBytes}")
            Mono("Content-Type: ${ex.responseContentType() ?: "(none)"}")
        }
        Section("Request headers") {
            ex.requestHeaders.forEach { Mono("${it.name}: ${it.value}") }
            if (ex.requestHeaders.isEmpty()) Mono("(none)")
        }
        Section("Response headers") {
            ex.responseHeaders.forEach { Mono("${it.name}: ${it.value}") }
            if (ex.responseHeaders.isEmpty()) Mono("(none)")
        }

        Section("Request body") {
            BodySection(reqPreview)
        }
        Section("Response body") {
            BodySection(resPreview)
        }

        Section("Cookies") {
            if (cookies.isEmpty()) {
                Mono("(none)")
            } else {
                cookies.forEach { c ->
                    Mono("[${c.source}] ${c.name} = ${c.value}")
                    if (c.attributes.isNotEmpty()) {
                        c.attributes.forEach { (k, v) ->
                            Mono(if (v.isEmpty()) "    $k" else "    $k = $v")
                        }
                    }
                }
            }
        }

        Section("JWT (decoded — NOT signature-verified)") {
            if (jwts.isEmpty()) {
                Mono("(none detected in Authorization / cookies / bodies)")
            } else {
                jwts.forEachIndexed { idx, jwt ->
                    Mono("--- token ${idx + 1} from ${jwt.source} ---")
                    Mono("signatureVerified: false (decoded-not-verified)")
                    Mono("Header:\n${jwt.header ?: "(n/a)"}")
                    Mono("Payload / claims:")
                    if (jwt.claims.isNotEmpty()) {
                        jwt.claims.forEach { (k, v) -> Mono("  $k = $v") }
                    } else {
                        Mono(jwt.payload ?: "(n/a)")
                    }
                }
            }
        }
    }
}

@Composable
private fun BodySection(preview: InspectHelpers.BodyPreview?) {
    if (preview == null) {
        Mono("(no body captured)")
        return
    }
    Mono("Content-Type: ${preview.contentType ?: "(none)"}")
    Mono("Bytes: ${preview.byteLength}${if (preview.truncated) " (truncated to capture cap)" else ""}")
    if (preview.isText) {
        Mono("Kind: text")
        Mono(preview.text ?: "")
    } else {
        Mono("Kind: binary (hex preview)")
        Mono(preview.hexPreview ?: "")
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(10.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, color = NeonMagenta)
    Spacer(modifier = Modifier.height(4.dp))
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
