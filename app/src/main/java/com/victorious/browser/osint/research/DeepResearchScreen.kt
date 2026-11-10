package com.victorious.browser.osint.research

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.victorious.browser.ui.components.NeonToolHeader
import com.victorious.browser.ui.theme.NeonCyan
import com.victorious.browser.ui.theme.NeonMagenta
import com.victorious.browser.ui.theme.StatusDanger
import com.victorious.browser.ui.theme.StatusOk
import com.victorious.browser.ui.theme.StatusWarn
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Deep Research (OSINT) hub: dorks, phone / email / username / domain-IP lookups,
 * reverse image search with local metadata, and a research case notebook.
 * Public sources only; lookups open as normal browser tabs or use bounded fetches.
 */
@Composable
fun DeepResearchScreen(
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    /** Opens a tab without leaving the hub (keeps results on screen). */
    onOpenInBackground: (String) -> Unit,
    /** Opens a private tab (used for images handed off from a private tab). */
    onOpenPrivate: (String) -> Unit,
    onSendToFleet: (targets: List<String>) -> Unit,
    onOpenScanner: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val handoff by ResearchHandoff.image.collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(handoff) { if (handoff != null) tab = 5 }
    val tabs = listOf("Dorks", "Phone", "Email", "Username", "Domain / IP", "Image", "Case")
    val context = LocalContext.current
    val store = remember { ResearchCaseStore.get(context) }
    val bg: (String) -> Unit = { u -> onOpenInBackground(u); context.toast("Opened in a background tab") }
    val save: (kind: String, target: String, title: String, body: String, urls: List<String>) -> Unit =
        { kind, target, title, body, urls ->
            val c = store.addEntry(kind, target, title, body, urls)
            context.toast("Saved to “${c.name}”")
        }

    Column(modifier.fillMaxSize().padding(12.dp)) {
        NeonToolHeader(title = "Deep Research", subtitle = "OSINT · public sources", onBack = onBack)
        Text(
            "For authorized security research or your own data. Uses public sources only — no login, paywall " +
                "or captcha bypass. Nothing is saved unless you tap “Save to case”.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp, containerColor = ResearchBg, contentColor = NeonCyan) {
            tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
        }
        Spacer(Modifier.height(6.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (tab) {
                0 -> DorksTab(onOpenUrl, save)
                1 -> PhoneTab(onOpenUrl, save)
                2 -> EmailTab(onOpenUrl, save)
                3 -> UsernameTab(bg, save)
                4 -> DomainIpTab(onOpenUrl, bg, save, onSendToFleet, onOpenScanner)
                5 -> ImageTab(onOpenUrl, onOpenPrivate, save)
                6 -> CaseTab(bg, onSendToFleet)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

internal typealias SaveFn = (kind: String, target: String, title: String, body: String, urls: List<String>) -> Unit

// --- Dorks ---------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DorksTab(onOpenUrl: (String) -> Unit, save: SaveFn) {
    val context = LocalContext.current
    val prefs = remember { ResearchPrefs(context) }
    var domain by rememberSaveable { mutableStateOf("") }
    var keyword by rememberSaveable { mutableStateOf("") }
    var op by remember { mutableStateOf(DorkComposer.Op.SITE) }
    var value by remember { mutableStateOf("") }
    val tokens = remember { mutableStateListOf<String>() }
    var engine by remember { mutableStateOf(DorkComposer.Engine.GOOGLE) }
    var custom by remember { mutableStateOf(prefs.customSearchTemplate) }
    var favorites by remember { mutableStateOf(prefs.favorites) }
    var openCategory by remember { mutableStateOf<String?>(null) }
    val query = DorkComposer.build(tokens)
    val url = DorkComposer.searchUrl(engine, query, custom)

    RCard("Target") {
        OutlinedTextField(domain, { domain = it }, label = { Text("Domain (example.com)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(keyword, { keyword = it }, label = { Text("Keyword / name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
    RCard("Operators") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DorkComposer.Op.entries.forEach { o ->
                FilterChip(selected = op == o, onClick = { op = o }, label = { Text(o.label) })
            }
        }
        if (op != DorkComposer.Op.OR) {
            OutlinedTextField(
                value, { value = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text(if (op == DorkComposer.Op.SITE && value.isEmpty() && domain.isNotBlank()) "value (blank = target domain)" else op.hint) },
            )
        }
        Row {
            Button(onClick = {
                val v = if (op == DorkComposer.Op.SITE && value.isBlank()) domain else value
                val t = DorkComposer.token(op, v)
                if (t.isEmpty()) context.toast(if (op == DorkComposer.Op.BEFORE || op == DorkComposer.Op.AFTER) "Use YYYY, YYYY-MM or YYYY-MM-DD" else "Enter a value")
                else { tokens += t; value = "" }
            }) { Text("+ Add") }
            TextButton(onClick = { tokens.clear() }) { Text("Clear", color = NeonMagenta) }
        }
        tokens.forEachIndexed { i, t ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                TextButton(onClick = { tokens.removeAt(i) }) { Text("✕", color = NeonMagenta) }
            }
        }
    }
    RCard("Query", accent = NeonMagenta) {
        Text(query.ifEmpty { "(empty)" }, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DorkComposer.Engine.entries.forEach { e -> FilterChip(selected = engine == e, onClick = { engine = e }, label = { Text(e.label) }) }
        }
        if (engine == DorkComposer.Engine.CUSTOM) {
            OutlinedTextField(
                custom, { custom = it; prefs.customSearchTemplate = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text("https://search.example/?q=%s") },
                isError = custom.isNotBlank() && !DorkComposer.isValidCustomTemplate(custom),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { url?.let(onOpenUrl) }, enabled = url != null) { Text("Open in new tab") }
            OutlinedButton(onClick = { favorites = (favorites + query); prefs.favorites = favorites; context.toast("Saved favorite") }, enabled = query.isNotEmpty()) { Text("★ Favorite") }
            OutlinedButton(onClick = { save("dork", domain.ifBlank { keyword }, "Dork query", query, listOfNotNull(url)) }, enabled = query.isNotEmpty()) { Text("Save to case") }
        }
    }
    RCard("Dork library (recon templates)") {
        Text("Tap a template to load it. {domain}/{keyword} come from the target fields.", style = MaterialTheme.typography.labelSmall)
        DorkLibrary.categories.forEach { cat ->
            TextButton(onClick = { openCategory = if (openCategory == cat) null else cat }) {
                Text((if (openCategory == cat) "▾ " else "▸ ") + cat, color = NeonCyan)
            }
            if (openCategory == cat) DorkLibrary.all.filter { it.category == cat }.forEach { d ->
                val expanded = DorkLibrary.expand(d, domain, keyword)
                Column(Modifier.padding(start = 12.dp, bottom = 4.dp)) {
                    Text(d.name, style = MaterialTheme.typography.bodyMedium)
                    Text(expanded ?: d.template, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row {
                        TextButton(onClick = {
                            if (expanded == null) context.toast(if (d.needsDomain) "Enter a target domain" else "Enter a keyword")
                            else { tokens.clear(); tokens += expanded }
                        }) { Text("Load") }
                        TextButton(onClick = { expanded?.let { q -> DorkComposer.searchUrl(engine, q, custom)?.let(onOpenUrl) } }, enabled = expanded != null) { Text("Run") }
                    }
                }
            }
        }
    }
    if (favorites.isNotEmpty()) RCard("Favorites") {
        favorites.forEach { f ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(f, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                TextButton(onClick = { DorkComposer.searchUrl(engine, f, custom)?.let(onOpenUrl) }) { Text("Run") }
                TextButton(onClick = { favorites = favorites - f; prefs.favorites = favorites }) { Text("✕", color = NeonMagenta) }
            }
        }
    }
}

// --- Phone ---------------------------------------------------------------------

@Composable
private fun PhoneTab(onOpenUrl: (String) -> Unit, save: SaveFn) {
    val context = LocalContext.current
    var number by rememberSaveable { mutableStateOf("") }
    var region by rememberSaveable { mutableStateOf(Locale.getDefault().country.ifBlank { "US" }) }
    var outcome by remember { mutableStateOf<PhoneLookup.Outcome?>(null) }
    RCard("Phone number") {
        OutlinedTextField(number, { number = it }, label = { Text("+1 202 555 0143") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
        OutlinedTextField(region, { region = it.take(2).uppercase() }, label = { Text("Default region (if no +code)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = { outcome = PhoneLookup.analyze(number, region) }) { Text("Analyze (offline)") }
        Text("Parsed on the phone with libphonenumber. Carrier = original carrier; numbers can be ported.", style = MaterialTheme.typography.labelSmall)
    }
    when (val o = outcome) {
        is PhoneLookup.Outcome.Error -> RCard("Result", accent = StatusDanger) { Text(o.message) }
        is PhoneLookup.Outcome.Ok -> {
            val r = o.result
            val text = buildString {
                appendLine("Valid: ${r.valid} (possible: ${r.possible})")
                appendLine("Country: +${r.countryCode} ${r.regionCode ?: "?"}")
                r.location?.let { appendLine("Location: $it") }
                r.carrier?.let { appendLine("Carrier (original): $it") }
                appendLine("Line type: ${r.lineType}")
                if (r.timeZones.isNotEmpty()) appendLine("Time zones: ${r.timeZones.joinToString()}")
                appendLine("E.164: ${r.e164}")
                appendLine("International: ${r.international}")
                append("National: ${r.national}")
            }
            RCard("Result", accent = if (r.valid) StatusOk else StatusWarn) {
                KV("Valid", if (r.valid) "yes" else if (r.possible) "possible length, not a valid number" else "no")
                KV("Country", "+${r.countryCode} ${r.regionCode ?: "?"}")
                KV("Location", r.location)
                KV("Carrier", r.carrier ?: "not in offline data")
                KV("Line type", r.lineType)
                KV("Time zones", r.timeZones.joinToString().ifBlank { null })
                KV("E.164", r.e164, mono = true)
                KV("International", r.international, mono = true)
                KV("National", r.national, mono = true)
                SaveRow(onSave = { save("phone", r.e164, "Phone ${r.e164}", text, emptyList()) }, onCopy = { context.copyToClipboard(text) })
            }
            RCard("Public pivots (open as tabs)") { PivotList(PhoneLookup.pivots(r), onOpenUrl) }
        }
        null -> {}
    }
}

// --- Email ---------------------------------------------------------------------

@Composable
private fun EmailTab(onOpenUrl: (String) -> Unit, save: SaveFn) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val net = remember { ResearchNet(context) }
    val secrets = remember { SecretBox(context) }
    var email by rememberSaveable { mutableStateOf("") }
    var syntax by remember { mutableStateOf<EmailChecks.Syntax?>(null) }
    var busy by remember { mutableStateOf(false) }
    var mx by remember { mutableStateOf<List<EmailChecks.Mx>?>(null) }
    var spf by remember { mutableStateOf<EmailChecks.Spf?>(null) }
    var dmarc by remember { mutableStateOf<EmailChecks.Dmarc?>(null) }
    var gravatar by remember { mutableStateOf<Boolean?>(null) }
    var dnsError by remember { mutableStateOf<String?>(null) }
    var extraDisposable by remember { mutableStateOf<Set<String>>(emptySet()) }
    var hasKey by remember { mutableStateOf(secrets.has(SecretBox.HIBP)) }
    var keyInput by remember { mutableStateOf("") }
    var hibp by remember { mutableStateOf<Hibp.Outcome?>(null) }

    RCard("Email address") {
        OutlinedTextField(email, { email = it }, label = { Text("name@example.com") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                val s = EmailChecks.syntax(email); syntax = s; mx = null; spf = null; dmarc = null; gravatar = null; dnsError = null; hibp = null
                if (s.valid) scope.launch {
                    busy = true
                    val d = s.domain
                    val mxAns = net.dns(d, "MX")
                    mx = mxAns.getOrNull()?.let { a -> EmailChecks.parseMx(a.records.filter { it.type == "MX" }.map { it.data }) }
                    dnsError = mxAns.exceptionOrNull()?.message
                    spf = EmailChecks.parseSpf(net.txt(d))
                    dmarc = EmailChecks.parseDmarc(net.txt("_dmarc.$d"))
                    gravatar = net.gravatarExists(email)
                    busy = false
                }
            }) { Text("Check") }
            if (busy) CircularProgressIndicator(Modifier.padding(start = 12.dp), color = NeonCyan)
        }
        Text("DNS via Cloudflare DNS-over-HTTPS. Gravatar check uses the public avatar URL (hash of the address).", style = MaterialTheme.typography.labelSmall)
    }
    val s = syntax ?: return
    val report = buildString {
        appendLine("Syntax: ${if (s.valid) "valid" else "invalid (${s.problems.joinToString()})"}")
        if (s.valid) {
            appendLine("Disposable domain: ${if (DisposableDomains.isDisposable(s.domain, extraDisposable)) "yes" else "not listed"}")
            mx?.let { l -> appendLine("MX: " + (if (l.isEmpty()) "none" else l.joinToString { "${it.priority} ${it.host}" })) }
            EmailChecks.mailProvider(mx.orEmpty())?.let { appendLine("Provider (guess): $it") }
            spf?.let { appendLine(it.summary + (it.record?.let { r -> " — $r" } ?: "")) }
            dmarc?.let { appendLine(it.summary + (it.record?.let { r -> " — $r" } ?: "")) }
            gravatar?.let { appendLine("Gravatar: ${if (it) "avatar exists" else "none"}") }
        }
    }.trimEnd()
    RCard("Result", accent = if (s.valid) StatusOk else StatusDanger) {
        Text(report, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
        dnsError?.let { Text("DNS: $it", color = StatusWarn, style = MaterialTheme.typography.labelSmall) }
        if (s.valid) Row {
            TextButton(onClick = {
                scope.launch {
                    net.disposableRemote().onSuccess { extraDisposable = it; context.toast("Loaded ${it.size} disposable domains") }
                        .onFailure { context.toast("Couldn't load list: ${it.message}") }
                }
            }) { Text("Refresh disposable list (CC0)") }
        }
        SaveRow(onSave = { save("email", EmailChecks.normalize(email), "Email ${EmailChecks.normalize(email)}", report, emptyList()) }, onCopy = { context.copyToClipboard(report) })
    }
    if (!s.valid) return
    RCard("Breach check — Have I Been Pwned (your own API key)", accent = NeonMagenta) {
        if (!hasKey) {
            Text("Optional. Paste your HIBP API key; it is encrypted with the Android Keystore and stays on this phone.", style = MaterialTheme.typography.labelSmall)
            OutlinedTextField(keyInput, { keyInput = it }, label = { Text("HIBP API key") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation())
            Button(onClick = { secrets.put(SecretBox.HIBP, keyInput); keyInput = ""; hasKey = secrets.has(SecretBox.HIBP) }, enabled = keyInput.isNotBlank()) { Text("Save key") }
        } else {
            Row {
                Button(onClick = {
                    scope.launch {
                        busy = true
                        hibp = secrets.get(SecretBox.HIBP)?.let { net.hibp(EmailChecks.normalize(email), it) } ?: Hibp.Outcome.Failed("Key unreadable — re-enter it")
                        busy = false
                    }
                }) { Text("Check breaches") }
                TextButton(onClick = { secrets.put(SecretBox.HIBP, ""); hasKey = false; hibp = null }) { Text("Remove key", color = NeonMagenta) }
            }
        }
        when (val h = hibp) {
            is Hibp.Outcome.Breached -> {
                val txt = "HIBP: in ${h.breaches.size} breach(es): " + h.breaches.joinToString { b -> "${b.name} (${b.date ?: "?"})" }
                Text(txt, color = StatusDanger, style = MaterialTheme.typography.bodySmall)
                h.breaches.forEach { b -> Text("• ${b.name} ${b.date ?: ""} — ${b.dataClasses.take(6).joinToString()}", style = MaterialTheme.typography.labelSmall) }
                TextButton(onClick = { save("email", EmailChecks.normalize(email), "HIBP breaches", txt, emptyList()) }) { Text("Save to case", color = NeonMagenta) }
            }
            Hibp.Outcome.NotFound -> Text("HIBP: not found in any breach", color = StatusOk)
            is Hibp.Outcome.Failed -> Text("HIBP: ${h.message}", color = StatusWarn)
            null -> {}
        }
    }
    RCard("Public pivots (open as tabs)") { PivotList(EmailChecks.pivots(email), onOpenUrl) }
}
