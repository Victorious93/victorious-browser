package com.victorious.browser.sandbox

import android.widget.Toast
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.victorious.browser.ui.theme.NeonCyan
import com.victorious.browser.ui.theme.NeonMagenta

/**
 * Compose "Sandboxes" screen: list / create / delete sandboxes, each backed directly by
 * an `androidx.webkit` Profile (see [SandboxManager]'s doc) rather than a local store.
 */
@Composable
fun SandboxesScreen(
    onBack: () -> Unit,
    onOpenSandbox: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val supported = remember { SandboxManager.isSupported() }
    var sandboxes by remember { mutableStateOf(SandboxManager.list()) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onBack) { Text("← Panels", color = NeonCyan) }
            Text("Sandboxes", style = MaterialTheme.typography.titleMedium, color = NeonMagenta)
        }

        if (!supported) {
            Text(
                "This WebView doesn't support isolated profiles — sandboxes can't be " +
                    "created here. Everything falls back to the one shared (default) profile.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            return@Column
        }

        Button(onClick = { adding = true }) { Text("Add sandbox") }
        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(sandboxes, key = { it }) { name ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable(onClick = { onOpenSandbox(name) }),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(name, style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = { deleting = name }) { Text("Del", color = NeonMagenta) }
                    }
                }
            }
        }
    }

    if (adding) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Add sandbox") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (letters, digits, - or _)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = name.trim()
                        if (!SandboxName.isValid(trimmed)) {
                            Toast.makeText(context, "Invalid or reserved name", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        if (SandboxManager.create(trimmed)) {
                            sandboxes = SandboxManager.list()
                            adding = false
                        } else {
                            Toast.makeText(context, "Couldn't create sandbox", Toast.LENGTH_SHORT).show()
                        }
                    },
                ) { Text("Create", color = NeonCyan) }
            },
            dismissButton = {
                TextButton(onClick = { adding = false }) { Text("Cancel") }
            },
        )
    }

    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"$target\"?") },
            text = { Text("Its cookies and storage are deleted too. Close any open browser tab using it first.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (SandboxManager.delete(target)) {
                            sandboxes = SandboxManager.list()
                        } else {
                            Toast.makeText(context, "Couldn't delete — is it still open in a browser tab?", Toast.LENGTH_LONG).show()
                        }
                        deleting = null
                    },
                ) { Text("Delete", color = NeonMagenta) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancel") }
            },
        )
    }
}
