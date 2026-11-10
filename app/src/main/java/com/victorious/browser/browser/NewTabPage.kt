package com.victorious.browser.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.victorious.browser.ui.theme.NeonCyan
import com.victorious.browser.ui.theme.NeonMagenta

/** Sentinel URL for a fresh tab that shows the neon new-tab page (never loaded into WebView). */
const val NEW_TAB_URL = "victorious://newtab"

/** True when [url] should show the Compose new-tab page instead of a WebView document. */
fun isNewTabUrl(url: String?): Boolean =
    url.isNullOrBlank() || url.equals(NEW_TAB_URL, ignoreCase = true)

/**
 * Compact neon new-tab page: brand mark, quick fleet-tool chips, and a tip to use the
 * address bar. Replaces the old habit of dumping every new tab on example.com.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewTabPage(
    isPrivate: Boolean,
    onOpenTool: (String) -> Unit,
    onShowAllTools: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        com.victorious.browser.ui.components.BrandRing(size = 72.dp, strokeWidth = 5.dp)
        Spacer(Modifier.height(14.dp))
        com.victorious.browser.ui.components.GradientTitle("Victorious")
        Text(
            if (isPrivate) "Private tab · nothing here is saved" else "Search or enter an address above",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(22.dp))
        Text("Fleet tools", style = MaterialTheme.typography.labelLarge, color = NeonCyan, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ToolsCatalog.quickIds.forEach { id ->
                val t = ToolsCatalog.find(id) ?: return@forEach
                FilterChip(
                    selected = false,
                    onClick = { onOpenTool(id) },
                    label = { Text(t.title) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = NeonMagenta.copy(alpha = 0.14f),
                        labelColor = NeonMagenta,
                    ),
                )
            }
            FilterChip(
                selected = false,
                onClick = onShowAllTools,
                label = { Text("All tools…") },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = NeonCyan.copy(alpha = 0.14f),
                    labelColor = NeonCyan,
                ),
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "Tip: the brand ring next to the address bar also opens Fleet tools.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
