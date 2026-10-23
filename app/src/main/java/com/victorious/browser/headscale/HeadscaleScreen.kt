package com.victorious.browser.headscale

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.victorious.browser.ui.theme.NeonCyan
import com.victorious.browser.ui.theme.NeonMagenta
import kotlinx.coroutines.launch

/**
 * Advanced Headscale settings: configure the control-server URL/API key and drive
 * its `/api/v1` endpoints directly (list nodes, list/create pre-auth keys, expire a
 * node). See [HeadscaleApiClient]'s doc — these request/response shapes are from
 * Headscale's published docs, not verified against a live server in this session.
 */
@Composable
fun HeadscaleScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { HeadscaleSettings.get(context) }
    val serverUrl by settings.serverUrl.collectAsState()
    val apiKey by settings.apiKey.collectAsState()
    var serverUrlInput by remember { mutableStateOf(serverUrl) }
    var apiKeyInput by remember { mutableStateOf(apiKey) }
    var output by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showCreateKey by remember { mutableStateOf(false) }

    fun ready(): Boolean {
        if (serverUrl.isBlank() || apiKey.isBlank()) {
            Toast.makeText(context, "Set and save a server URL and API key first", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    Column(modifier = modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onBack) { Text("← Panels", color = NeonCyan) }
            Text("Headscale", style = MaterialTheme.typography.titleMedium, color = NeonMagenta)
        }

        Text(
            "No VPN/tunnel here — this talks to your Headscale server's HTTP API only " +
                "(node and pre-auth-key management). Pair with WireGuard settings to " +
                "actually connect to a node.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = serverUrlInput,
            onValueChange = { serverUrlInput = it },
            label = { Text("Server URL (e.g. https://headscale.example.com)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = apiKeyInput,
            onValueChange = { apiKeyInput = it },
            label = { Text("API key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = {
                settings.setServerUrl(serverUrlInput.trim())
                settings.setApiKey(apiKeyInput.trim())
            },
        ) { Text("Save", color = NeonCyan) }

        HorizontalDivider(Modifier.height(1.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                enabled = !busy,
                onClick = {
                    if (!ready()) return@TextButton
                    busy = true
                    scope.launch {
                        val result = HeadscaleApiClient.listNodes(serverUrl, apiKey)
                        output = result.fold(
                            onSuccess = { nodes ->
                                if (nodes.isEmpty()) "(no nodes)" else nodes.joinToString("\n") {
                                    "${it.name} (${it.userName}) — ${if (it.online) "online" else "offline"} — ${it.ipAddresses}"
                                }
                            },
                            onFailure = { "List nodes failed: ${it.message}" },
                        )
                        busy = false
                    }
                },
            ) { Text("List nodes") }
            TextButton(onClick = { showCreateKey = true }) { Text("Create pre-auth key") }
        }

        if (output.isNotBlank()) {
            Text(
                output,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = NeonMagenta,
            )
        }
    }

    if (showCreateKey) {
        CreatePreAuthKeyDialog(
            onDismiss = { showCreateKey = false },
            onCreate = { user, reusable, ephemeral ->
                if (!ready()) return@CreatePreAuthKeyDialog
                showCreateKey = false
                scope.launch {
                    val result = HeadscaleApiClient.createPreAuthKey(serverUrl, apiKey, user, reusable, ephemeral, null)
                    output = result.fold(
                        onSuccess = { "Created: $it" },
                        onFailure = { "Create pre-auth key failed: ${it.message}" },
                    )
                }
            },
        )
    }
}

@Composable
private fun CreatePreAuthKeyDialog(
    onDismiss: () -> Unit,
    onCreate: (user: String, reusable: Boolean, ephemeral: Boolean) -> Unit,
) {
    var user by remember { mutableStateOf("") }
    var reusable by remember { mutableStateOf(false) }
    var ephemeral by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create pre-auth key") },
        text = {
            Column {
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text("User (name or ID — depends on your Headscale version)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = reusable, onCheckedChange = { reusable = it })
                    Text("Reusable")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = ephemeral, onCheckedChange = { ephemeral = it })
                    Text("Ephemeral")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (user.isBlank()) return@TextButton
                    onCreate(user.trim(), reusable, ephemeral)
                },
            ) { Text("Create", color = NeonCyan) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
