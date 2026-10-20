package com.victorious.browser.scanner

import com.victorious.browser.ui.components.NeonToolHeader
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.victorious.browser.inspect.CaptureStore
import com.victorious.browser.inspect.CapturedExchange
import com.victorious.browser.inspect.EditableRequest
import com.victorious.browser.inspect.RequestEditMerge
import com.victorious.browser.ui.theme.NeonCyan
import com.victorious.browser.ui.theme.NeonMagenta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Burp-style web-testing toolset UI. All intrusive tabs (Active Scan, Header
 * Fuzz, Smuggling, OOB) require an explicit in-scope host and an explicit Run
 * action; nothing runs on browsing traffic and everything is off by default.
 */
@Composable
fun ScannerScreen(
    onBack: () -> Unit,
    onSendToFleet: (List<Finding>) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val config = remember { ScannerConfig.get(context) }
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf("Scope", "Sitemap", "Active Scan", "Header Fuzz", "Smuggling", "OOB")

    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        NeonToolHeader(title = "Scanner", onBack = onBack)
        Text(
            "Burp-lite toolset for your own authorized targets. Active/intrusive " +
                "actions are opt-in, require an in-scope host, and never run on browsing traffic.",
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
            0 -> ScopeTab(config)
            1 -> SitemapTab(config)
            2 -> ActiveScanTab(config, onSendToFleet)
            3 -> HeaderFuzzTab(config)
            4 -> SmugglingTab(config)
            5 -> OobTab(config)
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

@Composable
private fun Mono(label: String, value: String) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = NeonCyan)
    Text(value, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
}

// --- Scope tab -----------------------------------------------------------------

@Composable
private fun ScopeTab(config: ScannerConfig) {
    val scope by config.scopeHosts.collectAsState()
    var entry by remember { mutableStateOf("") }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        SectionCard {
            Text("In-scope hosts (allowlist)", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
            Text(
                "Only hosts listed here can be scanned/fuzzed/probed. Use exact " +
                    "hosts (example.com) or wildcards (*.example.com). Empty = nothing runs.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = entry,
                onValueChange = { entry = it },
                label = { Text("Host or *.host or URL") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    config.addScopeHost(entry)
                    entry = ""
                }) { Text("Add to scope") }
                OutlinedButton(onClick = { config.clearScope() }) { Text("Clear all") }
            }
            Spacer(Modifier.height(8.dp))
            if (scope.isEmpty()) {
                Text("(scope empty — all intrusive tools disabled)", color = NeonMagenta, style = MaterialTheme.typography.bodySmall)
            } else {
                scope.forEach { h ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("• $h", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { config.removeScopeHost(h) }) { Text("Remove", color = NeonMagenta) }
                    }
                }
            }
        }
    }
}

// --- request picker helper -----------------------------------------------------

@Composable
private fun RequestPicker(
    config: ScannerConfig,
    requireScope: Boolean,
    onSelected: (CapturedExchange) -> Unit,
    selected: CapturedExchange?,
) {
    val history by CaptureStore.exchanges.collectAsState()
    SectionCard {
        Text("Select a captured request", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
        if (history.isEmpty()) {
            Text("No captured history yet. Browse a target first.", style = MaterialTheme.typography.bodySmall)
        }
        history.takeLast(40).reversed().forEach { ex ->
            val inScope = config.isInScope(ex.url)
            val isSel = selected?.id == ex.id
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.padding(end = 8.dp)) {
                    Text(
                        "${ex.method} ${ex.url.take(60)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSel) NeonMagenta else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (inScope) "in scope" else "out of scope",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (inScope) NeonCyan else NeonMagenta,
                    )
                }
                TextButton(
                    enabled = !requireScope || inScope,
                    onClick = { onSelected(ex) },
                ) { Text(if (isSel) "Selected" else "Pick") }
            }
        }
    }
}

private fun editableOf(ex: CapturedExchange): EditableRequest = RequestEditMerge.fromExchange(ex)

// --- Sitemap tab ---------------------------------------------------------------

