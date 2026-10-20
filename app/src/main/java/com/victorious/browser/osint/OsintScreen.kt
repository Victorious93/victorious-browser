package com.victorious.browser.osint

import com.victorious.browser.ui.components.NeonToolHeader
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.victorious.browser.inspect.CaptureStore
import com.victorious.browser.ui.theme.NeonCyan
import com.victorious.browser.ui.theme.NeonMagenta

/**
 * OSINT & Google Dork tool group. Three sub-sections, all building URLs/queries
 * from pure logic and handing them to the operator to open in the WebView:
 *  - Dork Builder ([DorkBuilder])
 *  - OSINT Panel ([OsintSources])
 *  - Passive Fingerprint ([TechFingerprint], over already-captured exchanges)
 */
@Composable
fun OsintScreen(
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf("Dork Builder", "OSINT Panel", "Fingerprint")

    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        NeonToolHeader(title = "OSINT & Dork Builder", onBack = onBack)
        Text(
            "Builds queries and public-source links for your own authorized targets. " +
                "The app never auto-scrapes — you open each link in a tab.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
            tabs.forEachIndexed { i, title ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
            }
        }
        Spacer(Modifier.height(8.dp))
        when (tab) {
            0 -> DorkBuilderTab(onOpenUrl)
            1 -> OsintPanelTab(onOpenUrl)
            2 -> FingerprintTab()
        }
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp)) { content() }
    }
}

