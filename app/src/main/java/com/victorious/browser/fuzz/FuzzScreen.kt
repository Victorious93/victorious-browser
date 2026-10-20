package com.victorious.browser.fuzz

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
import androidx.compose.material3.Divider
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.victorious.browser.scanner.ScannerConfig
import com.victorious.browser.ui.theme.NeonCyan
import com.victorious.browser.ui.theme.NeonMagenta

/**
 * Fuzz / Templates toolset UI. Three tools:
 *  - Dir Brute: ffuf-style directory/endpoint brute-forcer
 *  - Templates: Nuclei-style compatible-subset template scanner
 *  - Wordlists: SecLists-style wordlist library (import/preview/delete)
 *
 * SAFEGUARD: brute-force and template runs require an explicit in-scope host
 * (shared [ScannerConfig] scope allowlist) and an explicit Run; everything is
 * off by default and never runs on browsing traffic. Concurrency is capped.
 */
@Composable
fun FuzzScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val config = remember { ScannerConfig.get(context) }
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Dir Brute", "Templates", "Wordlists")

    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Fuzz / Templates", style = MaterialTheme.typography.titleLarge, color = NeonMagenta)
            TextButton(onClick = onBack) { Text("Back", color = NeonCyan) }
        }
        Text(
            "ffuf-style brute-force + Nuclei-compatible-subset template scanner for your " +
                "own authorized targets. Runs are off by default, require an in-scope host " +
                "(Scanner scope allowlist) and an explicit Run, and never run on browsing traffic.",
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
            0 -> DirBruteTab(config)
            1 -> TemplatesTab(config)
            2 -> WordlistsTab()
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

// --- Dir Brute tab -------------------------------------------------------------

@Composable
private fun DirBruteTab(config: ScannerConfig) {
    val context = LocalContext.current
    val scope by config.scopeHosts.collectAsState()
    val wordlists = remember { WordlistManager(context) }

    var baseUrl by remember { mutableStateOf("") }
    var wordlistText by remember { mutableStateOf("") }
    var extensions by remember { mutableStateOf("") }
    var concurrency by remember { mutableStateOf("10") }
    var matchCodes by remember { mutableStateOf("") }
    var filterCodes by remember { mutableStateOf("") }
    var filterSizes by remember { mutableStateOf("") }

    var runner by remember { mutableStateOf<DirBruteRunner?>(null) }
    var running by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val hits = remember { mutableStateListOf<FuzzLogic.Hit>() }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        SectionCard {
            Text("Target", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL (with FUZZ marker, or append mode)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "e.g. https://host/FUZZ  or  https://host/  (append mode). Host must be in scope.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "In-scope hosts: " + (if (scope.isEmpty()) "(none — add in Scanner ▸ Scope)" else scope.joinToString()),
                style = MaterialTheme.typography.labelSmall,
                color = if (scope.isEmpty()) NeonMagenta else NeonCyan,
            )
        }
        Spacer(Modifier.height(8.dp))
        SectionCard {
            Text("Wordlist", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
            Text(
                "Paste words (one per line) or load a starter/imported list from Wordlists tab.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = wordlistText,
                onValueChange = { wordlistText = it },
                label = { Text("Words") },
                modifier = Modifier.fillMaxWidth().height(120.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                wordlists.bundledNames().forEach { n ->
                    OutlinedButton(onClick = {
                        wordlistText = runCatching { wordlists.read(n, true) }.getOrDefault("")
                    }) { Text(n.removeSuffix(".txt"), style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        SectionCard {
            Text("Options & filters", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
            OutlinedTextField(
                value = extensions,
                onValueChange = { extensions = it },
                label = { Text("Extensions (comma-sep, e.g. php,bak,txt)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = concurrency,
                onValueChange = { concurrency = it },
                label = { Text("Concurrency (max ${DirBruteRunner.MAX_CONCURRENCY})") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = matchCodes,
                onValueChange = { matchCodes = it },
                label = { Text("Match status codes (blank = defaults)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = filterCodes,
                onValueChange = { filterCodes = it },
                label = { Text("Filter out status codes") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = filterSizes,
                onValueChange = { filterSizes = it },
                label = { Text("Filter out sizes (bytes)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !running,
                onClick = {
                    hits.clear()
                    status = ""
                    val words = WordlistParser.lines(wordlistText)
                    if (words.isEmpty()) { status = "No words to try."; return@Button }
                    val spec = FuzzLogic.FilterSpec(
                        matchCodes = FuzzLogic.parseIntCsv(matchCodes),
                        filterCodes = FuzzLogic.parseIntCsv(filterCodes),
                        filterSizes = FuzzLogic.parseIntCsv(filterSizes),
                    )
                    val r = DirBruteRunner(
                        baseUrl = baseUrl.trim(),
                        words = words,
                        extensions = extensions.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                        filters = spec,
                        scopeHosts = scope,
                        concurrency = concurrency.toIntOrNull() ?: DirBruteRunner.DEFAULT_CONCURRENCY,
                    )
                    try {
                        r.start(
                            onHit = { h -> if (hits.none { it.path == h.path }) hits.add(h) },
                            onProgress = { done, total -> status = "Running $done / $total" },
                            onDone = { running = false; paused = false; status = "Done. ${hits.size} hits." },
                        )
                        runner = r
                        running = true
                    } catch (e: DirBruteRunner.OutOfScopeException) {
                        status = "Refused: ${e.message}"
                    }
                },
            ) { Text("Run") }
            Button(
                enabled = running,
                onClick = {
                    val r = runner ?: return@Button
                    if (paused) { r.resume(); paused = false } else { r.pause(); paused = true }
                },
            ) { Text(if (paused) "Resume" else "Pause") }
            Button(
                enabled = running,
                onClick = { runner?.stop(); running = false; paused = false; status = "Stopped." },
            ) { Text("Stop") }
        }
        if (status.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(status, style = MaterialTheme.typography.labelMedium, color = NeonMagenta)
        }
        Spacer(Modifier.height(8.dp))
        SectionCard {
            Text("Results (path / status / size / words / lines)", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
            if (hits.isEmpty()) {
                Text("No hits yet.", style = MaterialTheme.typography.bodySmall)
            } else {
                hits.forEach { h ->
                    Divider()
                    Text(
                        "${h.status}  ${h.size}b  ${h.words}w  ${h.lines}l  ${h.path}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }
        }
    }
}

// --- Templates tab -------------------------------------------------------------

@Composable
private fun TemplatesTab(config: ScannerConfig) {
    val context = LocalContext.current
    val scope by config.scopeHosts.collectAsState()
    val library = remember { TemplateLibrary(context) }
    val loaded = remember { library.loadAll() }

    var baseUrl by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var runner by remember { mutableStateOf<TemplateRunner?>(null) }
    var running by remember { mutableStateOf(false) }
    val findings = remember { mutableStateListOf<TemplateFinding>() }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        SectionCard {
            Text(
                "Compatible-subset engine — NOT full Nuclei. Supports id, info.name/severity, " +
                    "requests[method/path/headers/body], matchers[status|word|regex|size].",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL (scheme://host) — must be in scope") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "In-scope hosts: " + (if (scope.isEmpty()) "(none — add in Scanner ▸ Scope)" else scope.joinToString()),
                style = MaterialTheme.typography.labelSmall,
                color = if (scope.isEmpty()) NeonMagenta else NeonCyan,
            )
        }
        Spacer(Modifier.height(8.dp))
        SectionCard {
            Text("Templates (${loaded.count { it.template != null }} valid)", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
            loaded.forEach { l ->
                Divider()
                val sev = l.template?.severity?.name ?: "?"
                val tag = if (l.bundled) "[bundled]" else "[imported]"
                if (l.template != null) {
                    Text(
                        "$tag ${l.template.id} — ${l.template.name} ($sev)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(
                        "$tag ${l.name} — PARSE ERROR: ${l.error}",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonMagenta,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !running,
                onClick = {
                    findings.clear()
                    status = ""
                    val templates = library.validTemplates()
                    if (templates.isEmpty()) { status = "No valid templates."; return@Button }
                    val r = TemplateRunner(baseUrl.trim(), templates, scope)
                    try {
                        r.start(
                            onFinding = { f -> findings.add(f) },
                            onProgress = { done, total -> status = "Running $done / $total requests" },
                            onDone = { running = false; status = "Done. ${findings.size} findings." },
                        )
                        runner = r
                        running = true
                    } catch (e: TemplateRunner.OutOfScopeException) {
                        status = "Refused: ${e.message}"
                    }
                },
            ) { Text("Run") }
            Button(
                enabled = running,
                onClick = { runner?.stop(); running = false; status = "Stopped." },
            ) { Text("Stop") }
        }
        if (status.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(status, style = MaterialTheme.typography.labelMedium, color = NeonMagenta)
        }
        Spacer(Modifier.height(8.dp))
        SectionCard {
            Text("Findings", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
            if (findings.isEmpty()) {
                Text("No findings yet.", style = MaterialTheme.typography.bodySmall)
            } else {
                findings.forEach { f ->
                    Divider()
                    Text(
                        "[${f.severity}] ${f.name} (${f.templateId})",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonCyan,
                    )
                    Text(f.url, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace))
                    Text("matched by: ${f.matchedBy.joinToString("; ")}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// --- Wordlists tab -------------------------------------------------------------

@Composable
private fun WordlistsTab() {
    val context = LocalContext.current
    val manager = remember { WordlistManager(context) }
    var refresh by remember { mutableIntStateOf(0) }
    val entries = remember(refresh) { manager.list() }

    var newName by remember { mutableStateOf("") }
    var pasteText by remember { mutableStateOf("") }
    var importUrl by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var previewName by remember { mutableStateOf<String?>(null) }
    var previewBundled by remember { mutableStateOf(false) }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        SectionCard {
            Text("Wordlist library", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
            Text(
                "Bundled starters are small curated lists (lineage: SecLists, MIT — see NOTICE). " +
                    "The full SecLists is NOT bundled; import your own lists below. URL import is capped " +
                    "at ${WordlistManager.MAX_IMPORT_BYTES / 1024} KB (no large downloads).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            entries.forEach { e ->
                Divider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.padding(end = 8.dp)) {
                        Text(
                            "${if (e.bundled) "[bundled] " else "[imported] "}${e.name}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "${e.count} entries, ${e.sizeBytes} bytes",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row {
                        TextButton(onClick = {
                            previewName = e.name; previewBundled = e.bundled
                        }) { Text("Preview", color = NeonCyan) }
                        if (!e.bundled) {
                            TextButton(onClick = {
                                manager.delete(e.name); refresh++; status = "Deleted ${e.name}"
                            }) { Text("Del", color = NeonMagenta) }
                        }
                    }
                }
            }
        }
        previewName?.let { name ->
            Spacer(Modifier.height(8.dp))
            SectionCard {
                Text("Preview: $name", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
                val lines = runCatching { manager.preview(name, previewBundled) }.getOrDefault(emptyList())
                lines.forEach { Text(it, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)) }
                TextButton(onClick = { previewName = null }) { Text("Close", color = NeonMagenta) }
            }
        }
        Spacer(Modifier.height(8.dp))
        SectionCard {
            Text("Import", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = pasteText,
                onValueChange = { pasteText = it },
                label = { Text("Paste words (one per line)") },
                modifier = Modifier.fillMaxWidth().height(100.dp),
            )
            Button(onClick = {
                if (newName.isBlank() || pasteText.isBlank()) { status = "Name and words required."; return@Button }
                val fn = manager.importText(newName, pasteText)
                refresh++; status = "Imported $fn"; pasteText = ""
            }) { Text("Import pasted") }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = importUrl,
                onValueChange = { importUrl = it },
                label = { Text("Import from URL (you provide)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = {
                if (newName.isBlank() || importUrl.isBlank()) { status = "Name and URL required."; return@Button }
                Thread {
                    val result = runCatching { manager.importUrl(newName, importUrl.trim()) }
                    status = result.fold({ "Imported $it" }, { "Import failed: ${it.message}" })
                    refresh++
                }.start()
            }) { Text("Import URL") }
        }
        if (status.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(status, style = MaterialTheme.typography.labelMedium, color = NeonMagenta)
        }
    }
}
