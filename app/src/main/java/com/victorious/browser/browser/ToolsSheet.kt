package com.victorious.browser.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.victorious.browser.ui.theme.NeonCyan
import com.victorious.browser.ui.theme.NeonMagenta

/**
 * Neon Tools drawer opened from the browser toolbar. Lists every fleet feature that
 * used to live on the launcher home screen. Picking one opens that screen as an
 * overlay; Back returns to the browser (tabs stay alive underneath).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ToolsSheet(
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text("Fleet tools", style = MaterialTheme.typography.titleLarge, color = NeonCyan)
            Text(
                "Everything that used to live on the home screen — reachable from inside the browser.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ToolsCatalog.quickIds.forEach { id ->
                    val t = ToolsCatalog.find(id) ?: return@forEach
                    FilterChip(
                        selected = false,
                        onClick = { onDismiss(); onOpen(id) },
                        label = { Text(t.title) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = NeonMagenta.copy(alpha = 0.12f),
                            labelColor = NeonMagenta,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                ToolsCatalog.groups.forEach { group ->
                    item {
                        Text(
                            group.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = NeonCyan,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(group.tools) { tool ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onDismiss(); onOpen(tool.id) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(tool.title, style = MaterialTheme.typography.bodyLarge)
                                Text(tool.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("›", color = NeonMagenta)
                        }
                        HorizontalDivider()
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Close", color = NeonMagenta)
            }
        }
    }
}
