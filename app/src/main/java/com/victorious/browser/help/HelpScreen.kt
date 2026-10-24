package com.victorious.browser.help

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.victorious.browser.ui.components.GradientCard
import com.victorious.browser.ui.components.NeonSectionHeader
import com.victorious.browser.ui.components.ToolScaffold
import com.victorious.browser.ui.theme.NeonCyan
import com.victorious.browser.ui.theme.NeonMagenta

/** Loads and parses the bundled manual asset. Cached per-process. */
object ManualAsset {
    const val ASSET_PATH = "manual/MANUAL.md"
    @Volatile private var cached: ManualDocument? = null

    fun load(assets: android.content.res.AssetManager): ManualDocument {
        cached?.let { return it }
        val text = runCatching {
            assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        }.getOrElse { "# Victorious Browser — Manual\n\n## Error\n\nManual asset could not be loaded." }
        val doc = ManualParser.parse(text)
        cached = doc
        return doc
    }
}

/**
 * In-app Help / Manual screen. Renders the bundled `MANUAL.md` (the single source
 * of truth, shared with `docs/MANUAL.md`) with a keyword section filter, using the
 * neon design system (ToolScaffold + BrandRing header art).
 */
@Composable
fun HelpScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val doc = remember { ManualAsset.load(context.assets) }
    var query by remember { mutableStateOf("") }
    val results = remember(query) { ManualSearch.filter(doc.sections, query) }

    ToolScaffold(
        title = "Help / Manual",
        subtitle = "Authorized-testing use only",
        onBack = onBack,
        modifier = modifier,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search the manual") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = NeonCyan) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${results.size} of ${doc.sections.size} sections",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (results.isEmpty()) {
                item {
                    GradientCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "No sections match \"$query\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            items(results, key = { it.anchor }) { section ->
                SectionCard(section)
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SectionCard(section: ManualSection) {
    GradientCard(modifier = Modifier.fillMaxWidth()) {
        NeonSectionHeader(section.title)
        Spacer(Modifier.height(6.dp))
        section.blocks.forEach { block ->
            when (block) {
                is ManualBlock.Heading -> {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        block.text,
                        style = MaterialTheme.typography.titleSmall,
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                }
                is ManualBlock.Paragraph -> {
                    Text(
                        block.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                is ManualBlock.Bullet -> {
                    Row(Modifier.padding(start = 4.dp, bottom = 3.dp)) {
                        Text("•  ", color = NeonMagenta, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            block.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                is ManualBlock.Code -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(6.dp),
                            )
                            .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .horizontalScroll(rememberScrollState())
                            .padding(10.dp),
                    ) {
                        Text(
                            block.text,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                            ),
                            color = Color(0xFFBFefff),
                        )
                    }
                }
            }
        }
    }
}
