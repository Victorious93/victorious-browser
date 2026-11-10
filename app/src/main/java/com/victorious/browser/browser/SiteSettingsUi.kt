package com.victorious.browser.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.victorious.browser.ui.theme.NeonCyan
import com.victorious.browser.ui.theme.NeonMagenta

/** The choices offered on a per-site capability prompt (camera / mic / location). */
enum class PromptChoice { ALLOW_ONCE, ALWAYS, BLOCK }

/**
 * Per-site capability prompt shown when a page first asks for camera, mic or location
 * on a site with no stored decision. "Always"/"Block" persist (non-private tabs only);
 * "Allow once" grants for this request without persisting.
 */
@Composable
fun SitePermissionPromptDialog(
    host: String,
    permissions: List<SitePermission>,
    isPrivate: Boolean,
    onChoice: (PromptChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    val what = permissions.joinToString(" & ") { it.label.lowercase() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Allow $what?", color = NeonCyan) },
        text = {
            Column {
                Text("$host wants to use your $what.", style = MaterialTheme.typography.bodyMedium)
                if (isPrivate) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Private tab: this choice is used once and never saved.",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonMagenta,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onChoice(PromptChoice.ALLOW_ONCE) }) { Text("Allow once", color = NeonCyan) }
        },
        dismissButton = {
            Row {
                if (!isPrivate) {
                    TextButton(onClick = { onChoice(PromptChoice.ALWAYS) }) { Text("Always", color = NeonCyan) }
                }
                TextButton(onClick = { onChoice(PromptChoice.BLOCK) }) { Text("Block", color = NeonMagenta) }
            }
        },
    )
}

/** "Open in app?" confirmation for an unknown external scheme (never auto-launched). */
@Composable
fun ExternalLinkConfirmDialog(
    scheme: String,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open in another app?", color = NeonCyan) },
        text = {
            Text(
                "This link ($scheme:) wants to open another app. Only continue if you trust this page.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = { TextButton(onClick = onOpen) { Text("Open", color = NeonCyan) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = NeonMagenta) } },
    )
}

/**
 * Per-site settings sheet: camera / mic / location / pop-ups as Ask·Allow·Block, plus a
 * JavaScript on/off toggle and a "Clear for this site" action. Reachable from the
 * address-bar site-info lock and the Fleet tools sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteSettingsSheet(
    host: String?,
    store: SitePermissionStore,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).fillMaxWidth()) {
            Text("Site settings", style = MaterialTheme.typography.titleMedium, color = NeonMagenta)
            Spacer(Modifier.height(4.dp))
            Text(
                host ?: "No site loaded",
                style = MaterialTheme.typography.bodyMedium,
                color = NeonCyan,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(12.dp))

            if (host == null) {
                Text(
                    "Open a website to change its permissions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val capabilities = listOf(
                    SitePermission.CAMERA,
                    SitePermission.MIC,
                    SitePermission.LOCATION,
                    SitePermission.POPUPS,
                )
                capabilities.forEach { perm ->
                    PermissionRow(
                        label = perm.label,
                        current = store.decision(host, perm),
                        onSet = { store.set(host, perm, it) },
                    )
                    HorizontalDivider(Modifier.padding(vertical = 2.dp))
                }
                // JavaScript: Allow / Block only (defaults to Allow).
                val jsDecision = store.decision(host, SitePermission.JAVASCRIPT)
                val jsOn = SitePermissionLogic.effective(SitePermission.JAVASCRIPT, jsDecision) == PermissionDecision.ALLOW
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("JavaScript", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    FilterChip(
                        selected = jsOn,
                        onClick = { store.set(host, SitePermission.JAVASCRIPT, if (jsOn) PermissionDecision.BLOCK else PermissionDecision.ALLOW) },
                        label = { Text(if (jsOn) "On" else "Off") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NeonCyan.copy(alpha = 0.2f)),
                    )
                }
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = { store.clearSite(host); onDismiss() },
                    colors = ButtonDefaults.textButtonColors(contentColor = NeonMagenta),
                ) { Text("Clear settings for this site") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionRow(label: String, current: PermissionDecision, onSet: (PermissionDecision) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                PermissionDecision.ASK to "Ask",
                PermissionDecision.ALLOW to "Allow",
                PermissionDecision.BLOCK to "Block",
            ).forEach { (dec, text) ->
                FilterChip(
                    selected = current == dec,
                    onClick = { onSet(dec) },
                    label = { Text(text, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = if (dec == PermissionDecision.BLOCK) NeonMagenta.copy(alpha = 0.25f) else NeonCyan.copy(alpha = 0.2f),
                    ),
                )
            }
        }
    }
}
