package com.victorious.browser.panels

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.victorious.browser.ui.theme.NeonCyan
import com.victorious.browser.ui.theme.NeonMagenta
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Fleet Panels home screen — lists the owner's own locally-hosted / fleet web
 * dashboards and opens them in the in-app WebView.
 *
 * Fleet bridge ops `browser.panel.list` / `browser.panel.open` read/drive [PanelStore].
 */
@Composable
fun FleetPanelScreen(
    onOpenPanel: (Panel) -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenFleetControl: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember { PanelStore.get(context) }
    val panels by store.panels.collectAsState()
    val reachability = remember { mutableStateMapOf<String, Reachability>() }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Panel?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Panel?>(null) }

    // Lightweight async reachability probe on load / panel-set change.
    LaunchedEffect(panels) {
        panels.forEach { p ->
            if (reachability[p.id] == null) reachability[p.id] = Reachability.UNKNOWN
            scope.launch {
                reachability[p.id] = ReachabilityChecker.check(p.url)
            }
        }
    }

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
            Text(
                "Fleet Panels",
                style = MaterialTheme.typography.titleLarge,
                color = NeonMagenta,
            )
            Row {
                TextButton(onClick = onOpenFleetControl) {
                    Text("Control", color = NeonMagenta)
                }
                TextButton(onClick = onOpenNetwork) {
                    Text("Network", color = NeonCyan)
                }
                TextButton(onClick = onOpenBrowser) {
                    Text("Browser", color = NeonCyan)
                }
            }
        }

        Text(
            "Owner dashboards — editable defaults. Mesh routing is provided by the " +
                "separate Victorious Mesh app; this launcher only stores a per-panel flag.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { adding = true }) { Text("Add panel") }
            Button(
                onClick = {
                    panels.forEach { p ->
                        reachability[p.id] = Reachability.UNKNOWN
                        scope.launch {
                            reachability[p.id] = ReachabilityChecker.check(p.url)
                        }
                    }
                },
            ) { Text("Recheck") }
        }

        Spacer(Modifier.height(8.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(panels, key = { it.id }) { panel ->
                PanelCard(
                    panel = panel,
                    reachability = reachability[panel.id] ?: Reachability.UNKNOWN,
                    onOpen = { onOpenPanel(panel) },
                    onEdit = { editing = panel },
                    onDelete = { deleting = panel },
                )
            }
        }
    }

    if (adding) {
        PanelEditDialog(
            title = "Add panel",
            initial = Panel(
                id = UUID.randomUUID().toString(),
                name = "",
                url = "http://127.0.0.1:8080/",
                kind = PanelKind.OTHER,
                reachOverMesh = false,
            ),
            onDismiss = { adding = false },
            onSave = { store.add(it); adding = false },
        )
    }
    editing?.let { panel ->
        PanelEditDialog(
            title = "Edit panel",
            initial = panel,
            onDismiss = { editing = null },
            onSave = { store.update(it); editing = null },
        )
    }
    deleting?.let { panel ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete panel?") },
            text = { Text("Remove \"${panel.name}\" from the launcher.") },
            confirmButton = {
                TextButton(onClick = {
                    store.delete(panel.id)
                    deleting = null
                }) { Text("Delete", color = NeonMagenta) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PanelCard(
    panel: Panel,
    reachability: Reachability,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    kindTag(panel.kind),
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan,
                )
                ReachDot(reachability)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                panel.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                panel.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (panel.reachOverMesh) {
                Text(
                    "via mesh (Victorious Mesh app)",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonMagenta,
                )
            } else {
                Text(
                    "localhost / direct",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onEdit) { Text("Edit", color = NeonCyan) }
                TextButton(onClick = onDelete) { Text("Del", color = NeonMagenta) }
            }
        }
    }
}

@Composable
private fun ReachDot(state: Reachability) {
    val color = when (state) {
        Reachability.UP -> Color(0xFF00C853)
        Reachability.DOWN -> Color(0xFFFF1744)
        Reachability.UNKNOWN -> Color(0xFF9E9E9E)
    }
    val label = when (state) {
        Reachability.UP -> "up"
        Reachability.DOWN -> "down"
        Reachability.UNKNOWN -> "…"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .padding(0.dp),
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.size(8.dp)) {
                drawCircle(color = color)
            }
        }
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

private fun kindTag(kind: PanelKind): String = when (kind) {
    PanelKind.LOCAL_HTTP -> "LOCAL"
    PanelKind.HEADSCALE_ADMIN -> "HEADSCALE"
    PanelKind.METASPLOIT_WEB -> "MSF"
    PanelKind.KYC_AUDITLAB -> "KYC"
    PanelKind.OTHER -> "OTHER"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PanelEditDialog(
    title: String,
    initial: Panel,
    onDismiss: () -> Unit,
    onSave: (Panel) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var url by remember { mutableStateOf(initial.url) }
    var kind by remember { mutableStateOf(initial.kind) }
    var mesh by remember { mutableStateOf(initial.reachOverMesh) }
    var kindExpanded by remember { mutableStateOf(false) }

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
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text("Kind", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PanelKind.entries.forEach { k ->
                        FilterChip(
                            selected = kind == k,
                            onClick = { kind = k },
                            label = { Text(k.name, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = mesh, onCheckedChange = { mesh = it })
                    Column {
                        Text("Reach over mesh")
                        Text(
                            "Mesh routing comes from the Victorious Mesh app — " +
                                "this flag is informational until mesh-reachability is wired.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isBlank() || url.isBlank()) return@TextButton
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            url = url.trim(),
                            kind = kind,
                            reachOverMesh = mesh,
                        ),
                    )
                },
            ) { Text("Save", color = NeonCyan) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
