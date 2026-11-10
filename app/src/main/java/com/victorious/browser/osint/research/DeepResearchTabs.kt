package com.victorious.browser.osint.research

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.victorious.browser.evidence.EvidenceStore
import com.victorious.browser.ui.theme.NeonCyan
import com.victorious.browser.ui.theme.NeonMagenta
import com.victorious.browser.ui.theme.StatusDanger
import com.victorious.browser.ui.theme.StatusIdle
import com.victorious.browser.ui.theme.StatusOk
import com.victorious.browser.ui.theme.StatusWarn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

// --- Username ------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UsernameTab(onOpenUrl: (String) -> Unit, save: SaveFn) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val net = remember { ResearchNet(context) }
    var username by rememberSaveable { mutableStateOf("") }
    var sites by remember { mutableStateOf(net.bundledSites()) }
    var fullList by remember { mutableStateOf(false) }
    val results = remember { mutableStateListOf<UsernameSearch.Check>() }
    var job by remember { mutableStateOf<Job?>(null) }
    var filter by remember { mutableStateOf<UsernameSearch.Verdict?>(UsernameSearch.Verdict.FOUND) }
    val running = job?.isActive == true

    RCard("Username") {
        OutlinedTextField(username, { username = it.trim() }, label = { Text("username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text(
            "${sites.size} sites (${if (fullList) "Sherlock list, fetched now" else "bundled starter list"}). " +
                "4 requests at a time with a short pause. ‘Unknown’ means the site didn't give a clear answer.",
            style = MaterialTheme.typography.labelSmall,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = {
                    results.clear()
                    job = scope.launch {
                        net.checkUsername(username, sites) { c -> scope.launch(Dispatchers.Main) { results += c } }
                    }
                },
                enabled = !running && UsernameSearch.isSearchable(username) && sites.isNotEmpty(),
            ) { Text("Search") }
            if (running) OutlinedButton(onClick = { job?.cancel() }) { Text("Stop") }
            if (!fullList) OutlinedButton(onClick = {
                scope.launch {
                    net.sherlockSites().onSuccess { sites = it; fullList = true; context.toast("Loaded ${it.size} sites") }
                        .onFailure { context.toast("Couldn't load Sherlock list: ${it.message}") }
                }
            }, enabled = !running) { Text("Load full Sherlock list") }
        }
        if (running || results.isNotEmpty()) {
            LinearProgressIndicator(progress = { if (sites.isEmpty()) 0f else results.size / sites.size.toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = NeonCyan)
        }
    }
    if (results.isEmpty()) return
    val counts = UsernameSearch.summarize(results)
    RCard("Results") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("All ${results.size}") })
            UsernameSearch.Verdict.entries.forEach { v ->
                FilterChip(selected = filter == v, onClick = { filter = v }, label = { Text("${v.name.lowercase().replace('_', ' ')} ${counts[v]}") })
            }
        }
        results.filter { filter == null || it.verdict == filter }.sortedBy { it.site.name.lowercase() }.forEach { c ->
            val color = when (c.verdict) {
                UsernameSearch.Verdict.FOUND -> StatusOk
                UsernameSearch.Verdict.NOT_FOUND -> StatusIdle
                UsernameSearch.Verdict.UNKNOWN -> StatusWarn
                UsernameSearch.Verdict.SKIPPED -> StatusIdle
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${c.site.name} — ${c.verdict.name.lowercase().replace('_', ' ')}", color = color, style = MaterialTheme.typography.bodyMedium)
                    Text(c.detail, style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = { onOpenUrl(c.url) }) { Text("Open") }
            }
        }
        val found = results.filter { it.verdict == UsernameSearch.Verdict.FOUND }
        TextButton(onClick = {
            save("username", username, "Username '$username' — ${found.size} found",
                "Found: ${found.size}, not found: ${counts[UsernameSearch.Verdict.NOT_FOUND]}, unknown: ${counts[UsernameSearch.Verdict.UNKNOWN]}\n" +
                    found.joinToString("\n") { "${it.site.name}: ${it.url}" },
                found.map { it.url })
        }, enabled = !running) { Text("Save found to case", color = NeonMagenta) }
    }
}

// --- Domain / IP ---------------------------------------------------------------

