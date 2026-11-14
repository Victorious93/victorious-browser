package com.victorious.browser.victorlinux

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.victorious.browser.ui.theme.CharcoalElevated
import com.victorious.browser.ui.theme.NeonCyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private fun gib(b: Long?) = if (b == null) "?" else "%.1f GiB".format(b / 1073741824.0)

/**
 * VictorLinux companion: pair with the PC's gateway, see its real hardware and model fit, and chat with its
 * local model. Always dark and rounded. The key is write-only here and is sent only to the saved origin.
 */
@Composable
fun VictorLinuxScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val store = remember { VictorProfileStore.get(ctx) }
    val profiles by store.profiles.collectAsState()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<VictorProfile?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var states by remember { mutableStateOf<Map<String, CompanionState>>(emptyMap()) }
    var busy by remember { mutableStateOf(false) }

    fun refresh(p: VictorProfile) = scope.launch {
        busy = true
        val st = withContext(Dispatchers.IO) { CompanionClient(p, { store.keys.get(p.id, p.origin) }).fetchStatus() }
        states = states + (p.id to st)
        if (st is CompanionState.Online) {
            // First successful pairing pins the PC identity; later runs verify it. Model memory powers "model changed".
            store.upsert(p.copy(pcId = p.pcId ?: st.report.pcId, lastModel = st.report.model))
        }
        busy = false
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack, shape = RoundedCornerShape(50)) { Text("Back") }
            Text("VictorLinux PCs", style = MaterialTheme.typography.titleLarge, color = NeonCyan)
        }
        Text(
            "This phone can only reach your PC over your LAN, VPN/mesh or a tunnel you run. 127.0.0.1 here is the phone itself. " +
                "Inference stays on your PC: no cloud fallback, no telemetry. The key is stored in the Android Keystore and sent only to the saved address.",
            style = MaterialTheme.typography.bodySmall,
        )
        profiles.forEach { p ->
            Card {
                Text(p.name, style = MaterialTheme.typography.titleMedium)
                Text(p.origin + if (p.pinnedCertSha256 != null) "  ·  certificate pinned" else "", style = MaterialTheme.typography.bodySmall)
                val st = states[p.id]
                StateView(st, onRepair = { store.upsert(p.copy(pcId = null)); states = states - p.id })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { refresh(p) }, enabled = !busy, shape = RoundedCornerShape(50)) { Text(if (busy) "Checking…" else "Check PC") }
                    OutlinedButton(onClick = { editing = p }, shape = RoundedCornerShape(50)) { Text("Edit") }
                    OutlinedButton(onClick = { selected = if (selected == p.id) null else p.id }, enabled = st is CompanionState.Online, shape = RoundedCornerShape(50)) { Text("Chat") }
                }
                if (selected == p.id && st is CompanionState.Online) ChatPanel(p, store)
            }
        }
        if (editing == null) Button(onClick = { editing = VictorProfile(UUID.randomUUID().toString(), "My VictorLinux PC", "") }, shape = RoundedCornerShape(50)) { Text("Add a PC") }
        editing?.let { p -> EditCard(p, store, onDone = { editing = null }) }
    }
}

