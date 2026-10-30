package com.victorious.browser.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.victorious.browser.inspect.InspectHelpers
import com.victorious.browser.ui.theme.NeonCyan
import com.victorious.browser.ui.theme.NeonMagenta

@Composable
private fun SheetTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = NeonMagenta)
}

@Composable
internal fun JwtBlock(jwt: InspectHelpers.DecodedJwt) {
    Text(
        "JWT from ${jwt.source} — decoded-not-verified (signature never checked)",
        style = MaterialTheme.typography.labelSmall,
        color = NeonMagenta,
    )
    Text(
        jwt.header ?: "(header n/a)",
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (jwt.claims.isNotEmpty()) {
        jwt.claims.forEach { (k, v) ->
            Text(
                "$k = $v",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        }
    } else {
        Text(
            jwt.payload ?: "(payload n/a)",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
    }
}

/** Live CookieManager jar for the current page URL. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CookieJarSheet(
    url: String,
    cookies: List<CookieJarLogic.JarCookie>,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onSave: (name: String, value: String) -> Unit,
    onDelete: (name: String) -> Unit,
    onExport: () -> Unit,
) {
    var editing by remember { mutableStateOf<CookieJarLogic.JarCookie?>(null) }
    var newName by remember { mutableStateOf("") }
    var newValue by remember { mutableStateOf("") }
    var expandedJwt by remember { mutableStateOf<String?>(null) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SheetTitle("Cookie jar")
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onRefresh) { Text("Refresh", color = NeonCyan) }
                TextButton(onClick = onExport, enabled = cookies.isNotEmpty()) { Text("Export", color = NeonCyan) }
            }
            Text(
                url.ifBlank { "(no URL)" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "CookieManager jar for this URL (HttpOnly included; flags are not exposed). JWT values are decoded, not verified.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (cookies.isEmpty()) {
                Text("No cookies for this URL.", style = MaterialTheme.typography.bodySmall)
            }
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(cookies, key = { it.name }) { c ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).clickable {
                                expandedJwt = if (expandedJwt == c.name) null else c.name
                            }) {
                                Text(c.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    c.value,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    maxLines = if (expandedJwt == c.name) 8 else 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (c.jwt != null) {
                                Text("JWT", color = NeonMagenta, style = MaterialTheme.typography.labelSmall)
                                Spacer(Modifier.width(8.dp))
                            }
                            TextButton(onClick = { editing = c; newName = c.name; newValue = c.value }) {
                                Text("Edit", color = NeonCyan)
                            }
                            TextButton(onClick = { onDelete(c.name) }) { Text("✕", color = NeonMagenta) }
                        }
                        if (expandedJwt == c.name && c.jwt != null) {
                            JwtBlock(c.jwt)
                        }
                    }
                    HorizontalDivider()
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Add / overwrite", style = MaterialTheme.typography.labelMedium, color = NeonCyan)
            OutlinedTextField(newName, { newName = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(newValue, { newValue = it }, label = { Text("Value") }, modifier = Modifier.fillMaxWidth())
            TextButton(
                onClick = {
                    if (newName.isNotBlank()) {
                        onSave(newName, newValue)
                        newName = ""
                        newValue = ""
                        editing = null
                    }
                },
                enabled = newName.isNotBlank(),
            ) { Text(if (editing != null) "Save cookie" else "Set cookie", color = NeonCyan) }
        }
    }
}

/** localStorage / sessionStorage for the current origin. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StorageInspectorSheet(
    dump: StorageInspector.Dump?,
    loading: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onSet: (StorageInspector.Store, String, String) -> Unit,
    onDelete: (StorageInspector.Store, String) -> Unit,
    onClear: (StorageInspector.Store) -> Unit,
    onExport: () -> Unit,
) {
    var store by remember { mutableStateOf(StorageInspector.Store.LOCAL) }
    var key by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf<String?>(null) }
    val entries = when (store) {
        StorageInspector.Store.LOCAL -> dump?.local.orEmpty()
        StorageInspector.Store.SESSION -> dump?.session.orEmpty()
    }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SheetTitle("Web storage")
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onRefresh) { Text("Refresh", color = NeonCyan) }
                TextButton(onClick = onExport, enabled = dump != null) { Text("Export", color = NeonCyan) }
            }
            Text(
                dump?.origin?.ifBlank { "(unknown origin)" } ?: if (loading) "Reading…" else "No dump yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            dump?.error?.let { Text(it, color = NeonMagenta, style = MaterialTheme.typography.labelSmall) }
            Row {
                FilterChip(
                    selected = store == StorageInspector.Store.LOCAL,
                    onClick = { store = StorageInspector.Store.LOCAL },
                    label = { Text("localStorage (${dump?.local?.size ?: 0})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonCyan.copy(alpha = 0.25f),
                        selectedLabelColor = NeonCyan,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = store == StorageInspector.Store.SESSION,
                    onClick = { store = StorageInspector.Store.SESSION },
                    label = { Text("sessionStorage (${dump?.session?.size ?: 0})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonCyan.copy(alpha = 0.25f),
                        selectedLabelColor = NeonCyan,
                    ),
                )
            }
            if (entries.isEmpty() && !loading) {
                Text("Empty.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(entries, key = { it.key }) { e ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(
                                Modifier.weight(1f).clickable {
                                    expanded = if (expanded == e.key) null else e.key
                                    key = e.key
                                    value = e.value
                                },
                            ) {
                                Text(e.key, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    e.value,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    maxLines = if (expanded == e.key) 8 else 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (e.jwt != null) {
                                Text("JWT", color = NeonMagenta, style = MaterialTheme.typography.labelSmall)
                                Spacer(Modifier.width(8.dp))
                            }
                            TextButton(onClick = { onDelete(store, e.key) }) { Text("✕", color = NeonMagenta) }
                        }
                        if (expanded == e.key && e.jwt != null) JwtBlock(e.jwt)
                    }
                    HorizontalDivider()
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(key, { key = it }, label = { Text("Key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value, { value = it }, label = { Text("Value") }, modifier = Modifier.fillMaxWidth())
            Row {
                TextButton(
                    onClick = { if (key.isNotBlank()) onSet(store, key, value) },
                    enabled = key.isNotBlank(),
                ) { Text("Set", color = NeonCyan) }
                TextButton(onClick = { onClear(store) }, enabled = entries.isNotEmpty()) {
                    Text("Clear ${store.jsName}", color = NeonMagenta)
                }
            }
        }
    }
}

/** Per-tab WebView console + page-load errors. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConsoleSheet(
    entries: List<ConsoleEntry>,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onCopy: () -> Unit,
) {
    var level by remember { mutableStateOf("ALL") }
    val filtered = ConsoleLog.filter(entries, level)
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SheetTitle("Console (${entries.size})")
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCopy, enabled = entries.isNotEmpty()) { Text("Copy", color = NeonCyan) }
                TextButton(onClick = onClear, enabled = entries.isNotEmpty()) { Text("Clear", color = NeonMagenta) }
            }
            Row {
                FilterChip(
                    selected = level == "ALL",
                    onClick = { level = "ALL" },
                    label = { Text("All") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonCyan.copy(alpha = 0.25f),
                        selectedLabelColor = NeonCyan,
                    ),
                )
                Spacer(Modifier.width(6.dp))
                FilterChip(
                    selected = level == "ERROR",
                    onClick = { level = "ERROR" },
                    label = { Text("Errors (${ConsoleLog.errorCount(entries)})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonMagenta.copy(alpha = 0.25f),
                        selectedLabelColor = NeonMagenta,
                    ),
                )
                Spacer(Modifier.width(6.dp))
                FilterChip(
                    selected = level == "WARNING",
                    onClick = { level = "WARNING" },
                    label = { Text("Warn") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonCyan.copy(alpha = 0.25f),
                        selectedLabelColor = NeonCyan,
                    ),
                )
            }
            if (filtered.isEmpty()) {
                Text(
                    if (entries.isEmpty()) "No console messages this tab yet. JS console.log / errors land here."
                    else "No messages at this level.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(filtered.size) { i ->
                    val e = filtered[filtered.size - 1 - i]
                    val color = when (e.level) {
                        "ERROR" -> NeonMagenta
                        "WARNING" -> NeonCyan
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Text(
                        ConsoleLog.format(e),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = color,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
internal fun ClearSiteDataDialog(
    origin: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear site data?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Wipes cookies for this URL, localStorage/sessionStorage, and the WebStorage origin " +
                        "(${origin ?: "unknown"}). Cache, IndexedDB and service workers may remain. " +
                        "Does not clear proxy capture history or other origins.",
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Clear", color = NeonMagenta) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