@Composable
private fun SitemapTab(config: ScannerConfig) {
    val history by CaptureStore.exchanges.collectAsState()
    val scope by config.scopeHosts.collectAsState()
    var scopeOnly by remember { mutableStateOf(true) }
    var seed by remember { mutableStateOf("") }
    val crawled = remember { mutableStateListOf<SitemapBuilder.Endpoint>() }
    var crawlStatus by remember { mutableStateOf<String?>(null) }
    val scopeFilter = if (scopeOnly) scope else emptyList()
    val histEndpoints = SitemapBuilder.endpointsFromHistory(history, scopeFilter)
    val merged = SitemapBuilder.mergeEndpoints(histEndpoints, crawled.toList())
    val tree = SitemapBuilder.buildTree(merged)

    Column(Modifier.verticalScroll(rememberScrollState())) {
        SectionCard {
            Text("Site tree from captured history", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { scopeOnly = !scopeOnly }) {
                    Text(if (scopeOnly) "Scope-only: ON" else "Scope-only: OFF", color = NeonCyan)
                }
                Text("${merged.size} endpoints", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(8.dp))
            Text("Optional same-origin crawl (bounded, scope-gated)", style = MaterialTheme.typography.labelMedium, color = NeonMagenta)
            OutlinedTextField(
                value = seed,
                onValueChange = { seed = it },
                label = { Text("Seed URL (must be in scope)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                enabled = seed.isNotBlank() && ScopeMatcher.inScope(seed, scope),
                onClick = {
                    crawlStatus = "crawling…"
                    SitemapCrawler.crawl(
                        seedUrl = seed,
                        scope = scope,
                        onEndpoint = { ep ->
                            if (crawled.none { SitemapBuilder.dedupKey(it.method, it.url) == SitemapBuilder.dedupKey(ep.method, ep.url) }) {
                                crawled.add(ep)
                            }
                        },
                        onDone = { visited -> crawlStatus = "crawled $visited pages" },
                    )
                },
            ) { Text("Crawl (depth 2, max 25)") }
            crawlStatus?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = NeonCyan) }
            if (seed.isNotBlank() && !ScopeMatcher.inScope(seed, scope)) {
                Text("Seed host not in scope.", color = NeonMagenta, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(8.dp))
        SectionCard {
            if (tree.isEmpty()) {
                Text("(no endpoints)", style = MaterialTheme.typography.bodySmall)
            } else {
                tree.forEach { RenderNode(it, 0) }
            }
        }
    }
}

@Composable
private fun RenderNode(node: SitemapBuilder.SiteNode, depth: Int) {
    val isLeaf = node.endpoint != null
    Text(
        "  ".repeat(depth) + (if (isLeaf) "→ " else "▸ ") + node.label,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = if (isLeaf) NeonCyan else MaterialTheme.colorScheme.onSurface,
    )
    node.children.forEach { RenderNode(it, depth + 1) }
}

// --- Active scan tab -----------------------------------------------------------

@Composable
private fun ActiveScanTab(config: ScannerConfig, onSendToFleet: (List<Finding>) -> Unit = {}) {
    val scope by config.scopeHosts.collectAsState()
    val collaborator by config.collaborator.collectAsState()
    var selected by remember { mutableStateOf<CapturedExchange?>(null) }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val findings = remember { mutableStateListOf<Finding>() }
    val uiScope = rememberCoroutineScope()

    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(
            "Opt-in active scanner. Replays ONE selected, in-scope request with a " +
                "small labeled payload set per insertion point. Manual trigger only.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        RequestPicker(config, requireScope = true, selected = selected, onSelected = { selected = it })
        Spacer(Modifier.height(8.dp))
        SectionCard {
            val sel = selected
            if (sel == null) {
                Text("Pick an in-scope request above to enable scanning.", style = MaterialTheme.typography.bodySmall)
            } else {
                Text("Target: ${sel.method} ${sel.url.take(70)}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Button(
                    enabled = !running,
                    onClick = {
                        error = null
                        findings.clear()
                        val canary = "vz" + Random.nextInt(100000, 999999).toString()
                        try {
                            running = true
                            ActiveScanner.run(
                                req = editableOf(sel),
                                config = config,
                                collaborator = collaborator,
                                canary = canary,
                                onProgress = { p -> uiScope.launch { progress = "${p.done}/${p.total} ${p.note}" } },
                                onFinding = { f -> uiScope.launch { findings.add(f) } },
                                onDone = { uiScope.launch { running = false; progress = "complete" } },
                            )
                        } catch (e: ActiveScanner.ScopeViolation) {
                            running = false
                            error = e.message
                        }
                    },
                ) { Text(if (running) "Scanning…" else "Run active scan") }
                progress?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = NeonCyan) }
                error?.let { Text(it, color = NeonMagenta, style = MaterialTheme.typography.bodySmall) }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (findings.isNotEmpty()) {
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Findings (${findings.size})", style = MaterialTheme.typography.titleSmall, color = NeonMagenta)
                    Button(onClick = { onSendToFleet(findings.toList()) }) { Text("Send to fleet") }
                }
                findings.forEach { f ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "[${f.confidence}] ${f.category} @ ${f.insertionPoint.kind} ${f.insertionPoint.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeonCyan,
                    )
                    Mono("payload", f.payloadValue)
                    Text(f.evidence, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// --- Header fuzz tab -----------------------------------------------------------

@Composable
private fun HeaderFuzzTab(config: ScannerConfig) {
    val collaborator by config.collaborator.collectAsState()
    var selected by remember { mutableStateOf<CapturedExchange?>(null) }
    var running by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val results = remember { mutableStateListOf<HeaderFuzzer.Diff>() }
    val uiScope = rememberCoroutineScope()

    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(
            "Sends mutated/added headers (X-Forwarded-For, Host override, …) and " +
                "diffs responses vs baseline. In-scope + manual run only.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        RequestPicker(config, requireScope = true, selected = selected, onSelected = { selected = it })
        Spacer(Modifier.height(8.dp))
        SectionCard {
            val sel = selected
            if (sel == null) {
                Text("Pick an in-scope request.", style = MaterialTheme.typography.bodySmall)
            } else {
                Button(
                    enabled = !running,
                    onClick = {
                        if (!config.isInScope(sel.url)) { error = "Out of scope."; return@Button }
                        error = null
                        results.clear()
                        running = true
                        val evil = collaborator.domain.ifBlank { "evil.example" }
                        ScanRunners.runHeaderFuzz(editableOf(sel), evil) { diffs ->
                            uiScope.launch { results.addAll(diffs); running = false }
                        }
                    },
                ) { Text(if (running) "Fuzzing…" else "Run header fuzz") }
                error?.let { Text(it, color = NeonMagenta, style = MaterialTheme.typography.bodySmall) }
            }
        }
        if (results.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            SectionCard {
                Text("Header diffs", style = MaterialTheme.typography.titleSmall, color = NeonMagenta)
                results.forEach { d ->
                    Text(
                        (if (d.interesting) "★ " else "  ") + "${d.mutation.name}: ${d.mutation.note}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (d.interesting) NeonCyan else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "   status ${d.baselineStatus}→${d.mutatedStatus}, Δlen=${d.lengthDelta}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

// --- Smuggling tab -------------------------------------------------------------

@Composable
private fun SmugglingTab(config: ScannerConfig) {
    var selected by remember { mutableStateOf<CapturedExchange?>(null) }
    var running by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val results = remember { mutableStateListOf<SmugglingProbe.Verdict>() }
    val uiScope = rememberCoroutineScope()

    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(
            "CL.TE / TE.CL request-smuggling DETECTION (heuristic guesses only — " +
                "the transport may rewrite framing; confirm manually). In-scope + manual run.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        RequestPicker(config, requireScope = true, selected = selected, onSelected = { selected = it })
        Spacer(Modifier.height(8.dp))
        SectionCard {
            val sel = selected
            if (sel == null) {
                Text("Pick an in-scope request.", style = MaterialTheme.typography.bodySmall)
            } else {
                Button(
                    enabled = !running,
                    onClick = {
                        if (!config.isInScope(sel.url)) { error = "Out of scope."; return@Button }
                        error = null
                        results.clear()
                        running = true
                        ScanRunners.runSmuggling(editableOf(sel)) { v ->
                            uiScope.launch { results.addAll(v); running = false }
                        }
                    },
                ) { Text(if (running) "Probing…" else "Run smuggling probe") }
                error?.let { Text(it, color = NeonMagenta, style = MaterialTheme.typography.bodySmall) }
            }
        }
        if (results.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            SectionCard {
                Text("Heuristic verdicts", style = MaterialTheme.typography.titleSmall, color = NeonMagenta)
                results.forEach { v ->
                    Text(
                        (if (v.suspicious) "★ " else "  ") + "${v.technique}: ${if (v.suspicious) "SUSPICIOUS" else "no signal"} (heuristic)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (v.suspicious) NeonCyan else MaterialTheme.colorScheme.onSurface,
                    )
                    Text("   ${v.reason}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// --- OOB tab -------------------------------------------------------------------

@Composable
private fun OobTab(config: ScannerConfig) {
    val collaborator by config.collaborator.collectAsState()
    var domain by remember { mutableStateOf(collaborator.domain) }
    var pollUrl by remember { mutableStateOf(collaborator.pollUrl) }
    val interactions = remember { mutableStateListOf<Collaborator.Interaction>() }
    var pollMsg by remember { mutableStateOf<String?>(null) }
    val uiScope = rememberCoroutineScope()

    Column(Modifier.verticalScroll(rememberScrollState())) {
        SectionCard {
            Text("OOB collaborator (bring your own)", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
            Text(
                "No server is bundled. Configure your own interactsh-style domain + " +
                    "HTTP poll endpoint. If empty, the OOB feature is inert and no OOB payloads are generated.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = domain,
                onValueChange = { domain = it },
                label = { Text("Collaborator domain (e.g. abc.oast.pro)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = pollUrl,
                onValueChange = { pollUrl = it },
                label = { Text("Poll endpoint URL (returns JSON)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { config.setCollaborator(domain, pollUrl) }) { Text("Save") }
                OutlinedButton(
                    enabled = Collaborator.Config(domain, pollUrl).configured,
                    onClick = {
                        pollMsg = "polling…"
                        interactions.clear()
                        CollaboratorPoller.poll(Collaborator.Config(domain.trim(), pollUrl.trim())) { list, err ->
                            uiScope.launch {
                                interactions.addAll(list)
                                pollMsg = err ?: "received ${list.size} interaction(s)"
                            }
                        }
                    },
                ) { Text("Poll now") }
            }
            pollMsg?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = NeonCyan) }
            if (!Collaborator.Config(domain, pollUrl).configured) {
                Text("Feature inert until both fields are set.", color = NeonMagenta, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (interactions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            SectionCard {
                Text("Received interactions", style = MaterialTheme.typography.titleSmall, color = NeonMagenta)
                interactions.forEach { i ->
                    Text("• ${i.type} from ${i.sourceHost} @ ${i.timestamp}", style = MaterialTheme.typography.bodySmall, color = NeonCyan)
                }
            }
        }
    }
}
