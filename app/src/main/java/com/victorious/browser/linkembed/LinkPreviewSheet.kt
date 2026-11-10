package com.victorious.browser.linkembed

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.victorious.browser.ui.components.NeonButton
import com.victorious.browser.ui.components.NeonOutlineButton
import com.victorious.browser.ui.components.NeonStatus
import com.victorious.browser.ui.components.StatusChip
import com.victorious.browser.ui.theme.NeonCyan
import com.victorious.browser.ui.theme.NeonMagenta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed class PreviewState {
    object NeedsTap : PreviewState()
    object Loading : PreviewState()
    data class Done(val preview: LinkPreview, val image: ImageBitmap?) : PreviewState()
    data class Rejected(val reason: String) : PreviewState()
}

/**
 * Link preview + embed recon + copy-as-embed sheet. Entry points: long-press a link
 * → Preview link, ⋮ → Recon tools ▸ Link preview & embed recon, and links shared to
 * the app (ACTION_SEND). In a private tab nothing is fetched until the user taps
 * "Load preview" (see [PreviewPolicy]).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LinkPreviewSheet(
    initialUrl: String,
    isPrivate: Boolean,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
    onOpenNewTab: (String) -> Unit,
    onCopy: (text: String, toast: String) -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var urlInput by remember { mutableStateOf(initialUrl) }
    var target by remember { mutableStateOf(initialUrl) }
    var tapped by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<PreviewState>(PreviewState.NeedsTap) }
    var showViewer by remember { mutableStateOf(false) }
    var showCopy by remember { mutableStateOf(false) }
    var forceRefresh by remember { mutableStateOf(false) }

    LaunchedEffect(target, tapped, reloadKey) {
        val check = UrlSafety.check(target)
        if (check is UrlSafety.Result.Rejected) {
            state = PreviewState.Rejected(check.reason)
            return@LaunchedEffect
        }
        val safe = (check as UrlSafety.Result.Ok).url
        if (!PreviewPolicy.mayFetch(isPrivate, tapped)) {
            state = PreviewState.NeedsTap
            return@LaunchedEffect
        }
        val cached = if (!forceRefresh) LinkPreviewEnvironment.cache.get(safe) else null
        forceRefresh = false
        state = PreviewState.Loading
        val preview = cached ?: withContext(Dispatchers.IO) { LinkPreviewEnvironment.service(context).load(safe) }
        if (cached == null && PreviewPolicy.mayCache(isPrivate)) LinkPreviewEnvironment.cache.put(safe, preview)
        val bmp = preview.imageBytes?.let { bytes -> withContext(Dispatchers.Default) { decodeBounded(bytes) } }
        state = PreviewState.Done(preview, bmp?.asImageBitmap())
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Link preview & embed", style = MaterialTheme.typography.titleMedium, color = NeonMagenta)
                Spacer(Modifier.weight(1f))
                if (isPrivate) StatusChip("PRIVATE", NeonStatus.RISK)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                singleLine = true,
                label = { Text("URL (http/https only)") },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan, cursorColor = NeonMagenta),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    val t = urlInput.trim()
                    // Private tabs: a new URL always needs a fresh explicit tap before any fetch.
                    if (isPrivate) tapped = false
                    if (t != target) target = t else reloadKey++
                }) { Text("Check URL", color = NeonCyan) }
                Spacer(Modifier.weight(1f))
                Text(
                    LinkPreviewEnvironment.routeSummary(context),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(2f),
                )
            }
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            when (val s = state) {
                PreviewState.NeedsTap -> {
                    Text(
                        "Private tab: nothing has been fetched. Loading a preview contacts this site (and any " +
                            "oEmbed / image host) from your device, without cookies.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    NeonButton("Load preview", onClick = { tapped = true; reloadKey++ })
                }
                PreviewState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = NeonCyan, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Fetching metadata and framing headers…", style = MaterialTheme.typography.bodySmall)
                }
                is PreviewState.Rejected -> Text(
                    "Blocked: ${s.reason}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonMagenta,
                )
                is PreviewState.Done -> {
                    PreviewCard(s.preview, s.image)
                    Spacer(Modifier.height(12.dp))
                    ReconBlock(s.preview.recon)
                    Spacer(Modifier.height(12.dp))
                    val open = s.preview.finalUrl ?: s.preview.requestedUrl
                    val plan = remember(s.preview) { EmbedViewerHtml.plan(s.preview) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        NeonButton("Open", onClick = { onDismiss(); onOpen(open) })
                        NeonOutlineButton("New tab", onClick = { onDismiss(); onOpenNewTab(open) })
                        NeonOutlineButton("View embedded", onClick = { showViewer = true })
                        NeonOutlineButton(if (showCopy) "Copy as embed ▾" else "Copy as embed ▸", onClick = { showCopy = !showCopy })
                        TextButton(onClick = { forceRefresh = true; reloadKey++ }) { Text("Refresh", color = NeonCyan) }
                    }
                    if (plan is EmbedViewerHtml.Plan.NotEmbeddable) {
                        Text(
                            "Viewer: ${plan.reason}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonMagenta,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    if (showCopy) {
                        Spacer(Modifier.height(8.dp))
                        CopyAsEmbed(s.preview, onCopy)
                    }
                    if (showViewer) {
                        EmbedViewerDialog(
                            plan = plan,
                            title = s.preview.displayTitle,
                            pageUrl = open,
                            onDismiss = { showViewer = false },
                            onOpenInTab = { url -> showViewer = false; onDismiss(); onOpenNewTab(url) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(p: LinkPreview, image: ImageBitmap?) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .background(NeonCyan.copy(alpha = 0.05f))
            .padding(12.dp),
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = p.metadata?.imageAlt ?: "Preview image",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(10.dp)),
            )
            Spacer(Modifier.height(8.dp))
        }
        p.displaySite?.let { Text(it.uppercase(), style = MaterialTheme.typography.labelSmall, color = NeonMagenta, maxLines = 1) }
        Text(p.displayTitle, style = MaterialTheme.typography.titleSmall, color = NeonCyan, maxLines = 3, overflow = TextOverflow.Ellipsis)
        p.metadata?.description?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 5, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            p.finalUrl ?: p.requestedUrl,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val notes = buildList {
            p.oembed?.let { add("oEmbed: ${it.type}${it.providerName?.let { n -> " · $n" } ?: ""}") }
            p.metadata?.let { add("title from ${it.titleSource}") }
            p.imageNote?.let { add(it) }
            if (p.truncated) add("page larger than ${LinkPreviewService.MAX_PAGE_BYTES / 1024} KB; only the start was read")
            p.error?.let { add("error: $it") }
        }
        if (notes.isNotEmpty()) {
            Text(
                notes.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun ReconBlock(r: EmbedRecon.Report) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Embed recon", style = MaterialTheme.typography.titleSmall, color = NeonMagenta)
        Spacer(Modifier.width(8.dp))
        StatusChip(
            r.verdict.label,
            when (r.verdict) {
                EmbedRecon.Verdict.EMBEDDABLE -> NeonStatus.ON
                EmbedRecon.Verdict.CONDITIONAL -> NeonStatus.WARN
                EmbedRecon.Verdict.NOT_EMBEDDABLE -> NeonStatus.RISK
                EmbedRecon.Verdict.UNKNOWN -> NeonStatus.IDLE
            },
        )
    }
    Spacer(Modifier.height(4.dp))
    @Composable
    fun row(k: String, v: String?) {
        Row(Modifier.padding(vertical = 1.dp)) {
            Text("$k  ", style = MaterialTheme.typography.labelSmall, color = NeonCyan, modifier = Modifier.width(118.dp))
            Text(
                v ?: "— (absent)",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = if (v == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
    row("Final URL", r.finalUrl)
    row("HTTP status", r.status?.toString())
    row("Content-Type", r.contentType)
    row("X-Frame-Options", r.xFrameOptions)
    row("CSP frame-anc.", r.frameAncestors)
    if (r.reportOnlyFrameAncestors != null) row("CSP-RO (not enf.)", r.reportOnlyFrameAncestors)
    row("Redirects", if (r.redirects.isEmpty()) "0" else "${r.redirects.size}: " + r.redirects.joinToString(" → "))
    r.reasons.forEach {
        Text("• $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
    }
    Text(
        EmbedRecon.DISCLAIMER,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CopyAsEmbed(p: LinkPreview, onCopy: (String, String) -> Unit) {
    val url = p.finalUrl ?: p.requestedUrl
    val snippets = remember(p) { EmbedSnippets.build(url, p.displayTitle.takeIf { it != url }, p.recon.verdict, p.oembed?.html) }
    if (snippets == null) {
        Text("Nothing to copy: URL is not http(s).", style = MaterialTheme.typography.bodySmall, color = NeonMagenta)
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        NeonOutlineButton("Markdown", onClick = { onCopy(snippets.markdown, "Markdown link copied") })
        NeonOutlineButton("HTML link", onClick = { onCopy(snippets.htmlLink, "HTML link copied") })
        if (snippets.iframe != null) {
            NeonOutlineButton("HTML iframe", onClick = { onCopy(snippets.iframe, "iframe copied") })
        } else {
            NeonOutlineButton("Link (no iframe)", onClick = { onCopy(snippets.iframeOrFallback, "Link copied — iframe omitted") })
        }
        if (snippets.oembedHtml != null) {
            NeonOutlineButton("oEmbed HTML", onClick = { onCopy(snippets.oembedHtml, "Provider oEmbed HTML copied (untrusted)") })
        }
    }
    snippets.iframeOmittedReason?.let {
        Text("iframe omitted: $it", style = MaterialTheme.typography.labelSmall, color = NeonMagenta, modifier = Modifier.padding(top = 4.dp))
    }
    if (snippets.oembedHtml != null) {
        Text(
            "oEmbed HTML is copied exactly as the provider sent it. It is untrusted third-party markup (it may load scripts); review it before pasting.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Decodes at most ~1024px on the long edge; returns null for undecodable data. */
private fun decodeBounded(bytes: ByteArray): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}.getOrNull()