@Composable
internal fun DomainIpTab(
    onOpenUrl: (String) -> Unit,
    onOpenInBackground: (String) -> Unit,
    save: SaveFn,
    onSendToFleet: (List<String>) -> Unit,
    onOpenScanner: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val net = remember { ResearchNet(context) }
    var input by rememberSaveable { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var isIp by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var dns by remember { mutableStateOf<List<Doh.Record>>(emptyList()) }
    var rdap by remember { mutableStateOf<Result<Rdap.Info>?>(null) }
    var subs by remember { mutableStateOf<Result<Pair<List<String>, Boolean>>?>(null) }
    var provider by remember { mutableStateOf(Doh.Provider.CLOUDFLARE) }

    RCard("Domain or IP") {
        OutlinedTextField(input, { input = it }, label = { Text("example.com or 93.184.216.34") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row {
            Doh.Provider.entries.forEach { p -> FilterChip(selected = provider == p, onClick = { provider = p }, label = { Text("DoH ${p.label}") }, modifier = Modifier.padding(end = 4.dp)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                val t = input.trim().lowercase().removePrefix("https://").removePrefix("http://").substringBefore('/').trimEnd('.')
                target = t; isIp = InfraPivots.isIpv4(t) || InfraPivots.isIpv6(t); dns = emptyList(); rdap = null; subs = null
                if (!isIp && !Doh.isValidName(t)) { context.toast("Not a domain or IP"); return@Button }
                scope.launch {
                    busy = true
                    if (isIp) {
                        Doh.ptrName(t)?.let { ptr -> dns = net.dns(ptr, "PTR", provider).getOrNull()?.records.orEmpty() }
                        rdap = net.rdap(Rdap.ipUrl(t))
                    } else {
                        val all = mutableListOf<Doh.Record>()
                        for (type in listOf("A", "AAAA", "CNAME", "MX", "NS", "TXT", "CAA", "SOA")) {
                            net.dns(t, type, provider).getOrNull()?.records?.let { all += it }
                        }
                        dns = all.distinct()
                        rdap = net.rdap(Rdap.domainUrl(t))
                    }
                    busy = false
                }
            }) { Text("Look up") }
            if (busy) CircularProgressIndicator(Modifier.padding(start = 12.dp), color = NeonCyan)
        }
    }
    if (target.isEmpty()) return
    val dnsText = dns.joinToString("\n") { "${it.type.padEnd(5)} ${it.name}  ${it.data}" }
    RCard("DNS (DoH ${provider.label})") {
        Text(dnsText.ifEmpty { if (busy) "…" else "No records" }, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
        if (dns.isNotEmpty()) SaveRow(onSave = { save(if (isIp) "ip" else "domain", target, "DNS records", dnsText, emptyList()) }, onCopy = { context.copyToClipboard(dnsText) })
    }
    rdap?.let { res ->
        RCard("RDAP / WHOIS") {
            res.onFailure { Text(it.message ?: "Lookup failed", color = StatusWarn) }
            res.onSuccess { i ->
                val txt = buildString {
                    i.name?.let { appendLine("Name: $it") }
                    i.handle?.let { appendLine("Handle: $it") }
                    i.range?.let { appendLine("Range: $it") }
                    i.country?.let { appendLine("Country: $it") }
                    if (i.status.isNotEmpty()) appendLine("Status: ${i.status.joinToString()}")
                    i.events.forEach { (a, d) -> appendLine("$a: $d") }
                    if (i.nameservers.isNotEmpty()) appendLine("Nameservers: ${i.nameservers.joinToString()}")
                    i.entities.forEach { (r, n) -> appendLine("$r: $n") }
                }.trimEnd()
                Text(txt, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                SaveRow(onSave = { save(if (isIp) "ip" else "domain", target, "RDAP", txt, emptyList()) }, onCopy = { context.copyToClipboard(txt) })
            }
        }
    }
    if (!isIp) RCard("Subdomains — certificate transparency (crt.sh)") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { scope.launch { busy = true; subs = net.crtSh(target); busy = false } }, enabled = !busy) { Text("Enumerate") }
        }
        subs?.onFailure { Text("crt.sh: ${it.message} (it is often slow — retry)", color = StatusWarn) }
        subs?.onSuccess { (list, truncated) ->
            Text("${list.size} names" + if (truncated) " (response was capped at 4 MB — partial)" else "", color = NeonMagenta)
            list.take(300).forEach { n ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(n, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                    TextButton(onClick = { onOpenInBackground("https://$n") }) { Text("Open") }
                }
            }
            SaveRow(onSave = { save("domain", target, "Subdomains (crt.sh)", list.joinToString("\n"), emptyList()) }, onCopy = { context.copyToClipboard(list.joinToString("\n")) })
            TextButton(onClick = { onSendToFleet(list) }) { Text("Stage subdomains for fleet export", color = NeonCyan) }
        }
    }
    RCard("Send to tools") {
        Row {
            TextButton(onClick = { onSendToFleet(listOf(target)) }) { Text("Stage for fleet export") }
            TextButton(onClick = { context.copyToClipboard("https://$target", "Target URL copied — paste it in the scanner"); onOpenScanner() }) { Text("Open scanner") }
        }
    }
    RCard("Public pivots (open as tabs)") { PivotList(if (isIp) InfraPivots.forIp(target) else InfraPivots.forDomain(target), onOpenUrl) }
}

// --- Reverse image -------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ImageTab(onOpenNormal: (String) -> Unit, onOpenPrivate: (String) -> Unit, save: SaveFn) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val net = remember { ResearchNet(context) }
    val prefs = remember { ResearchPrefs(context) }
    val handoff by ResearchHandoff.image.collectAsState()
    var imageUrl by rememberSaveable { mutableStateOf("") }
    var localName by remember { mutableStateOf<String?>(null) }
    var fromPrivate by remember { mutableStateOf(false) }
    var meta by remember { mutableStateOf<ImageMetadata.Summary?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var custom by remember { mutableStateOf(prefs.customImageTemplate) }
    // Images that came from a private tab are searched in a private tab too.
    val onOpenUrl: (String) -> Unit = { u -> if (fromPrivate) onOpenPrivate(u) else onOpenNormal(u) }

    LaunchedEffect(handoff) {
        ResearchHandoff.consumeImage()?.let { req -> imageUrl = req.url; localName = null; meta = null; error = null; fromPrivate = req.fromPrivateTab }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            busy = true; error = null; meta = null; imageUrl = ""; fromPrivate = false
            localName = uri.lastPathSegment ?: "picked image"
            withContext(Dispatchers.IO) { ImageInspector.readUri(context, uri).map { ImageInspector.inspect(it) } }
                .onSuccess { meta = it }.onFailure { error = it.message }
            busy = false
        }
    }

    if (fromPrivate) RCard(accent = NeonMagenta) {
        Text("From a private tab: nothing is fetched or saved until you tap a button below.", style = MaterialTheme.typography.labelSmall)
    }
    RCard("Image") {
        OutlinedTextField(imageUrl, { imageUrl = it; localName = null }, label = { Text("Image URL (https://…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { picker.launch("image/*") }) { Text("Pick from device") }
            OutlinedButton(onClick = {
                scope.launch {
                    busy = true; error = null; meta = null
                    net.fetchImage(imageUrl.trim()).map { withContext(Dispatchers.Default) { ImageInspector.inspect(it) } }
                        .onSuccess { meta = it }.onFailure { error = it.message }
                    busy = false
                }
            }, enabled = ReverseImage.isShareableImageUrl(imageUrl) && !busy) { Text("Fetch & read metadata") }
        }
        Text("Tip: long-press an image in any tab → “Reverse image search”.", style = MaterialTheme.typography.labelSmall)
        localName?.let { Text("Local file: $it (stays on this phone)", color = NeonMagenta, style = MaterialTheme.typography.labelSmall) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = NeonCyan)
        error?.let { Text(it, color = StatusDanger) }
    }
    RCard("Search engines", accent = NeonMagenta) {
        val hasUrl = ReverseImage.isShareableImageUrl(imageUrl)
        Text(
            if (hasUrl) "Opens each engine's search-by-URL page in a new tab."
            else "Opens the engine's upload page in a new tab — tap its upload button and choose your image.",
            style = MaterialTheme.typography.labelSmall,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ReverseImage.Engine.entries.forEach { e ->
                OutlinedButton(onClick = {
                    onOpenUrl(if (hasUrl) ReverseImage.byUrl(e, imageUrl)!! else ReverseImage.uploadPage(e))
                }) { Text(e.label) }
            }
            if (hasUrl && ReverseImage.isValidCustomTemplate(custom)) OutlinedButton(onClick = { ReverseImage.custom(custom, imageUrl)?.let(onOpenUrl) }) { Text("Custom") }
        }
        OutlinedTextField(custom, { custom = it; prefs.customImageTemplate = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text("Custom engine: https://…{url}") }, isError = custom.isNotBlank() && !ReverseImage.isValidCustomTemplate(custom))
        if (hasUrl) TextButton(onClick = {
            save("image", imageUrl, "Reverse image search links", imageUrl, ReverseImage.allByUrl(imageUrl, custom).map { it.url })
        }) { Text("Save links to case", color = NeonMagenta) }
    }
    meta?.let { m ->
        val source = localName ?: imageUrl
        val text = m.toText(source)
        RCard("Metadata (read on this phone)") {
            KV("Dimensions", if (m.width != null) "${m.width}×${m.height}" else null)
            KV("Type", m.mime)
            KV("Size", ImageMetadata.humanSize(m.sizeBytes))
            KV("MD5", m.md5, mono = true)
            KV("SHA-256", m.sha256, mono = true)
            KV("dHash", m.dHash, mono = true)
            if (m.hasGps) {
                Text(String.format(Locale.US, "GPS %.6f, %.6f", m.lat, m.lon), color = StatusWarn)
                TextButton(onClick = { onOpenUrl(ImageMetadata.mapsUrl(m.lat!!, m.lon!!)) }) { Text("Open location map") }
            }
            if (m.exif.isEmpty()) Text("No EXIF found (many sites strip it).", style = MaterialTheme.typography.labelSmall)
            m.exif.forEach { (k, v) -> KV(k, v) }
            Row {
                TextButton(onClick = { context.copyToClipboard(text, "Metadata copied") }) { Text("Copy EXIF") }
                TextButton(onClick = { save("image", source, "Image metadata", text, if (m.hasGps) listOf(ImageMetadata.mapsUrl(m.lat!!, m.lon!!)) else emptyList()) }) {
                    Text("Save to case", color = NeonMagenta)
                }
            }
        }
    }
}

// --- Case notebook -------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CaseTab(onOpenUrl: (String) -> Unit, onSendToFleet: (List<String>) -> Unit) {
    val context = LocalContext.current
    val store = remember { ResearchCaseStore.get(context) }
    val cases by store.cases.collectAsState()
    val activeId by store.activeId.collectAsState()
    val active = cases.firstOrNull { it.id == activeId }
    var newName by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }

    RCard("Cases") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            cases.forEach { c -> FilterChip(selected = c.id == activeId, onClick = { store.setActive(c.id) }, label = { Text("${c.name} (${c.entries.size})") }) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(newName, { newName = it }, label = { Text("New case name") }, singleLine = true, modifier = Modifier.weight(1f))
            TextButton(onClick = { store.create(newName); newName = "" }) { Text("Create") }
        }
        Text("Cases are stored only in this app's private storage.", style = MaterialTheme.typography.labelSmall)
    }
    if (active == null) return
    RCard(active.name, accent = NeonMagenta) {
        Text("Targets: " + active.targets.joinToString().ifEmpty { "(none)" }, style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(target, { target = it }, label = { Text("Add target") }, singleLine = true, modifier = Modifier.weight(1f))
            TextButton(onClick = { store.addTarget(target); target = "" }, enabled = target.isNotBlank()) { Text("Add") }
        }
        OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        TextButton(onClick = { store.addEntry("note", active.targets.firstOrNull() ?: "", "Note", note); note = "" }, enabled = note.isNotBlank()) { Text("Add note") }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { context.shareText(ResearchCaseCodec.toMarkdown(active), "${active.name}.md") }) { Text("Export Markdown") }
            OutlinedButton(onClick = { context.shareText(ResearchCaseCodec.toJson(active).toString(2), "${active.name}.json") }) { Text("Export JSON") }
            OutlinedButton(onClick = {
                val ev = EvidenceStore.get(context)
                val evCase = ev.ensureActiveCase()
                ev.addNote(evCase.id, active.targets.firstOrNull() ?: "research", "Deep Research: ${active.name}", ResearchCaseCodec.toMarkdown(active))
                context.toast("Added to evidence case “${evCase.name}”")
            }) { Text("Send to Evidence") }
            OutlinedButton(onClick = { onSendToFleet(active.targets) }, enabled = active.targets.isNotEmpty()) { Text("Fleet export") }
            TextButton(onClick = { store.delete(active.id) }) { Text("Delete case", color = StatusDanger) }
        }
    }
    active.entries.sortedByDescending { it.timestamp }.forEach { e ->
        RCard("${ResearchCaseCodec.isoTime(e.timestamp)} · ${e.kind}") {
            Text(e.title, style = MaterialTheme.typography.bodyMedium, color = NeonCyan)
            if (e.target.isNotBlank()) Text("Target: ${e.target}", style = MaterialTheme.typography.labelSmall)
            if (e.body.isNotBlank()) Text(e.body.take(2000), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
            e.urls.take(20).forEach { u -> TextButton(onClick = { onOpenUrl(u) }) { Text(u.take(80), style = MaterialTheme.typography.labelSmall) } }
            TextButton(onClick = { store.removeEntry(active.id, e.id) }) { Text("Remove", color = StatusDanger) }
        }
    }
}
