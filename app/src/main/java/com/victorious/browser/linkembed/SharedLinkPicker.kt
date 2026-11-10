package com.victorious.browser.linkembed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.victorious.browser.ui.theme.NeonCyan
import com.victorious.browser.ui.theme.NeonMagenta

/**
 * Shown when text shared into the app contains several http(s) links: lists every
 * link (host + path) so the user picks which one to preview. Nothing is fetched
 * until a link is picked. The list stays available after a preview is closed.
 */
@Composable
fun SharedLinkPickerDialog(
    urls: List<String>,
    onPick: (String) -> Unit,
    onCopyAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${urls.size} links shared — pick one to preview", style = MaterialTheme.typography.titleMedium, color = NeonCyan) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(urls) { url ->
                    val (host, rest) = SharedLinks.displayLabel(url)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(url) }
                            .padding(vertical = 10.dp),
                    ) {
                        Text(host, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (rest.isNotEmpty()) {
                            Text(rest, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = NeonMagenta) } },
        dismissButton = { TextButton(onClick = onCopyAll) { Text("Copy all", color = NeonCyan) } },
    )
}
