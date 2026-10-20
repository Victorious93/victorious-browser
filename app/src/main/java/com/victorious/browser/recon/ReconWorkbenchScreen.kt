package com.victorious.browser.recon

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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.victorious.browser.ui.theme.NeonCyan
import com.victorious.browser.ui.theme.NeonMagenta

/**
 * Recon Workbench — three self-contained, offline, pure-logic tools in tabbed
 * sub-sections: Encode/Decode, Hashes, and URL tools. All logic lives in
 * [EncoderTools], [HashTools] and [UrlTools]; this screen is a thin UI over them.
 */
@Composable
fun ReconWorkbenchScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf("Encode/Decode", "Hashes", "URL Tools")

    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Recon Workbench", style = MaterialTheme.typography.titleLarge, color = NeonMagenta)
            TextButton(onClick = onBack) { Text("Back", color = NeonCyan) }
        }
        Text(
            "Offline, pure-logic transforms for your own authorized targets. " +
                "JWT decode never verifies signatures.",
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
            0 -> EncoderTab()
            1 -> HashTab()
            2 -> UrlTab()
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
private fun MonoOutput(label: String, value: String) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = NeonCyan)
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
    )
}

// --- Encode/Decode tab ---------------------------------------------------------

private enum class Transform(val label: String) {
    BASE64("Base64"),
    BASE64URL("Base64 URL"),
    URL("URL encode"),
    HEX("Hex"),
    HTML("HTML entities"),
    ROT13("ROT13"),
    UNICODE("\\u escapes"),
    JWT("JWT decode"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EncoderTab() {
    var input by remember { mutableStateOf("") }
    var transform by remember { mutableStateOf(Transform.BASE64) }
    var output by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun run(decode: Boolean) {
        error = null
        if (transform == Transform.JWT) {
            val v = EncoderTools.jwtDecode(input)
            output = if (v.decodedOk) {
                buildString {
                    append("— HEADER —\n").append(v.header).append("\n\n")
                    append("— PAYLOAD —\n").append(v.payload).append("\n\n")
                    append("— SIGNATURE (not verified) —\n").append(v.signature)
                }
            } else {
                error = v.error ?: "decode failed"
                ""
            }
            return
        }
        val r = when (transform) {
            Transform.BASE64 -> if (decode) EncoderTools.base64Decode(input) else EncoderTools.base64Encode(input)
            Transform.BASE64URL -> if (decode) EncoderTools.base64UrlDecode(input) else EncoderTools.base64UrlEncode(input)
            Transform.URL -> if (decode) EncoderTools.urlDecode(input) else EncoderTools.urlEncode(input)
            Transform.HEX -> if (decode) EncoderTools.hexDecode(input) else EncoderTools.hexEncode(input)
            Transform.HTML -> if (decode) EncoderTools.htmlEntityDecode(input) else EncoderTools.htmlEntityEncode(input)
            Transform.ROT13 -> EncoderTools.rot13(input)
            Transform.UNICODE -> if (decode) EncoderTools.unicodeUnescape(input) else EncoderTools.unicodeEscape(input)
            Transform.JWT -> EncoderTools.Result.ok("")
        }
        if (r.ok) output = r.output else { error = r.error; output = "" }
    }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        SectionCard {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Input") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Transform.entries.forEach { t ->
                    FilterChip(
                        selected = transform == t,
                        onClick = { transform = t },
                        label = { Text(t.label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { run(decode = false) }) {
                    Text(if (transform == Transform.JWT) "Decode JWT" else "Encode")
                }
                if (transform != Transform.JWT) {
                    OutlinedButton(onClick = { run(decode = true) }) { Text("Decode") }
                }
                OutlinedButton(onClick = { input = output }) { Text("Chain ↑") }
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text("Error: $it", color = NeonMagenta, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            MonoOutput("Output", output)
        }
    }
}

// --- Hashes tab ----------------------------------------------------------------

@Composable
private fun HashTab() {
    var input by remember { mutableStateOf("") }
    var digests by remember { mutableStateOf<Map<HashTools.Algorithm, String>>(emptyMap()) }
    var identifyInput by remember { mutableStateOf("") }
    var identification by remember { mutableStateOf<HashTools.Identification?>(null) }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        SectionCard {
            Text("Hasher", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Text to hash") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { digests = HashTools.hashAll(input) }) { Text("Compute digests") }
            Spacer(Modifier.height(8.dp))
            digests.forEach { (algo, hex) -> MonoOutput(algo.label, hex) }
        }
        Spacer(Modifier.height(12.dp))
        SectionCard {
            Text("Identify (heuristic guess)", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
            OutlinedTextField(
                value = identifyInput,
                onValueChange = { identifyInput = it },
                label = { Text("Hash string") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { identification = HashTools.identify(identifyInput) }) { Text("Identify") }
            identification?.let { id ->
                Spacer(Modifier.height(8.dp))
                Text(
                    "len=${id.length}, hex=${id.isHex} — guesses (not proof):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                id.guesses.forEach { g ->
                    Text("• ${g.label}", color = NeonMagenta, style = MaterialTheme.typography.bodyMedium)
                    Text("   ${g.note}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// --- URL tab -------------------------------------------------------------------

@Composable
private fun UrlTab() {
    var input by remember { mutableStateOf("") }
    var parsed by remember { mutableStateOf<UrlTools.ParsedUrl?>(null) }
    var normalized by remember { mutableStateOf("") }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        SectionCard {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("URL") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { parsed = UrlTools.parse(input) }) { Text("Parse") }
                OutlinedButton(onClick = { normalized = UrlTools.normalize(input) }) { Text("Normalize") }
            }
            if (normalized.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                MonoOutput("Normalized", normalized)
            }
            parsed?.let { p ->
                Spacer(Modifier.height(8.dp))
                if (p.error != null) {
                    Text("Parse error: ${p.error}", color = NeonMagenta)
                } else {
                    Text("scheme: ${p.scheme ?: "—"}", style = MaterialTheme.typography.bodySmall)
                    Text("host: ${p.host ?: "—"}", style = MaterialTheme.typography.bodySmall)
                    Text("port: ${if (p.port >= 0) p.port.toString() else "default"}", style = MaterialTheme.typography.bodySmall)
                    Text("path: ${p.path}", style = MaterialTheme.typography.bodySmall)
                    p.fragment?.let { Text("fragment: $it", style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.height(4.dp))
                    Text("Query params (decoded):", style = MaterialTheme.typography.labelMedium, color = NeonCyan)
                    if (p.params.isEmpty()) {
                        Text("(none)", style = MaterialTheme.typography.bodySmall)
                    } else {
                        p.params.forEach { param ->
                            Text("• ${param.name} = ${param.value}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    MonoOutput("Rebuilt", UrlTools.build(p))
                }
            }
        }
    }
}
