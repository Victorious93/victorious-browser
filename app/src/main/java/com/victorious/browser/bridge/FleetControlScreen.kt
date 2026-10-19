package com.victorious.browser.bridge

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.victorious.browser.ui.theme.NeonCyan
import com.victorious.browser.ui.theme.NeonMagenta

/**
 * In-app "Fleet control" settings: external-control on/off (OFF by default),
 * read-only node-id, and pinned controller info.
 */
@Composable
fun FleetControlScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settings = remember { BridgeSettings.get(context) }
    val enabled by settings.externalControlEnabled.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Back", color = NeonCyan) }
        Text(
            "Fleet control",
            style = MaterialTheme.typography.titleLarge,
            color = NeonMagenta,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Bound Messenger bridge for the owner's first-party controller. " +
                "External control is OFF by default and cert-pinned to " +
                "${ControllerAllowlist.CONTROLLER_PACKAGE}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("External control", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (enabled) "ON — allowlisted controller may drive this node"
                    else "OFF — all bridge ops rejected with not_paired",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { settings.setExternalControlEnabled(it) },
            )
        }

        Spacer(Modifier.height(16.dp))
        Text("Node id", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
        Text(
            settings.nodeId,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
        )

        Spacer(Modifier.height(16.dp))
        Text("Pinned controller", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
        Text("Package: ${ControllerAllowlist.CONTROLLER_PACKAGE}", fontFamily = FontFamily.Monospace)
        Text(
            "(no ai.droidcommand.app.debug pin — dropped by the fleet)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text("DEBUG cert SHA-256 (fleet-provided controller):", style = MaterialTheme.typography.labelSmall)
        Text(ControllerAllowlist.DEBUG_CERT_SHA256, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(4.dp))
        Text("RELEASE cert SHA-256 (fleet-provided controller caller pin):", style = MaterialTheme.typography.labelSmall)
        Text(ControllerAllowlist.RELEASE_CERT_SHA256, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)

        Spacer(Modifier.height(16.dp))
        Text("Protocol", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
        Text("BRIDGE_PROTOCOL_MAJOR = ${BridgeProtocol.BRIDGE_PROTOCOL_MAJOR}")
        Text("Action: ${BridgeProtocol.ACTION}")
        Text("Message.what request=${BridgeProtocol.MSG_WHAT_REQUEST} response=${BridgeProtocol.MSG_WHAT_RESPONSE}")
        Text(
            "Check order: sendingUid identity → external-control switch → envelope",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
