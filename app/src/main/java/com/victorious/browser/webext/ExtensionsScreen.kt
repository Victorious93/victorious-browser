package com.victorious.browser.webext

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.victorious.browser.ui.components.NeonButton
import com.victorious.browser.ui.components.NeonOutlineButton
import com.victorious.browser.ui.components.ToolScaffold
import com.victorious.browser.ui.theme.NeonCyan
import com.victorious.browser.ui.theme.NeonMagenta

/**
 * Extension manager for the WebView-native userscript engine.
 * Honest about scope: userscripts only — not Chrome/Firefox .crx/.xpi.
 */
@Composable
fun ExtensionsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember { ExtensionStore.get(context) }
    LaunchedEffect(Unit) { store.installStarters() }
    val extensions by store.extensions.collectAsState()
    var importing by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Extension?>(null) }
    var deleting by remember { mutableStateOf<Extension?>(null) }
    var status by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                if (bytes.size > ExtensionRegistry.MAX_SOURCE_BYTES) {
                    status = "File too large (${bytes.size / 1024} KB); cap is " +
                        "${ExtensionRegistry.MAX_SOURCE_BYTES / 1024} KB"
                    return@rememberLauncherForActivityResult
                }
                when (val r = store.install(String(bytes, Charsets.UTF_8))) {
                    is ExtensionRegistry.InstallResult.Installed ->
                        status = "Installed ${r.extension.name}" +
                            if (r.warnings.isEmpty()) "" else " (${r.warnings.size} warning(s))"
                    is ExtensionRegistry.InstallResult.Rejected -> status = "Rejected: ${r.reason}"
                }
            } ?: run { status = "Could not open file" }
        }.onFailure { status = "Import failed: ${it.message}" }
    }

    ToolScaffold(
        title = "Extensions",
        subtitle = "Userscript engine — not Chrome/Firefox add-ons",
        onBack = onBack,
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Text(
                "Runs Greasemonkey/Tampermonkey-style userscripts injected into WebView pages. " +
                    "A system WebView cannot load .crx/.xpi packages — there is no WebExtension API.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonButton(text = "Paste / Import", onClick = { importing = true })
                NeonOutlineButton(
                    text = "Load .user.js",
                    onClick = { filePicker.launch(arrayOf("*/*", "text/*", "application/javascript")) },
                )
            }
            if (status.isNotBlank()) {
                Text(status, style = MaterialTheme.typography.labelSmall, color = NeonCyan,
                    modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(extensions, key = { it.id }) { ext ->
                    ExtensionRow(
                        ext = ext,
                        onToggle = { store.setEnabled(ext.id, it) },
                        onOpen = { detail = ext },
                        onDelete = { deleting = ext },
                    )
                }
            }
        }
    }

    if (importing) {
        ImportDialog(
            onDismiss = { importing = false },
            onImport = { src ->
                when (val r = store.install(src)) {
                    is ExtensionRegistry.InstallResult.Installed -> {
                        status = "Installed ${r.extension.name}"
                        importing = false
                    }
                    is ExtensionRegistry.InstallResult.Rejected -> status = "Rejected: ${r.reason}"
                }
            },
        )
    }
    detail?.let { ext ->
        DetailDialog(ext = ext, onDismiss = { detail = null })
    }
    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${target.name}\"?") },
            text = { Text("Removes the userscript and its GM_* storage.") },
            confirmButton = {
                TextButton(onClick = {
                    store.delete(target.id); deleting = null
                    status = "Deleted ${target.name}"
                }) { Text("Delete", color = NeonMagenta) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ExtensionRow(
    ext: Extension,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(ext.name, style = MaterialTheme.typography.titleSmall, color = NeonCyan,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString {
                        if (ext.version.isNotBlank()) append("v${ext.version} · ")
                        if (ext.bundled) append("bundled · ")
                        append(ext.runAt.token)
                        if (ext.matches.isNotEmpty()) append(" · ${ext.matches.size} match")
                        if (ext.includes.isNotEmpty()) append(" · ${ext.includes.size} include")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (ext.description.isNotBlank()) {
                    Text(ext.description, style = MaterialTheme.typography.bodySmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Switch(checked = ext.enabled, onCheckedChange = onToggle)
            TextButton(onClick = onDelete) { Text("Del", color = NeonMagenta) }
        }
    }
}

@Composable
private fun ImportDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import userscript") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Paste a full .user.js including the ==UserScript== block. Cap " +
                        "${ExtensionRegistry.MAX_SOURCE_BYTES / 1024} KB.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = text, onValueChange = {
                        text = it
                        preview = when (val r = UserscriptParser.parse(it)) {
                            is ParseResult.Ok -> "${r.meta.name} v${r.meta.version} · " +
                                "${r.meta.matches.size} match · ${r.meta.runAt.token}" +
                                if (r.warnings.isEmpty()) "" else " · ${r.warnings.size} warn"
                            is ParseResult.Error -> "Parse: ${r.message}"
                        }
                    },
                    label = { Text("Source") },
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
                preview?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = NeonCyan,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onImport(text) }, enabled = text.isNotBlank()) { Text("Install") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DetailDialog(ext: Extension, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(ext.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                MetaLine("Version", ext.version.ifBlank { "—" })
                MetaLine("Namespace", ext.namespace.ifBlank { "—" })
                MetaLine("Run at", ext.runAt.token)
                MetaLine("@match", ext.matches.joinToString("\n").ifBlank { "—" })
                MetaLine("@include", ext.includes.joinToString("\n").ifBlank { "—" })
                MetaLine("@exclude", ext.excludes.joinToString("\n").ifBlank { "—" })
                MetaLine("@grant", ext.grants.joinToString(", ").ifBlank { "—" })
                MetaLine("Description", ext.description.ifBlank { "—" })
                Spacer(Modifier.height(6.dp))
                Text("Source (first 2 KB)", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                Text(
                    ext.source.take(2048),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun MetaLine(label: String, value: String) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = NeonCyan)
    Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 4.dp))
}
