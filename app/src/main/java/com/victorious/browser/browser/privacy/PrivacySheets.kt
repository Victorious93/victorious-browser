package com.victorious.browser.browser.privacy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.victorious.browser.ui.components.NeonButton
import com.victorious.browser.ui.theme.NeonCyan
import com.victorious.browser.ui.theme.NeonMagenta
import com.victorious.browser.ui.theme.NeonViolet

private val brandBrush get() = Brush.linearGradient(listOf(NeonCyan, NeonViolet, NeonMagenta))

/** Victorious shield glyph (own drawing, brand gradient). Dimmed when protections are off. */
@Composable
fun PrivacyShieldIcon(active: Boolean, modifier: Modifier = Modifier.size(22.dp)) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val p = Path().apply {
            moveTo(w * 0.5f, h * 0.06f)
            lineTo(w * 0.88f, h * 0.2f)
            cubicTo(w * 0.88f, h * 0.6f, w * 0.7f, h * 0.82f, w * 0.5f, h * 0.95f)
            cubicTo(w * 0.3f, h * 0.82f, w * 0.12f, h * 0.6f, w * 0.12f, h * 0.2f)
            close()
        }
        val brush = if (active) brandBrush else Brush.linearGradient(listOf(Color.Gray, Color.Gray))
        drawPath(p, brush, style = Stroke(width = w * 0.1f))
        if (active) drawCircle(brush, radius = w * 0.1f, center = Offset(w * 0.5f, h * 0.47f))
    }
}

/** Fire glyph: a gradient flame (own drawing; no third-party icon). */
@Composable
fun FireIcon(modifier: Modifier = Modifier.size(22.dp)) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val p = Path().apply {
            moveTo(w * 0.5f, h * 0.04f)
            cubicTo(w * 0.62f, h * 0.3f, w * 0.88f, h * 0.42f, w * 0.86f, h * 0.66f)
            cubicTo(w * 0.84f, h * 0.88f, w * 0.68f, h * 0.97f, w * 0.5f, h * 0.97f)
            cubicTo(w * 0.32f, h * 0.97f, w * 0.15f, h * 0.86f, w * 0.15f, h * 0.64f)
            cubicTo(w * 0.15f, h * 0.48f, w * 0.27f, h * 0.38f, w * 0.33f, h * 0.3f)
            cubicTo(w * 0.36f, h * 0.42f, w * 0.42f, h * 0.48f, w * 0.47f, h * 0.5f)
            cubicTo(w * 0.42f, h * 0.32f, w * 0.44f, h * 0.18f, w * 0.5f, h * 0.04f)
            close()
        }
        drawPath(p, Brush.verticalGradient(listOf(NeonMagenta, NeonViolet, NeonCyan)))
    }
}

@Composable
private fun Title(text: String) = Text(
    text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
    color = NeonCyan, modifier = Modifier.padding(bottom = 8.dp),
)

@Composable
private fun ToggleRow(label: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan, checkedTrackColor = NeonMagenta.copy(alpha = 0.5f)),
        )
    }
}

