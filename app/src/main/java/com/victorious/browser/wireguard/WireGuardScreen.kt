package com.victorious.browser.wireguard

import android.app.Activity
import android.widget.Toast
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp
import com.wireguard.android.backend.Tunnel
import com.victorious.browser.ui.theme.NeonCyan
import com.victorious.browser.ui.theme.NeonMagenta
import kotlinx.coroutines.launch

/**
 * Advanced WireGuard settings: create/edit named profiles and connect/disconnect the
 * one tunnel this app can run at a time (see [WireGuardTunnelManager]'s doc for the
 * device-VPN-slot conflict with the separate Victorious Mesh app — this screen warns
 * about it inline rather than hiding it).
 */
@Composable
fun WireGuardScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { WireGuardProfileStore.get(context) }
    val profiles by store.profiles.collectAsState()
    val tunnelState by WireGuardTunnelManager.state.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<WireGuardProfileFields?>(null) }
    var deleting by remember { mutableStateOf<WireGuardProfileFields?>(null) }
    var pendingConnect by remember { mutableStateOf<WireGuardProfileFields?>(null) }

    val vpnConsentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val profile = pendingConnect
        pendingConnect = null
        if (profile == null) return@rememberLauncherForActivityResult
        if (result.resultCode == Activity.RESULT_OK) {
            scope.launch {
                val outcome = WireGuardTunnelManager.connect(context, profile)
                if (outcome.isFailure) {
                    Toast.makeText(context, "Connect failed: ${outcome.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            Toast.makeText(context, "VPN permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestConnect(profile: WireGuardProfileFields) {
        val intent = WireGuardTunnelManager.prepareIntent(context)
        if (intent != null) {
            pendingConnect = profile
            vpnConsentLauncher.launch(intent)
        } else {
            scope.launch {
                val outcome = WireGuardTunnelManager.connect(context, profile)
                if (outcome.isFailure) {
                    Toast.makeText(context, "Connect failed: ${outcome.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onBack) { Text("← Panels", color = NeonCyan) }
            Text("WireGuard", style = MaterialTheme.typography.titleMedium, color = NeonMagenta)
        }

        Text(
            "Connecting takes the device's one VPN slot. If Victorious Mesh's VPN is " +
                "running, the OS tears it down the moment this one comes up, and vice " +
                "versa — Android allows exactly one active VPN app at a time; this app " +
                "and the Mesh app can't coordinate that for you.",
            style = MaterialTheme.typography.labelSmall,
            color = NeonMagenta,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Text(
            "Tunnel state: ${tunnelState.name}",
            style = MaterialTheme.typography.titleSmall,
            color = if (tunnelState == Tunnel.State.UP) NeonCyan else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (tunnelState == Tunnel.State.UP) {
            TextButton(onClick = { scope.launch { WireGuardTunnelManager.disconnect(context) } }) {
                Text("Disconnect", color = NeonMagenta)
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(onClick = { adding = true }) { Text("Add profile") }
        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(profiles, key = { it.name }) { profile ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = { editing = profile }),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(profile.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            profile.endpoint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(
                                enabled = tunnelState != Tunnel.State.UP,
                                onClick = { requestConnect(profile) },
                            ) { Text("Connect", color = NeonCyan) }
                            TextButton(onClick = { deleting = profile }) { Text("Del", color = NeonMagenta) }
                        }
                    }
                }
            }
        }
    }

    if (adding || editing != null) {
        WireGuardProfileDialog(
            initial = editing ?: WireGuardProfileFields(
                name = WireGuardProfileStore.newProfileNamePlaceholder(),
                privateKeyBase64 = "",
                addresses = emptyList(),
                peerPublicKeyBase64 = "",
                endpoint = "",
                allowedIps = listOf("0.0.0.0/0", "::/0"),
            ),
            originalName = editing?.name,
            onDismiss = { adding = false; editing = null },
            onSave = { fields, originalName ->
                if (originalName != null) store.update(originalName, fields) else store.add(fields)
                adding = false
                editing = null
            },
        )
    }

    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${target.name}\"?") },
            confirmButton = {
                TextButton(onClick = { store.delete(target.name); deleting = null }) { Text("Delete", color = NeonMagenta) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun WireGuardProfileDialog(
    initial: WireGuardProfileFields,
    originalName: String?,
    onDismiss: () -> Unit,
    onSave: (WireGuardProfileFields, String?) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var privateKey by remember { mutableStateOf(initial.privateKeyBase64) }
    var addresses by remember { mutableStateOf(initial.addresses.joinToString(", ")) }
    var dns by remember { mutableStateOf(initial.dnsServers.joinToString(", ")) }
    var listenPort by remember { mutableStateOf(initial.listenPort?.toString() ?: "") }
    var peerPublicKey by remember { mutableStateOf(initial.peerPublicKeyBase64) }
    var presharedKey by remember { mutableStateOf(initial.presharedKeyBase64 ?: "") }
    var endpoint by remember { mutableStateOf(initial.endpoint) }
    var allowedIps by remember { mutableStateOf(initial.allowedIps.joinToString(", ")) }
    var keepalive by remember { mutableStateOf(initial.persistentKeepaliveSeconds?.toString() ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (originalName != null) "Edit profile" else "Add profile") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(privateKey, { privateKey = it }, label = { Text("Private key (base64)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(addresses, { addresses = it }, label = { Text("Address(es), comma-separated CIDR") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(dns, { dns = it }, label = { Text("DNS servers (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(listenPort, { listenPort = it }, label = { Text("Listen port (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(peerPublicKey, { peerPublicKey = it }, label = { Text("Peer public key (base64)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(presharedKey, { presharedKey = it }, label = { Text("Preshared key (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(endpoint, { endpoint = it }, label = { Text("Endpoint (host:port)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(allowedIps, { allowedIps = it }, label = { Text("Allowed IPs, comma-separated CIDR") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(keepalive, { keepalive = it }, label = { Text("Persistent keepalive, seconds (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = NeonMagenta, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val fields = WireGuardProfileFields(
                        name = name.trim(),
                        privateKeyBase64 = privateKey.trim(),
                        addresses = addresses.split(',').map { it.trim() }.filter { it.isNotBlank() },
                        dnsServers = dns.split(',').map { it.trim() }.filter { it.isNotBlank() },
                        listenPort = listenPort.trim().toIntOrNull(),
                        peerPublicKeyBase64 = peerPublicKey.trim(),
                        presharedKeyBase64 = presharedKey.trim().takeIf { it.isNotBlank() },
                        endpoint = endpoint.trim(),
                        allowedIps = allowedIps.split(',').map { it.trim() }.filter { it.isNotBlank() },
                        persistentKeepaliveSeconds = keepalive.trim().toIntOrNull(),
                    )
                    if (fields.name.isBlank()) {
                        error = "Name is required"
                        return@TextButton
                    }
                    // Validate via the real WireGuard library before saving — the same
                    // check that would otherwise only surface at Connect time.
                    val parsed = WireGuardConfigBuilder.parse(fields)
                    if (parsed.isFailure) {
                        error = "Invalid config: ${parsed.exceptionOrNull()?.message}"
                        return@TextButton
                    }
                    onSave(fields, originalName)
                },
            ) { Text("Save", color = NeonCyan) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