// --- Dork Builder tab ----------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DorkBuilderTab(onOpenUrl: (String) -> Unit) {
    var target by remember { mutableStateOf("") }
    var engine by remember { mutableStateOf(DorkBuilder.Engine.GOOGLE) }
    var op by remember { mutableStateOf(DorkBuilder.Operator.SITE) }
    var opValue by remember { mutableStateOf("") }
    var exactValue by remember { mutableStateOf("") }
    var excludeValue by remember { mutableStateOf("") }
    var rangeFrom by remember { mutableStateOf("") }
    var rangeTo by remember { mutableStateOf("") }
    val tokens = remember { mutableStateListOf<String>() }

    fun addToken(t: String) { if (t.isNotBlank()) tokens.add(t) }

    val query = DorkBuilder.buildQuery(tokens)

    Column(Modifier.verticalScroll(rememberScrollState())) {
        SectionCard {
            Text("Target domain", style = MaterialTheme.typography.labelMedium, color = NeonCyan)
            OutlinedTextField(
                value = target,
                onValueChange = { target = it },
                label = { Text("example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text("Quick templates", style = MaterialTheme.typography.labelMedium, color = NeonCyan)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DorkBuilder.templates.forEach { tpl ->
                    OutlinedButton(onClick = {
                        tokens.clear()
                        tokens.addAll(tpl.build(target.trim()).filter { it.isNotBlank() })
                    }) { Text(tpl.name, style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        SectionCard {
            Text("Add operator term", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DorkBuilder.Operator.entries.forEach { o ->
                    FilterChip(
                        selected = op == o,
                        onClick = { op = o },
                        label = { Text(o.label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
            OutlinedTextField(
                value = opValue,
                onValueChange = { opValue = it },
                label = { Text("value for ${op.label}") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    addToken(op.format(opValue)); opValue = ""
                }) { Text("Add ${op.label}") }
                OutlinedButton(onClick = {
                    addToken(op.format(if (opValue.isBlank()) target.trim() else opValue))
                    opValue = ""
                }) { Text("Add for target") }
            }
        }
        Spacer(Modifier.height(12.dp))
        SectionCard {
            Text("Logic & literals", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
            OutlinedTextField(
                value = exactValue,
                onValueChange = { exactValue = it },
                label = { Text("exact phrase") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { addToken(DorkBuilder.exact(exactValue)); exactValue = "" }) { Text("+ \"exact\"") }
                OutlinedButton(onClick = { addToken("AND") }) { Text("+ AND") }
                OutlinedButton(onClick = { addToken("OR") }) { Text("+ OR") }
                OutlinedButton(onClick = { addToken("*") }) { Text("+ *") }
            }
            OutlinedTextField(
                value = excludeValue,
                onValueChange = { excludeValue = it },
                label = { Text("exclude term (-)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = { addToken(DorkBuilder.exclude(excludeValue)); excludeValue = "" }) { Text("+ exclude") }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = rangeFrom,
                    onValueChange = { rangeFrom = it },
                    label = { Text("num from") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = rangeTo,
                    onValueChange = { rangeTo = it },
                    label = { Text("num to") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(onClick = {
                addToken(DorkBuilder.numericRange(rangeFrom, rangeTo)); rangeFrom = ""; rangeTo = ""
            }) { Text("+ range ..") }
        }
        Spacer(Modifier.height(12.dp))
        SectionCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Current tokens", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
                TextButton(onClick = { tokens.clear() }) { Text("Clear", color = NeonMagenta) }
            }
            if (tokens.isEmpty()) {
                Text("(none yet)", style = MaterialTheme.typography.bodySmall)
            } else {
                tokens.forEachIndexed { i, tk ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(tk, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                        TextButton(onClick = { tokens.removeAt(i) }) { Text("✕", color = NeonMagenta) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Final query", style = MaterialTheme.typography.labelMedium, color = NeonCyan)
            OutlinedTextField(
                value = query,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
            Spacer(Modifier.height(8.dp))
            Text("Engine", style = MaterialTheme.typography.labelMedium, color = NeonCyan)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DorkBuilder.Engine.entries.forEach { e ->
                    FilterChip(
                        selected = engine == e,
                        onClick = { engine = e },
                        label = { Text(e.label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { if (query.isNotBlank()) onOpenUrl(DorkBuilder.searchUrl(engine, query)) },
                enabled = query.isNotBlank(),
            ) { Text("Open in new tab") }
        }
    }
}

// --- OSINT Panel tab -----------------------------------------------------------

@Composable
private fun OsintPanelTab(onOpenUrl: (String) -> Unit) {
    var target by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }

    val type = remember(submitted) { OsintSources.classify(submitted) }
    val resolved = remember(submitted) { OsintSources.sourcesFor(submitted) }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        SectionCard {
            Text("Target", style = MaterialTheme.typography.labelMedium, color = NeonCyan)
            OutlinedTextField(
                value = target,
                onValueChange = { target = it },
                label = { Text("domain / IP / username / email / URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { submitted = target.trim() }) { Text("Resolve sources") }
            if (submitted.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Detected type: ${type.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = NeonMagenta,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (submitted.isNotEmpty()) {
            if (resolved.isEmpty()) {
                SectionCard { Text("No sources match this target type.", style = MaterialTheme.typography.bodySmall) }
            } else {
                resolved.forEach { rs ->
                    SectionCard {
                        Text(rs.source.name, style = MaterialTheme.typography.titleSmall, color = NeonCyan)
                        Text(
                            "${rs.source.category.name} — ${rs.source.note}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            rs.url,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        )
                        Spacer(Modifier.height(4.dp))
                        Button(onClick = { onOpenUrl(rs.url) }) { Text("Open") }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

// --- Fingerprint tab -----------------------------------------------------------

@Composable
private fun FingerprintTab() {
    val exchanges by CaptureStore.exchanges.collectAsState()
    // Most recent exchanges that actually have a response.
    val candidates = remember(exchanges) {
        exchanges.filter { it.responseHeaders.isNotEmpty() || it.responseBody != null }.takeLast(40).reversed()
    }
    var selectedId by remember { mutableStateOf<Long?>(null) }

    val result = remember(selectedId, candidates) {
        val ex = candidates.firstOrNull { it.id == selectedId } ?: return@remember null
        val headers = ex.responseHeaders.map { it.name to it.value }
        val html = ex.responseBody?.let { body ->
            val ct = ex.responseContentType()?.lowercase() ?: ""
            if (ct.contains("html") || ct.isEmpty()) {
                runCatching { String(body, Charsets.UTF_8) }.getOrNull()
            } else null
        }
        TechFingerprint.analyze(headers, html)
    }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        SectionCard {
            Text("Passive tech fingerprint", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
            Text(
                "Infers likely server/framework/CMS from an already-captured response " +
                    "(headers + HTML). All results are HEURISTIC GUESSES, not proof. " +
                    "Open a page in the browser first so it appears in capture history.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        SectionCard {
            Text("Captured responses", style = MaterialTheme.typography.labelMedium, color = NeonCyan)
            if (candidates.isEmpty()) {
                Text("(no captured responses yet)", style = MaterialTheme.typography.bodySmall)
            } else {
                candidates.forEach { ex ->
                    val label = "${ex.method} ${ex.host}${runCatching { java.net.URI(ex.url).path }.getOrNull().orEmpty()}"
                    TextButton(onClick = { selectedId = ex.id }) {
                        Text(
                            (if (selectedId == ex.id) "▶ " else "") + label.take(60),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selectedId == ex.id) NeonMagenta else NeonCyan,
                        )
                    }
                }
            }
        }
        result?.let { r ->
            Spacer(Modifier.height(12.dp))
            SectionCard {
                Text("Inferences (heuristic)", style = MaterialTheme.typography.titleSmall, color = NeonMagenta)
                if (r.isEmpty) {
                    Text("No signatures matched.", style = MaterialTheme.typography.bodySmall)
                } else {
                    r.signals.forEach { s ->
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Text(
                            "${s.technology}  [${s.confidence.name}]",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NeonCyan,
                        )
                        Text("${s.category.name} — ${s.evidence}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
