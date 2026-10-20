package com.victorious.browser.panels

import android.widget.Toast
import androidx.compose.foundation.clickable
import com.victorious.browser.liteapp.LiteAppShortcuts
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
import androidx.compose.material3.CircularProgressIndicator
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
 * Mesh-flagged panels go through [MeshReachability] before open: tunnel-up +
 * reachable → WebView; tunnel-down / unreachable / unknown-host → actionable dialog.
 *
 * Fleet bridge ops `browser.panel.list` / `browser.panel.open` read/drive [PanelStore].
 */
@Composable
fun FleetPanelScreen(
    onOpenPanel: (Panel) -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenFleetControl: () -> Unit = {},
    onOpenRecon: () -> Unit = {},
    onOpenSandboxes: () -> Unit = {},
    onOpenOsint: () -> Unit = {},
    onOpenScanner: () -> Unit = {},
    onOpenNetId: () -> Unit = {},
    onOpenFuzz: () -> Unit = {},
    onOpenExport: () -> Unit = {},
    onOpenMsf: () -> Unit = {},
    onOpenCerts: () -> Unit = {},
    onOpenEvidence: () -> Unit = {},
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
    var meshChecking by remember { mutableStateOf(false) }
    var meshBlock by remember { mutableStateOf<MeshOpenDecision.Blocked?>(null) }
    var meshBlockedPanel by remember { mutableStateOf<Panel?>(null) }
    val meshAppInstalled = remember { MeshReachability.isMeshAppInstalled(context) }

    val appContext = context.applicationContext

    // Row status: direct panels → plain TCP; mesh panels → MeshReachability probe.
    suspend fun probeRow(p: Panel): Reachability =
        if (p.reachOverMesh) {
            if (MeshReachability.probeFor(p.url, appContext).tcpReachable) Reachability.UP else Reachability.DOWN
        } else {
            ReachabilityChecker.check(p.url)
        }

    // Lightweight async reachability probe on load / panel-set change.
    // Direct panels: plain TCP. Mesh panels: MeshReachability probe → UP/DOWN.
    LaunchedEffect(panels) {
        panels.forEach { p ->
            if (reachability[p.id] == null) reachability[p.id] = Reachability.UNKNOWN
            scope.launch {
                reachability[p.id] = probeRow(p)
            }
        }
    }

    fun openPanel(panel: Panel) {
        if (!panel.reachOverMesh) {
            onOpenPanel(panel)
            return
        }
        meshChecking = true
        scope.launch {
            val decision = MeshReachability.resolveAndDecide(panel, appContext)
            meshChecking = false
            reachability[panel.id] =
                if (decision is MeshOpenDecision.Open) Reachability.UP else Reachability.DOWN
            when (decision) {
                is MeshOpenDecision.Open -> onOpenPanel(panel)
                is MeshOpenDecision.Blocked -> {
                    meshBlockedPanel = panel
                    meshBlock = decision
                }
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
                TextButton(onClick = onOpenRecon) {
                    Text("Recon", color = NeonCyan)
                }
                TextButton(onClick = onOpenSandboxes) {
                    Text("Sandboxes", color = NeonMagenta)
                }
                TextButton(onClick = onOpenOsint) {
                    Text("OSINT", color = NeonMagenta)
                }
                TextButton(onClick = onOpenScanner) {
                    Text("Scanner", color = NeonCyan)
                }
                TextButton(onClick = onOpenNetId) {
                    Text("Net ID", color = NeonCyan)
                }
                TextButton(onClick = onOpenFuzz) {
                    Text("Fuzz", color = NeonMagenta)
                }
                TextButton(onClick = onOpenMsf) {
                    Text("MSF", color = NeonMagenta)
                }
                TextButton(onClick = onOpenCerts) {
                    Text("Certs", color = NeonCyan)
                }
                TextButton(onClick = onOpenEvidence) {
                    Text("Evidence", color = NeonMagenta)
                }
                TextButton(onClick = onOpenExport) {
                    Text("Export", color = NeonCyan)
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
            "Owner dashboards. Mesh panels resolve over Victorious Mesh " +
                "(tunnel provided by that app — this browser does not hold VpnService).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!meshAppInstalled) {
            Text(
                "Victorious Mesh app not detected on this device " +
                    "(looked for ${MeshReachability.MESH_PACKAGE_CANDIDATES.joinToString()}). " +
                    "Mesh panels will still probe; install Mesh for live routing.",
                style = MaterialTheme.typography.labelSmall,
                color = NeonMagenta,
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { adding = true }) { Text("Add panel") }
            Button(
                onClick = {
                    panels.forEach { p ->
                        reachability[p.id] = Reachability.UNKNOWN
                        scope.launch {
                            reachability[p.id] = probeRow(p)
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
                    onOpen = { openPanel(panel) },
                    onEdit = { editing = panel },
                    onDelete = { deleting = panel },
                    onPin = {
                        val ok = LiteAppShortcuts.requestPin(context, panel)
                        if (!ok) {
                            Toast.makeText(context, "Launcher can't pin shortcuts", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
        }
    }

    if (meshChecking) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Checking mesh…") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Resolving mesh target and probing reachability.")
                }
            },
            confirmButton = {},
        )
    }

    meshBlock?.let { blocked ->
        AlertDialog(
            onDismissRequest = { meshBlock = null },
            title = {
                Text(
                    when (blocked.reason) {
                        MeshBlockReason.TUNNEL_DOWN -> "Mesh tunnel down"
                        MeshBlockReason.UNREACHABLE -> "Mesh node unreachable"
                        MeshBlockReason.UNKNOWN_HOST -> "Unknown mesh host"
                        MeshBlockReason.NOT_MESH_PANEL -> "Not a mesh panel"
                    },
                )
            },
            text = { Text(blocked.message) },
            confirmButton = {
                Row {
                    if (blocked.reason == MeshBlockReason.TUNNEL_DOWN) {
                        // Standard Android launcher intent for an installed candidate
                        // package — NOT a Victorious Mesh API (none is published yet).
                        val launch = remember(blocked) { MeshReachability.meshAppLaunchIntent(context) }
                        if (launch != null) {
                            TextButton(
                                onClick = {
                                    runCatching { context.startActivity(launch) }
                                },
                            ) { Text("Open Victorious Mesh", color = NeonCyan) }
                        }
                    }
                    val retryPanel = meshBlockedPanel
                    if (retryPanel != null && blocked.reason != MeshBlockReason.NOT_MESH_PANEL) {
                        TextButton(
                            onClick = {
                                meshBlock = null
                                openPanel(retryPanel)
                            },
                        ) { Text("Retry", color = NeonCyan) }
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        meshBlock = null
                        meshBlockedPanel = null
                    },
                ) { Text("Dismiss", color = NeonMagenta) }
            },
        )
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
    onPin: () -> Unit,
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
                    "via mesh (Victorious Mesh)",
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
                TextButton(onClick = onPin) { Text("Pin", color = NeonCyan) }
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
                    label = { Text("URL (mesh IP, MagicDNS, or node name)") },
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
                            "Opens via mesh resolution. Routing comes from the " +
                                "Victorious Mesh app; this browser only probes reachability.",
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
