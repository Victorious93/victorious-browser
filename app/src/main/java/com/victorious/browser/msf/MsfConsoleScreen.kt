package com.victorious.browser.msf

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Metasploit RPC console UI: connect (host/port/ssl/user/pass), an interactive
 * console (create → read/write → destroy), and a session list.
 *
 * Off by default — no field is pre-filled with a working host, and nothing
 * connects until the user enters credentials and taps Connect.
 */
@Composable
fun MsfConsoleScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val config = remember { MsfConfig.get(context) }
    val cfg by config.config.collectAsState()
    val scope = rememberCoroutineScope()

    var host by remember(cfg.host) { mutableStateOf(cfg.host) }
    var port by remember(cfg.port) { mutableStateOf(cfg.port.toString()) }
    var ssl by remember(cfg.ssl) { mutableStateOf(cfg.ssl) }
    var user by remember(cfg.username) { mutableStateOf(cfg.username) }
    var pass by remember(cfg.password) { mutableStateOf(cfg.password) }

    var client by remember { mutableStateOf<MsfRpcClient?>(null) }
    var connected by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    var consoleId by remember { mutableStateOf<String?>(null) }
    var consoleOut by remember { mutableStateOf("") }
    var cmd by remember { mutableStateOf("") }
    var sessions by remember { mutableStateOf("") }

    fun runBg(block: suspend () -> Unit) {
        busy = true
        scope.launch {
            withContext(Dispatchers.IO) { block() }
            busy = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Metasploit RPC", style = MaterialTheme.typography.titleLarge, color = NeonMagenta)
            TextButton(onClick = onBack) { Text("Back", color = NeonCyan) }
        }
        Text(
            "Connects to your own Metasploit msgrpc (msgpack over HTTP, /api/1.0/). " +
                "Off by default; nothing connects without credentials + Connect.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = host, onValueChange = { host = it },
                    label = { Text("Host") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = port, onValueChange = { port = it.filter { c -> c.isDigit() } },
                        label = { Text("Port") }, singleLine = true,
                        modifier = Modifier.height(64.dp),
                    )
                    Spacer(Modifier.height(0.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = ssl, onCheckedChange = { ssl = it })
                        Text("SSL")
                    }
                }
                OutlinedTextField(
                    value = user, onValueChange = { user = it },
                    label = { Text("Username") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it },
                    label = { Text("Password") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !busy && host.isNotBlank() && user.isNotBlank(),
                        onClick = {
                            val p = port.toIntOrNull() ?: 55553
                            config.set(host, p, ssl, user, pass)
                            val c = MsfRpcClient(host.trim(), p, ssl)
                            runBg {
                                val res = c.login(user.trim(), pass)
                                client = c
                                connected = res.ok
                                status = if (res.ok) "Connected. Token acquired." else "Login failed: ${res.error}"
                            }
                        },
                    ) { Text("Connect") }
                    OutlinedButton(
                        enabled = !busy && connected,
                        onClick = {
                            val c = client ?: return@OutlinedButton
                            runBg {
                                c.logout()
                                connected = false
                                consoleId = null
                                status = "Disconnected."
                            }
                        },
                    ) { Text("Disconnect") }
                }
            }
        }

        status?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = NeonMagenta)
        }

        if (connected) {
            Spacer(Modifier.height(12.dp))
            Text("Console", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy && consoleId == null, onClick = {
                    val c = client ?: return@Button
                    runBg {
                        val res = c.consoleCreate()
                        consoleId = MsfProtocol.asString(MsfProtocol.mapGet(res.value, "id"))
                        status = if (res.ok) "Console ${consoleId} created." else "create failed: ${res.error}"
                    }
                }) { Text("New console") }
                OutlinedButton(enabled = !busy && consoleId != null, onClick = {
                    val c = client ?: return@OutlinedButton
                    val id = consoleId ?: return@OutlinedButton
                    runBg {
                        val res = c.consoleRead(id)
                        val data = MsfProtocol.asString(MsfProtocol.mapGet(res.value, "data")).orEmpty()
                        consoleOut += data
                    }
                }) { Text("Read") }
                OutlinedButton(enabled = !busy && consoleId != null, onClick = {
                    val c = client ?: return@OutlinedButton
                    val id = consoleId ?: return@OutlinedButton
                    runBg {
                        c.consoleDestroy(id)
                        consoleId = null
                        status = "Console destroyed."
                    }
                }) { Text("Destroy") }
            }
            Spacer(Modifier.height(6.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Text(
                    consoleOut.ifEmpty { "(no output yet — Read to poll)" },
                    modifier = Modifier.padding(8.dp).fillMaxWidth().height(180.dp)
                        .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = cmd, onValueChange = { cmd = it },
                    label = { Text("command") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(0.7f),
                )
                Spacer(Modifier.height(0.dp))
                Button(enabled = !busy && consoleId != null && cmd.isNotBlank(), onClick = {
                    val c = client ?: return@Button
                    val id = consoleId ?: return@Button
                    val toSend = cmd
                    cmd = ""
                    runBg {
                        c.consoleWrite(id, toSend)
                        val res = c.consoleRead(id)
                        consoleOut += "> $toSend\n" +
                            MsfProtocol.asString(MsfProtocol.mapGet(res.value, "data")).orEmpty()
                    }
                }) { Text("Send") }
            }

            Spacer(Modifier.height(12.dp))
            Text("Sessions", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
            Button(enabled = !busy, onClick = {
                val c = client ?: return@Button
                runBg {
                    val res = c.sessionList()
                    sessions = res.value?.toString() ?: res.error.orEmpty()
                }
            }) { Text("Refresh sessions") }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Text(
                    sessions.ifEmpty { "(none loaded)" },
                    modifier = Modifier.padding(8.dp).fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
