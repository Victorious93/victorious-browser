package com.victorious.browser.evidence

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp
import com.victorious.browser.ui.theme.NeonCyan
import com.victorious.browser.ui.theme.NeonMagenta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Evidence screen: manage recon evidence cases, set the active case (where the
 * browser screenshot action lands), add notes, and export a case as a ZIP.
 */
@Composable
fun EvidenceScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember { EvidenceStore.get(context) }
    val cases by store.cases.collectAsState()
    val activeId by store.activeCaseId.collectAsState()
    val scope = rememberCoroutineScope()

    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<EvidenceCase?>(null) }
    var noteFor by remember { mutableStateOf<EvidenceCase?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

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
            Text("Evidence", style = MaterialTheme.typography.titleLarge, color = NeonMagenta)
            TextButton(onClick = onBack) { Text("Back", color = NeonCyan) }
        }
        Text(
            "Recon evidence cases. The active case is where the browser camera action " +
                "and saved request/response exchanges land. Secrets are stripped from " +
                "saved request evidence.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = { adding = true }) { Text("New case") }
        status?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = NeonMagenta)
        }
        Spacer(Modifier.height(8.dp))

        if (cases.isEmpty()) {
            Text("No cases yet.", style = MaterialTheme.typography.bodyMedium)
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(cases, key = { it.id }) { case ->
                val isActive = case.id == activeId
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { store.setActive(case.id) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive) {
                            NeonCyan.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                case.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isActive) NeonCyan else MaterialTheme.colorScheme.onSurface,
                            )
                            if (isActive) {
                                Text("ACTIVE", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                            }
                        }
                        Text(
                            "${case.items.size} items — " +
                                "${case.countByType(EvidenceType.SCREENSHOT)} shots, " +
                                "${case.countByType(EvidenceType.REQUEST)} req, " +
                                "${case.countByType(EvidenceType.FINDING)} find, " +
                                "${case.countByType(EvidenceType.NOTE)} notes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = { noteFor = case }) { Text("Note", color = NeonCyan) }
                            TextButton(onClick = {
                                status = "Exporting…"
                                scope.launch {
                                    val res = withContext(Dispatchers.IO) {
                                        EvidenceExporter.export(context, case, store.caseFolderFor(case))
                                    }
                                    status = res.location?.let { "Exported ZIP → $it" } ?: "Export failed"
                                }
                            }) { Text("Export ZIP", color = NeonCyan) }
                            TextButton(onClick = { deleting = case }) { Text("Del", color = NeonMagenta) }
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("New evidence case") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Case name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    store.createCase(name)
                    adding = false
                }) { Text("Create", color = NeonCyan) }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
        )
    }

    noteFor?.let { case ->
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { noteFor = null },
            title = { Text("Add note to ${case.name}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("Note") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    store.addNote(case.id, host = "", title = title, note = body)
                    noteFor = null
                    Toast.makeText(context, "Note added", Toast.LENGTH_SHORT).show()
                }) { Text("Add", color = NeonCyan) }
            },
            dismissButton = { TextButton(onClick = { noteFor = null }) { Text("Cancel") } },
        )
    }

    deleting?.let { case ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete case?") },
            text = { Text("Remove \"${case.name}\" and all its evidence files.") },
            confirmButton = {
                TextButton(onClick = {
                    store.deleteCase(case.id)
                    deleting = null
                }) { Text("Delete", color = NeonMagenta) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}
