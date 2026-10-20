package com.victorious.browser.netid

import com.victorious.browser.ui.components.NeonToolHeader
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.victorious.browser.inspect.LocalProxyService
import com.victorious.browser.ui.theme.NeonCyan
import com.victorious.browser.ui.theme.NeonMagenta

@Composable
fun NetIdScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settings = remember { NetIdSettings.get(context) }
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf("Upstream", "Location", "UA / FP", "MAC")

    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        NeonToolHeader(title = "Net ID / Spoof", onBack = onBack)
        Text(
            "All features OFF by default. Changes apply to new WebView tabs / after " +
                "restarting the local inspection proxy for upstream chaining.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
            tabs.forEachIndexed { i, title ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
            }
        }
        Spacer(Modifier.height(8.dp))
        when (tab) {
            0 -> UpstreamTab(settings)
            1 -> GeoTab(settings)
            2 -> FingerprintTab(settings)
            3 -> MacTab()
        }
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp)) { content() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UpstreamTab(settings: NetIdSettings) {
    val current by settings.upstream.collectAsState()
    val proxyRunning by LocalProxyService.isRunning.collectAsState()
    var enabled by remember(current) { mutableStateOf(current.enabled) }
    var type by remember(current) { mutableStateOf(current.type) }
    var host by remember(current) { mutableStateOf(current.host) }
    var port by remember(current) { mutableStateOf(if (current.port > 0) current.port.toString() else "") }
    var user by remember(current) { mutableStateOf(current.username) }
    var pass by remember(current) { mutableStateOf(current.password) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        SectionCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Upstream proxy chaining", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
                    Text(
                        "When on, the local inspection proxy (127.0.0.1) forwards through this upstream.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            Spacer(Modifier.height(8.dp))
            Text("Type", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                UpstreamProxyConfig.Type.entries.forEach { t ->
                    FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) })
                }
            }
            if (type == UpstreamProxyConfig.Type.HTTPS) {
                Text(UpstreamChain.HTTPS_LIMITATION, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("Host") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("Port") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = user, onValueChange = { user = it }, label = { Text("Username (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text("Password (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(
                "Local proxy: " + if (proxyRunning) "running on 127.0.0.1:${LocalProxyService.port}" else "stopped",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Upstream: " + if (current.enabled) current.summary() else "off",
                style = MaterialTheme.typography.bodySmall,
                color = NeonCyan,
            )
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                when (val r = UpstreamProxyValidator.validate(enabled, type, host, port, user, pass)) {
                    is UpstreamProxyValidator.Result.Error -> message = r.message
                    is UpstreamProxyValidator.Result.Ok -> {
                        if (r.config.enabled && UpstreamProxyValidator.isSelfLoop(r.config, LocalProxyService.port)) {
                            message = "Cannot chain to the local inspection proxy itself"
                            return@OutlinedButton
                        }
                        settings.setUpstream(r.config)
                        UpstreamChain.set(r.config)
                        message = if (r.config.enabled) {
                            "Saved. Restart the local inspection proxy (Network screen) if it is already running."
                        } else "Upstream chaining off."
                    }
                }
            }) { Text("Save") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GeoTab(settings: NetIdSettings) {
    val current by settings.geo.collectAsState()
    var enabled by remember(current) { mutableStateOf(current.enabled) }
    var lat by remember(current) { mutableStateOf(current.latitude.toString()) }
    var lng by remember(current) { mutableStateOf(current.longitude.toString()) }
    var acc by remember(current) { mutableStateOf(current.accuracyMeters.toString()) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        SectionCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Geolocation spoof", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
                    Text(
                        "Overrides navigator.geolocation for pages in this app's WebView. Off = real device location passthrough (OS permission still required).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            Spacer(Modifier.height(8.dp))
            Text("Presets", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                GeoSpoof.presets.forEach { p ->
                    FilterChip(
                        selected = false,
                        onClick = { lat = p.lat.toString(); lng = p.lng.toString() },
                        label = { Text(p.name, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
            OutlinedTextField(value = lat, onValueChange = { lat = it }, label = { Text("Latitude") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = lng, onValueChange = { lng = it }, label = { Text("Longitude") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = acc, onValueChange = { acc = it }, label = { Text("Accuracy (meters)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                when (val r = GeoSpoof.validate(enabled, lat, lng, acc)) {
                    is GeoSpoof.Result.Error -> message = r.message
                    is GeoSpoof.Result.Ok -> {
                        settings.setGeo(r.config)
                        message = if (r.config.enabled) "Saved. Open a new tab (or reload) for the shim to apply." else "Geolocation spoof off."
                    }
                }
            }) { Text("Save") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FingerprintTab(settings: NetIdSettings) {
    val current by settings.fingerprint.collectAsState()
    var cfg by remember(current) { mutableStateOf(current) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        SectionCard {
            Text("User-Agent", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                UaPreset.entries.forEach { p ->
                    FilterChip(
                        selected = cfg.uaPreset == p,
                        onClick = { cfg = cfg.copy(uaPreset = p) },
                        label = { Text(p.label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
            if (cfg.uaPreset == UaPreset.CUSTOM) {
                OutlinedTextField(
                    value = cfg.customUa,
                    onValueChange = { cfg = cfg.copy(customUa = it) },
                    label = { Text("Custom UA") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Fingerprint surfaces (JS shim)", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
            ToggleRow("navigator.platform", cfg.spoofPlatform) { cfg = cfg.copy(spoofPlatform = it) }
            if (cfg.spoofPlatform) {
                OutlinedTextField(value = cfg.platform, onValueChange = { cfg = cfg.copy(platform = it) }, label = { Text("platform") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            ToggleRow("languages", cfg.spoofLanguages) { cfg = cfg.copy(spoofLanguages = it) }
            if (cfg.spoofLanguages) {
                OutlinedTextField(value = cfg.languages, onValueChange = { cfg = cfg.copy(languages = it) }, label = { Text("en-US,en") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            ToggleRow("hardwareConcurrency", cfg.spoofHardwareConcurrency) { cfg = cfg.copy(spoofHardwareConcurrency = it) }
            if (cfg.spoofHardwareConcurrency) {
                OutlinedTextField(
                    value = cfg.hardwareConcurrency.toString(),
                    onValueChange = { v -> v.toIntOrNull()?.let { cfg = cfg.copy(hardwareConcurrency = it) } },
                    label = { Text("cores") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
            ToggleRow("screen size", cfg.spoofScreen) { cfg = cfg.copy(spoofScreen = it) }
            if (cfg.spoofScreen) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = cfg.screenWidth.toString(),
                        onValueChange = { v -> v.toIntOrNull()?.let { cfg = cfg.copy(screenWidth = it) } },
                        label = { Text("W") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = cfg.screenHeight.toString(),
                        onValueChange = { v -> v.toIntOrNull()?.let { cfg = cfg.copy(screenHeight = it) } },
                        label = { Text("H") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
            }
            ToggleRow("timezone offset", cfg.spoofTimezone) { cfg = cfg.copy(spoofTimezone = it) }
            if (cfg.spoofTimezone) {
                OutlinedTextField(
                    value = cfg.timezoneOffsetMinutes.toString(),
                    onValueChange = { v -> v.toIntOrNull()?.let { cfg = cfg.copy(timezoneOffsetMinutes = it) } },
                    label = { Text("getTimezoneOffset() minutes") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
            ToggleRow("WebGL vendor/renderer", cfg.spoofWebGl) { cfg = cfg.copy(spoofWebGl = it) }
            if (cfg.spoofWebGl) {
                OutlinedTextField(value = cfg.webGlVendor, onValueChange = { cfg = cfg.copy(webGlVendor = it) }, label = { Text("vendor") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = cfg.webGlRenderer, onValueChange = { cfg = cfg.copy(webGlRenderer = it) }, label = { Text("renderer") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                if (cfg.uaPreset == UaPreset.CUSTOM && cfg.customUa.isBlank()) {
                    message = "Custom UA is empty"
                    return@OutlinedButton
                }
                if (cfg.spoofTimezone && !FingerprintShim.isValidTimezoneOffset(cfg.timezoneOffsetMinutes)) {
                    message = "Timezone offset must be between -840 and 720"
                    return@OutlinedButton
                }
                settings.setFingerprint(cfg)
                message = "Saved. Open a new tab for UA / shim to apply."
            }) { Text("Save") }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun MacTab() {
    val context = LocalContext.current
    val info = remember { MacStatus.read(context) }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        SectionCard {
            Text("MAC address", style = MaterialTheme.typography.titleSmall, color = NeonCyan)
            Spacer(Modifier.height(8.dp))
            Text(MacStatus.LIMITATION, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text("Status (read-only)", style = MaterialTheme.typography.labelMedium, color = NeonCyan)
            Text("Wi-Fi connected: ${info.wifiConnected}")
            Text("SSID: ${info.ssid ?: "(unknown / not connected)"}")
            Text(info.randomizedMacStatus, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "[Certain] No non-privileged Android API exists for apps to set the Wi-Fi MAC. " +
                    "Privileged / system / carrier apps and OEM settings UIs own that surface.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
