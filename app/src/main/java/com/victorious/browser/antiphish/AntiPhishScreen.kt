package com.victorious.browser.antiphish

import android.widget.Toast
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.victorious.browser.ui.theme.NeonCyan
import com.victorious.browser.ui.theme.NeonMagenta
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Anti-Phish module home: training / quiz, detection toggle + known-good list editor,
 * and local report log with export. Purely defensive / educational.
 */
@Composable
fun AntiPhishScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settings = remember { AntiPhishSettings.get(context) }
    val log = remember { AntiPhishReportLog.get(context) }
    val enabled by settings.enabled.collectAsState()
    val knownGood by settings.knownGood.collectAsState()
    val entries by log.entries.collectAsState()

    var tab by remember { mutableStateOf(AntiPhishTab.TRAINING) }

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
            Text("Anti-Phish", style = MaterialTheme.typography.titleLarge, color = NeonMagenta)
            TextButton(onClick = onBack) { Text("Back", color = NeonCyan) }
        }
        Text(
            "Defensive awareness + passive page scoring. Never harvests credentials. Off by default.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AntiPhishTab.entries.forEach { t ->
                FilterChip(
                    selected = tab == t,
                    onClick = { tab = t },
                    label = { Text(t.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonCyan.copy(alpha = 0.25f),
                        selectedLabelColor = NeonCyan,
                    ),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        when (tab) {
            AntiPhishTab.TRAINING -> TrainingPane()
            AntiPhishTab.DETECT -> DetectPane(
                enabled = enabled,
                onEnabledChange = { settings.setEnabled(it) },
                knownGood = knownGood,
                onSaveDomains = { settings.setKnownGood(it) },
                onReset = { settings.resetDefaults() },
            )
            AntiPhishTab.REPORTS -> ReportsPane(
                entries = entries,
                onClear = { log.clear() },
                onExport = {
                    val loc = log.export()
                    Toast.makeText(
                        context,
                        if (loc != null) "Exported to $loc" else "Export failed",
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            )
        }
    }
}

private enum class AntiPhishTab(val label: String) {
    TRAINING("Training"),
    DETECT("Detect"),
    REPORTS("Reports"),
}

@Composable
private fun TrainingPane() {
    var quizIndex by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var score by remember { mutableIntStateOf(0) }
    var answered by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Text("What phishing looks like", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
        Spacer(Modifier.height(4.dp))
        RED_FLAGS.forEach { (title, body) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = NeonMagenta)
                    Text(body, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Spot the phish", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
        Text(
            "Neutral fictional brands only. Score: $score / $answered",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        val item = PhishQuiz.ITEMS[quizIndex % PhishQuiz.ITEMS.size]
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(item.prompt, style = MaterialTheme.typography.titleSmall, color = NeonMagenta)
                Text(item.scenario, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                item.choices.forEachIndexed { idx, choice ->
                    val highlight = when {
                        selected == null -> NeonCyan
                        selected == idx && choice.isPhish -> NeonMagenta
                        selected != null && choice.isPhish -> NeonMagenta
                        selected == idx -> NeonCyan
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    TextButton(
                        onClick = {
                            if (selected != null) return@TextButton
                            selected = idx
                            answered++
                            if (choice.isPhish) score++
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(choice.text, color = highlight, modifier = Modifier.fillMaxWidth())
                    }
                    if (selected != null) {
                        Text(
                            choice.explanation,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (choice.isPhish) NeonMagenta else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                        )
                    }
                }
                if (selected != null) {
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = {
                            selected = null
                            quizIndex = (quizIndex + 1) % PhishQuiz.ITEMS.size
                        },
                    ) { Text("Next") }
                }
            }
        }
    }
}

private val RED_FLAGS = listOf(
    "Lookalike domains" to
        "Typosquats and brand-in-subdomain tricks (acmepay-secure.top) mimic a trusted name on a different registrable domain.",
    "Punycode / homoglyphs" to
        "IDN hosts (xn--…) or mixed-script labels can look identical to a brand while resolving elsewhere. Check the decoded Unicode form.",
    "Mismatched form actions" to
        "A login form whose action posts to another registrable domain is a credential-harvest indicator.",
    "Insecure / IP-literal forms" to
        "Password fields on http:// pages or raw IP hosts skip normal certificate identity checks.",
    "Hidden credential fields" to
        "Off-screen or type=hidden password/username inputs often belong to cloned login kits.",
    "Urgency cues" to
        "\"Account locked in 15 minutes — verify NOW\" pairs fear with a demand to enter credentials.",
    "Title / brand mismatch" to
        "Page title or logo alt text claims a brand whose domain is not the one in the address bar.",
)

@Composable
private fun DetectPane(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    knownGood: List<String>,
    onSaveDomains: (List<String>) -> Unit,
    onReset: () -> Unit,
) {
    var editor by remember(knownGood) { mutableStateOf(knownGood.joinToString("\n")) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Passive detection", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
                Text(
                    "Scores pages you visit. Warnings are advisory and non-blocking.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        Spacer(Modifier.height(12.dp))
        Text("Known-good brands / domains", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
        Text(
            "One domain per line. Used for lookalike / brand-mismatch checks. Editable.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = editor,
            onValueChange = { editor = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            textStyle = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSaveDomains(editor.lines()) }) { Text("Save list") }
            TextButton(onClick = {
                onReset()
            }) { Text("Reset defaults", color = NeonMagenta) }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Heuristics: punycode/IDN, mixed-script labels, typosquat distance, suspicious TLD, " +
                "structural host oddities, password-on-HTTP, cross-domain form action, " +
                "hidden credential fields, IP-literal credential forms, title/brand keyword mismatch.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReportsPane(
    entries: List<AntiPhishReportLog.ReportEntry>,
    onClear: () -> Unit,
    onExport: () -> Unit,
) {
    val fmt = remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onExport, enabled = entries.isNotEmpty()) { Text("Export JSON") }
            TextButton(onClick = onClear, enabled = entries.isNotEmpty()) {
                Text("Clear", color = NeonMagenta)
            }
        }
        Text(
            "Host only — no query strings, paths, or credentials stored.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            if (entries.isEmpty()) {
                Text(
                    "No detections recorded yet. Enable passive detection and browse.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            entries.forEach { e ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(
                            "${fmt.format(Date(e.timestampMs))} · ${e.host}",
                            style = MaterialTheme.typography.titleSmall,
                            color = NeonCyan,
                        )
                        Text(
                            "Score ${e.score} (${e.level})",
                            style = MaterialTheme.typography.labelMedium,
                            color = NeonMagenta,
                        )
                        e.reasons.take(5).forEach { r ->
                            Text("• $r", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
