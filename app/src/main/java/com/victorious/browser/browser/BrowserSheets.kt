package com.victorious.browser.browser

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.victorious.browser.ui.theme.NeonCyan
import com.victorious.browser.ui.theme.NeonMagenta
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@Composable
private fun SheetTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = NeonMagenta)
}

@Composable
internal fun FaviconOrLetter(favicon: Bitmap?, label: String, size: Int = 16) {
    if (favicon != null) {
        Image(favicon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(size.dp))
    } else {
        Box(
            Modifier.size(size.dp).border(1.dp, NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•",
                style = MaterialTheme.typography.labelSmall,
                color = NeonCyan,
            )
        }
    }
}

/** Full tab switcher: 2-column grid of cards with favicon/title/URL. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TabSwitcherSheet(
    tabs: List<BrowserTab>,
    activeId: String?,
    favicons: SnapshotStateMap<String, Bitmap?>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onNewTab: () -> Unit,
    onCloseOthers: () -> Unit,
    onCloseAll: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SheetTitle("Tabs (${tabs.size})")
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onNewTab) { Text("＋ New tab", color = NeonCyan) }
            }
            Row {
                TextButton(onClick = onCloseOthers, enabled = tabs.size > 1) { Text("Close others", color = NeonMagenta) }
                TextButton(onClick = onCloseAll, enabled = tabs.isNotEmpty()) { Text("Close all", color = NeonMagenta) }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.heightIn(max = 520.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(tabs, key = { it.id }) { tab ->
                    val active = tab.id == activeId
                    Column(
                        Modifier
                            .border(
                                if (active) 2.dp else 1.dp,
                                if (active) NeonCyan else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(10.dp),
                            )
                            .background(if (active) NeonCyan.copy(alpha = 0.10f) else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable { onSelect(tab.id) }
                            .padding(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FaviconOrLetter(favicons[tab.id], tabLabel(tab))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                tabLabel(tab),
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "✕",
                                color = NeonMagenta,
                                modifier = Modifier.clickable { onClose(tab.id) }.padding(4.dp),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            tab.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Bookmarks with search, open / new tab / remove. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookmarksSheet(
    bookmarks: List<Bookmark>,
    onDismiss: () -> Unit,
    onOpen: (Bookmark) -> Unit,
    onOpenNewTab: (Bookmark) -> Unit,
    onRemove: (Bookmark) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            SheetTitle("Bookmarks")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(query, { query = it }, label = { Text("Search bookmarks") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            val filtered = bookmarks.filter {
                query.isBlank() || it.url.contains(query, true) || it.title.contains(query, true)
            }
            if (filtered.isEmpty()) {
                Text(
                    if (bookmarks.isEmpty()) "No bookmarks yet. Tap the ★ in the toolbar to save the current page." else "No matches.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(filtered, key = { it.url }) { bm ->
                    UrlRow(bm.title, bm.url, null, onClick = { onOpen(bm) }) {
                        TextButton(onClick = { onOpenNewTab(bm) }) { Text("New tab", color = NeonCyan) }
                        TextButton(onClick = { onRemove(bm) }) { Text("Remove", color = NeonMagenta) }
                    }
                }
            }
        }
    }
}

@Composable
private fun UrlRow(
    title: String,
    url: String,
    meta: String?,
    onClick: () -> Unit,
    actions: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clickable(onClick = onClick)) {
            Text(title.ifBlank { url }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (meta != null) {
                Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        actions()
    }
}

/** Persisted history: search, open, open in new tab, delete one, clear all. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistorySheet(
    history: List<HistoryEntry>,
    onDismiss: () -> Unit,
    onOpen: (HistoryEntry) -> Unit,
    onOpenNewTab: (HistoryEntry) -> Unit,
    onRemove: (HistoryEntry) -> Unit,
    onClearAll: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SheetTitle("History")
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { confirmClear = true }, enabled = history.isNotEmpty()) { Text("Clear all", color = NeonMagenta) }
            }
            OutlinedTextField(query, { query = it }, label = { Text("Search history") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            val filtered = HistoryLogic.search(history, query)
            if (filtered.isEmpty()) {
                Text(
                    if (history.isEmpty()) "No history yet." else "No matches.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(filtered, key = { it.url }) { h ->
                    val meta = fmt.format(Date(h.lastVisited)) + if (h.visits > 1) " · ${h.visits} visits" else ""
                    UrlRow(h.title, h.url, meta, onClick = { onOpen(h) }) {
                        TextButton(onClick = { onOpenNewTab(h) }) { Text("New tab", color = NeonCyan) }
                        TextButton(onClick = { onRemove(h) }) { Text("✕", color = NeonMagenta) }
                    }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear browsing history?") },
            text = { Text("Removes the in-app history list. Proxy capture history and cookies are not affected.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; onClearAll() }) { Text("Clear", color = NeonMagenta) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

/** Downloads started from the browser, with live DownloadManager status (polled while open). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadsSheet(
    records: List<DownloadRecord>,
    statusOf: (Long) -> DownloadStatus,
    onDismiss: () -> Unit,
    onOpenFile: (DownloadRecord) -> Unit,
    onRemove: (DownloadRecord, Boolean) -> Unit,
) {
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { delay(1000); tick++ }
    }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            SheetTitle("Downloads")
            Text(
                "Saved via the system Download Manager to Downloads/. Not routed through the inspection proxy.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            if (records.isEmpty()) {
                Text("No downloads yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(records, key = { it.downloadId }) { r ->
                    @Suppress("UNUSED_EXPRESSION") tick
                    val st = statusOf(r.downloadId)
                    val label = when (st.state) {
                        DownloadState.DONE -> "Done · " + DownloadLogic.formatBytes(st.totalBytes)
                        DownloadState.RUNNING -> "Downloading " + DownloadLogic.formatBytes(st.bytesSoFar) +
                            (if (st.totalBytes > 0) " / " + DownloadLogic.formatBytes(st.totalBytes) else "")
                        DownloadState.PENDING -> "Pending"
                        DownloadState.PAUSED -> "Paused (waiting for network?)"
                        DownloadState.FAILED -> "Failed"
                        DownloadState.MISSING -> "Removed from system list"
                    }
                    UrlRow(r.fileName, r.url, label, onClick = { if (st.state == DownloadState.DONE) onOpenFile(r) }) {
                        if (st.state == DownloadState.DONE) {
                            TextButton(onClick = { onOpenFile(r) }) { Text("Open", color = NeonCyan) }
                        }
                        TextButton(onClick = { onRemove(r, st.state != DownloadState.DONE) }) {
                            Text(if (st.state == DownloadState.DONE) "✕" else "Cancel", color = NeonMagenta)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

/** Long-press menu for a link (or linked image). */
@Composable
internal fun LinkMenuDialog(
    url: String,
    isImage: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onOpenNewTab: () -> Unit,
    onOpenBackground: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onBookmark: () -> Unit,
    onDownload: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(url, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
        },
        text = {
            Column {
                @Composable
                fun item(label: String, action: () -> Unit) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable { onDismiss(); action() }.padding(vertical = 10.dp),
                    )
                }
                item("Open", onOpen)
                item("Open in new tab", onOpenNewTab)
                item("Open in background tab", onOpenBackground)
                item("Copy link", onCopy)
                item("Share link", onShare)
                item("Bookmark link", onBookmark)
                item(if (isImage) "Download image" else "Download link", onDownload)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun SearchEngineDialog(
    current: SearchEngine,
    onPick: (SearchEngine) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search engine") },
        text = {
            Column {
                SearchEngine.entries.forEach { e ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(e) }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = e == current, onClick = { onPick(e) })
                        Text(e.label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = NeonCyan) } },
    )
}

/** Address-bar suggestion list (shown under the field while editing). */
@Composable
internal fun SuggestionList(
    suggestions: List<Suggestion>,
    typed: String,
    searchLabel: String,
    onPick: (Suggestion) -> Unit,
    onSearch: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)),
    ) {
        val looksUrl = looksLikeUrl(typed.trim()) || typed.contains("://")
        Text(
            if (looksUrl) "Go to  ${typed.trim()}" else "Search $searchLabel for “${typed.trim()}”",
            style = MaterialTheme.typography.bodyMedium,
            color = NeonCyan,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onSearch).padding(horizontal = 12.dp, vertical = 10.dp),
        )
        suggestions.forEach { s ->
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().clickable { onPick(s) }.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when (s.source) {
                        SuggestionSource.OPEN_TAB -> "⧉"
                        SuggestionSource.BOOKMARK -> "★"
                        SuggestionSource.HISTORY -> "⟲"
                    },
                    color = if (s.source == SuggestionSource.BOOKMARK) NeonMagenta else NeonCyan,
                    modifier = Modifier.width(22.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(s.title.ifBlank { s.url }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        (if (s.source == SuggestionSource.OPEN_TAB) "Switch to tab · " else "") + s.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