/** Privacy Dashboard for the current page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyDashboardSheet(
    host: String?,
    stats: PageStats?,
    sessionBlocked: Int,
    settings: PrivacySettings,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val blocking by settings.blockTrackers.collectAsState()
    val allow by settings.allowlist.collectAsState()
    val fireproof by settings.fireproof.collectAsState()
    val siteAllowed = host != null && allow.any { HostUtil.matchesSuffix(host, it) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PrivacyShieldIcon(active = blocking && !siteAllowed, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Privacy protections", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = NeonCyan)
                    Text(host ?: "No page", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(12.dp))
            val s = stats ?: PageStats()
            val trackerLine = when {
                s.observedCount == 0 -> "No known 3rd-party trackers seen on this page."
                blocking && !siteAllowed -> "Blocked ${s.blockedCount} tracker request(s) from ${s.companies().size} domain(s)."
                else -> "Saw ${s.observedCount} tracker request(s) — NOT blocked (" +
                    (if (!blocking) "protection off" else "site exception") + ")."
            }
            Text(trackerLine, style = MaterialTheme.typography.bodyMedium)
            s.byCategory().forEach { (cat, n) ->
                Text("• ${cat.label}: $n", style = MaterialTheme.typography.bodySmall)
            }
            if (s.companies().isNotEmpty()) {
                Text(
                    s.companies().take(12).joinToString(", "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("HTTPS upgrades: ${s.httpsUpgrades}   Tracking params stripped: ${s.paramsStripped}", style = MaterialTheme.typography.bodySmall)
            Text("Cookie pop-ups handled: ${s.cookiePopupsHandled}   Blocked this session: $sessionBlocked", style = MaterialTheme.typography.bodySmall)
            Text(
                "Blocked requests also appear in Proxy history (marked blocked).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            if (host != null) {
                ToggleRow(
                    "Protections on this site",
                    if (siteAllowed) "Exception: trackers load here" else "Tracker blocking applies here (when enabled)",
                    !siteAllowed,
                ) { on -> settings.setAllowed(host, !on) }
                val fp = FireproofLogic.isFireproof(host, fireproof)
                ToggleRow("Fireproof this site", "Keep its cookies and storage when you use Fire", fp) { on ->
                    if (on) settings.addFireproof(host) else settings.removeFireproof(HostUtil.normalizeHost(host) ?: host)
                }
            }
            Spacer(Modifier.height(8.dp))
            NeonButton("Privacy protections settings", onClick = onOpenSettings)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Fire confirm sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FireSheet(host: String?, fireproofCount: Int, onFire: (FireOptions) -> Unit, onDismiss: () -> Unit) {
    var keepHistory by remember { mutableStateOf(false) }
    var keepCookies by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FireIcon(Modifier.size(28.dp)); Spacer(Modifier.width(10.dp)); Title("Fire")
            }
            Text(
                "Burns cookies, site storage, cache, history and site permissions. " +
                    "Bookmarks, downloads, proxy history/HAR and fireproofed sites ($fireproofCount) are kept.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            @Composable
            fun check(label: String, v: Boolean, set: (Boolean) -> Unit) = Row(
                Modifier.fillMaxWidth().clickable { set(!v) }, verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(v, set, colors = CheckboxDefaults.colors(checkedColor = NeonMagenta))
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
            check("Keep history", keepHistory) { keepHistory = it }
            check("Keep cookies & site data", keepCookies) { keepCookies = it }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (host != null) {
                    TextButton(onClick = { onFire(FireOptions(FireScope.THIS_TAB, keepHistory, keepCookies)) }) {
                        Text("Burn this tab ($host)", color = NeonCyan, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            NeonButton("Close all tabs & burn everywhere", onClick = {
                onFire(FireOptions(FireScope.EVERYWHERE, keepHistory, keepCookies))
            })
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** All toggles + Fireproof manager. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsSheet(settings: PrivacySettings, onDismiss: () -> Unit) {
    val block by settings.blockTrackers.collectAsState()
    val https by settings.forceHttps.collectAsState()
    val gpc by settings.gpc.collectAsState()
    val strip by settings.stripParams.collectAsState()
    val ref by settings.trimReferrer.collectAsState()
    val pops by settings.cookiePopups.collectAsState()
    val exit by settings.clearOnExit.collectAsState()
    val fireproof by settings.fireproof.collectAsState()
    val allow by settings.allowlist.collectAsState()
    var newHost by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Title("Privacy protections")
            Text(
                "All off by default — for recon, trackers, raw URLs and referrers are often the signal.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ToggleRow("Tracker & ad loading protection", "Block known 3rd-party tracker/ad hosts (still logged)", block, settings::setBlockTrackers)
            ToggleRow("Force HTTPS", "Upgrade http→https; skips IPs, localhost, LAN names, custom ports", https, settings::setForceHttps)
            ToggleRow("Global Privacy Control", "Send Sec-GPC: 1 and set navigator.globalPrivacyControl", gpc, settings::setGpc)
            ToggleRow("Strip link tracking", "Remove utm_*, fbclid, gclid, mc_eid… from navigations", strip, settings::setStripParams)
            ToggleRow("Trim referrer", "Cross-site navigations send origin only; none on https→http", ref, settings::setTrimReferrer)
            ToggleRow("Cookie pop-up protection (lite)", "Best-effort: click reject / hide consent banners", pops, settings::setCookiePopups)
            ToggleRow("Clear on exit", "Run Fire (everywhere) when the app goes to background or closes", exit, settings::setClearOnExit)
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Title("Fireproof sites")
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newHost, onValueChange = { newHost = it }, singleLine = true, modifier = Modifier.weight(1f),
                    label = { Text("example.com") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { settings.addFireproof(newHost); newHost = "" }),
                )
                TextButton(onClick = { settings.addFireproof(newHost); newHost = "" }) { Text("Add", color = NeonCyan) }
            }
            if (fireproof.isEmpty()) Text("None yet.", style = MaterialTheme.typography.bodySmall)
            fireproof.sorted().forEach { h ->
                HostRow(h) { settings.removeFireproof(h) }
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Title("Tracker blocking exceptions")
            if (allow.isEmpty()) Text("None. Add one from the shield on any page.", style = MaterialTheme.typography.bodySmall)
            allow.sorted().forEach { h -> HostRow(h) { settings.setAllowed(h, false) } }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HostRow(host: String, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).let { it }) { Canvas(Modifier.size(8.dp)) { drawCircle(brandBrush) } }
        Spacer(Modifier.width(8.dp))
        Text(host, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onRemove) { Text("Remove", color = NeonMagenta) }
    }
}