@Composable
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(CharcoalElevated, RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
private fun StateView(st: CompanionState?, onRepair: () -> Unit) {
    when (st) {
        null -> Text("Not checked yet.", style = MaterialTheme.typography.bodySmall)
        is CompanionState.Online -> {
            val r = st.report
            Text("Online: ${r.pcName}", color = NeonCyan)
            Text("RAM ${gib(r.ramAvailable)} free of ${gib(r.ramTotal)} · " + (if (r.gpus.isEmpty()) "no GPU enumerated" else r.gpus.joinToString()), style = MaterialTheme.typography.bodySmall)
            Text("Backend ${r.backend ?: "none"} · memory pool ${r.memoryPool ?: "?"} · budget ${gib(r.memoryBudget)}", style = MaterialTheme.typography.bodySmall)
            Text(
                if (r.running) "Running ${r.model} · context ${r.context} · GPU layers ${r.gpuLayers} · " +
                    (if (r.generationCheck == "verified") "generated a token on this PC" else "not yet proven to generate")
                else "No model is running.", style = MaterialTheme.typography.bodySmall,
            )
            r.models.forEach { m ->
                Text("• ${m.name}: ${m.fit}${m.context?.let { c -> " at $c context" } ?: ""} · " +
                    (if (m.verified == "verified-here") "verified here" else "estimate only, not tested here"), style = MaterialTheme.typography.bodySmall)
            }
            st.notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
        }
        is CompanionState.Offline -> Text("Offline: ${st.reason}", color = MaterialTheme.colorScheme.error)
        CompanionState.BadKey -> Text("The PC rejected the key. It may have been rotated or revoked. Create a new key in the PC's Control Center and save it here.", color = MaterialTheme.colorScheme.error)
        is CompanionState.WrongHost -> Text("Not connected: ${st.reason}", color = MaterialTheme.colorScheme.error)
        is CompanionState.ChangedPc -> {
            Text("A different PC answered at this address. Pinned ${st.pinned.take(8)}…, now ${st.now.take(8)}… (${st.report.pcName}). Nothing was sent.", color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onRepair, shape = RoundedCornerShape(50)) { Text("Trust this PC instead (re-pair)") }
        }
        is CompanionState.Unsupported -> Text(st.reason, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun EditCard(p: VictorProfile, store: VictorProfileStore, onDone: () -> Unit) {
    var name by remember(p.id) { mutableStateOf(p.name) }
    var address by remember(p.id) { mutableStateOf(p.origin) }
    var tunnel by remember(p.id) { mutableStateOf(p.tunnel) }
    var pin by remember(p.id) { mutableStateOf(p.pinnedCertSha256 ?: "") }
    var key by remember(p.id) { mutableStateOf("") }
    var error by remember(p.id) { mutableStateOf("") }
    Card {
        Text(if (p.origin.isEmpty()) "Add a PC" else "Edit PC", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        OutlinedTextField(address, { address = it }, label = { Text("PC address, e.g. https://192.168.1.20:8080") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(tunnel, { tunnel = it }); Text("This address is a tunnel to the PC (adb reverse / SSH forward)", style = MaterialTheme.typography.bodySmall) }
        OutlinedTextField(pin, { pin = it }, label = { Text("Certificate SHA-256 (self-signed PCs only)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        OutlinedTextField(key, { key = it }, label = { Text(if (store.keys.has(p.id, p.origin)) "New API key (leave empty to keep the saved one)" else "API key") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                when (val o = OriginPolicy.parse(address, tunnel)) {
                    is OriginPolicy.Result.Rejected -> error = o.reason
                    is OriginPolicy.Result.Ok -> {
                        if (pin.isNotBlank() && !Fingerprint.isValid(pin)) { error = "The certificate fingerprint must be 64 hex characters."; return@Button }
                        if (!o.https && pin.isNotBlank()) { error = "A certificate pin only applies to https."; return@Button }
                        val carriedKey = if (key.isBlank() && p.origin == o.origin) store.keys.get(p.id, p.origin) else null
                        store.upsert(p.copy(name = name.ifBlank { "VictorLinux PC" }, origin = o.origin, tunnel = tunnel, pinnedCertSha256 = pin.takeIf { it.isNotBlank() }?.let(Fingerprint::normalize)))
                        (key.takeIf { it.isNotBlank() } ?: carriedKey)?.let { store.keys.save(p.id, o.origin, it) }
                        onDone()
                    }
                }
            }, shape = RoundedCornerShape(50)) { Text("Save") }
            OutlinedButton(onClick = onDone, shape = RoundedCornerShape(50)) { Text("Cancel") }
            if (store.get(p.id) != null) {
                OutlinedButton(onClick = { store.keys.revoke(p.id) }, shape = RoundedCornerShape(50)) { Text("Forget key") }
                OutlinedButton(onClick = { store.delete(p.id); onDone() }, shape = RoundedCornerShape(50)) { Text("Delete PC") }
            }
        }
    }
}

@Composable
private fun ChatPanel(p: VictorProfile, store: VictorProfileStore) {
    val scope = rememberCoroutineScope()
    var input by remember(p.id) { mutableStateOf("") }
    var log by remember(p.id) { mutableStateOf(listOf<Pair<String, String>>()) }   // in memory only: never stored or uploaded
    var busy by remember(p.id) { mutableStateOf(false) }
    var problem by remember(p.id) { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        log.forEach { (r, t) -> Text((if (r == "user") "You: " else "PC: ") + t, style = MaterialTheme.typography.bodyMedium) }
        if (problem.isNotEmpty()) Text(problem, color = MaterialTheme.colorScheme.error)
        OutlinedTextField(input, { input = it }, label = { Text("Message to your PC's local model") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        Button(onClick = {
            val msg = input.trim(); if (msg.isEmpty() || busy) return@Button
            input = ""; busy = true; problem = ""
            val history = log + ("user" to msg); log = history
            scope.launch {
                val r = withContext(Dispatchers.IO) { CompanionClient(p, { store.keys.get(p.id, p.origin) }, readTimeoutMs = 120_000).chat(history) }
                when (r) {
                    is CompanionClient.Chat.Reply -> log = history + ("assistant" to r.text)
                    is CompanionClient.Chat.Failed -> problem = r.message + (r.state?.let { " " + describe(it) } ?: "")
                }
                busy = false
            }
        }, enabled = !busy, shape = RoundedCornerShape(50)) { Text(if (busy) "Waiting for the PC…" else "Send") }
    }
}

private fun describe(s: CompanionState) = when (s) {
    is CompanionState.WrongHost -> s.reason
    is CompanionState.Offline -> s.reason
    CompanionState.BadKey -> "Check or replace the key."
    else -> ""
}
