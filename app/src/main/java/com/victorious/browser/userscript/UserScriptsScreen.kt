package com.victorious.browser.userscript

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
 * Compose "Scripts" screen: list / add / edit / delete [UserScript]s, same
 * SharedPreferences-store + dialog pattern as [com.victorious.browser.panels.FleetPanelScreen].
 *
 * Not document-start on every device — see [UserScriptInjector]'s doc for the
 * fallback-path caveat; this screen doesn't surface that distinction per-script,
 * only once via the banner at the top.
 */
@Composable
fun UserScriptsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember { UserScriptStore.get(context) }
    val scripts by store.scripts.collectAsState()
    var editing by remember { mutableStateOf<UserScript?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<UserScript?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onBack) { Text("← Browser", color = NeonCyan) }
            Text("Scripts", style = MaterialTheme.typography.titleMedium, color = NeonMagenta)
        }

        Text(
            if (UserScriptInjector.isDocumentStartSupported()) {
                "Document-start injection is supported on this WebView."
            } else {
                "This WebView has no document-start support — scripts run via a later " +
                    "fallback and may see the page already running."
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp),
        )

        Button(onClick = { adding = true }) { Text("Add script") }
        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(scripts, key = { it.id }) { script ->
                ScriptRow(
                    script = script,
                    onToggle = { enabled -> store.update(script.copy(enabled = enabled)) },
                    onEdit = { editing = script },
                    onDelete = { deleting = script },
                )
            }
        }
    }

    if (adding) {
        ScriptEditDialog(
            title = "Add script",
            initial = UserScript(id = UserScriptStore.newId(), name = "", hostPattern = "", code = ""),
            onDismiss = { adding = false },
            onSave = { s -> store.add(s); adding = false },
        )
    }
    editing?.let { current ->
        ScriptEditDialog(
            title = "Edit script",
            initial = current,
            onDismiss = { editing = null },
            onSave = { s -> store.update(s); editing = null },
        )
    }
    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${target.name}\"?") },
            confirmButton = {
                TextButton(onClick = { store.delete(target.id); deleting = null }) {
                    Text("Delete", color = NeonMagenta)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ScriptRow(
    script: UserScript,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(script.name.ifBlank { "(untitled)" }, style = MaterialTheme.typography.titleSmall)
                Switch(checked = script.enabled, onCheckedChange = onToggle)
            }
            Text(
                script.hostPattern,
                style = MaterialTheme.typography.bodySmall,
                color = NeonCyan,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDelete) { Text("Del", color = NeonMagenta) }
            }
        }
    }
}

@Composable
private fun ScriptEditDialog(
    title: String,
    initial: UserScript,
    onDismiss: () -> Unit,
    onSave: (UserScript) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var hostPattern by remember { mutableStateOf(initial.hostPattern) }
    var code by remember { mutableStateOf(initial.code) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = hostPattern,
                    onValueChange = { hostPattern = it },
                    label = { Text("Host (e.g. example.com or *.example.com)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("JavaScript") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isBlank() || hostPattern.isBlank() || code.isBlank()) return@TextButton
                    onSave(initial.copy(name = name.trim(), hostPattern = hostPattern.trim(), code = code))
                },
            ) { Text("Save", color = NeonCyan) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
