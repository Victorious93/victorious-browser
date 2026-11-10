package com.victorious.browser

import android.content.Intent
import android.widget.Toast
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.victorious.browser.bridge.BridgeCommandBus
import com.victorious.browser.bridge.BridgeDispatcher
import com.victorious.browser.bridge.FleetControlScreen
import com.victorious.browser.browser.BrowserScreen
import com.victorious.browser.browser.TabSessionStore
import com.victorious.browser.browser.TabStore
import com.victorious.browser.browser.TabbedBrowserScreen
import com.victorious.browser.inspect.CapturedExchange
import com.victorious.browser.linkembed.SharedLinks
import com.victorious.browser.headscale.HeadscaleScreen
import com.victorious.browser.inspect.NetworkHistoryScreen
import com.victorious.browser.help.HelpScreen
import com.victorious.browser.home.HomeScreen
import com.victorious.browser.panels.FleetPanelScreen
import com.victorious.browser.osint.OsintScreen
import com.victorious.browser.osint.research.DeepResearchScreen
import com.victorious.browser.recon.ReconWorkbenchScreen
import com.victorious.browser.scanner.ScannerScreen
import com.victorious.browser.netid.NetIdScreen
import com.victorious.browser.fuzz.FuzzScreen
import com.victorious.browser.export.ExportScreen
import com.victorious.browser.export.FleetExportStaging
import com.victorious.browser.certmgr.CertScreen
import com.victorious.browser.evidence.EvidenceScreen
import com.victorious.browser.antiphish.AntiPhishScreen
import com.victorious.browser.msf.MsfConsoleScreen
import com.victorious.browser.sandbox.SandboxesScreen
import com.victorious.browser.ui.theme.ThemeSettings
import com.victorious.browser.ui.theme.VictoriousBrowserTheme
import com.victorious.browser.ui.theme.enableThemedEdgeToEdge
import com.victorious.browser.userscript.UserScriptsScreen
import com.victorious.browser.webext.ExtensionsScreen
import com.victorious.browser.wireguard.WireGuardScreen
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    private val pendingOpenUrl = mutableStateOf<String?>(null)
    private val pendingPreviewUrls = mutableStateOf<List<String>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A recreated activity (savedInstanceState != null) re-delivers the original intent;
        // don't re-open a shared/VIEW link that was already handled.
        if (savedInstanceState != null) intent?.putExtra(EXTRA_SHARE_HANDLED, true)
        consumeOpenUrl(intent)
        val themeSettings = ThemeSettings.get(this)
        setContent {
            val useLightTheme by themeSettings.useLightTheme.collectAsState()
            LaunchedEffect(useLightTheme) { enableThemedEdgeToEdge(darkTheme = !useLightTheme) }
            VictoriousBrowserTheme(darkTheme = !useLightTheme) {
                VictoriousBrowserApp(
                    pendingOpenUrl = pendingOpenUrl.value,
                    onPendingConsumed = { pendingOpenUrl.value = null },
                    pendingPreviewUrls = pendingPreviewUrls.value,
                    onPreviewConsumed = { pendingPreviewUrls.value = null },
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Opt-in "Clear on exit": Fire everywhere (fireproofed sites kept) when backgrounded/closed.
        if (!isChangingConfigurations) {
            val privacy = com.victorious.browser.browser.privacy.PrivacySettings.get(this)
            if (privacy.clearOnExit.value) {
                com.victorious.browser.browser.privacy.FireBurner.burn(
                    context = this,
                    opts = com.victorious.browser.browser.privacy.FireOptions(),
                    fireproof = privacy.fireproof.value,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeOpenUrl(intent)
    }

    private fun consumeOpenUrl(intent: Intent?) {
        if (intent == null) return
        consumeSharedLink(intent)
        val url = intent.getStringExtra(BridgeDispatcher.EXTRA_OPEN_URL) ?: return
        pendingOpenUrl.value = url
        intent.removeExtra(BridgeDispatcher.EXTRA_OPEN_URL)
    }

    /**
     * Share-in: ACTION_VIEW http(s) opens a new tab; ACTION_SEND text/plain opens the
     * link preview sheet for the first http(s) URL in the shared text. Anything that
     * is not a safe http(s) URL is ignored. Each intent is handled once.
     */
    private fun consumeSharedLink(intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_SHARE_HANDLED, false)) return
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val url = SharedLinks.fromView(intent.dataString) ?: return
                intent.putExtra(EXTRA_SHARE_HANDLED, true)
                pendingOpenUrl.value = url
            }
            Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("text/") != true) return
                intent.putExtra(EXTRA_SHARE_HANDLED, true)
                val urls = SharedLinks.extractUrls(
                    intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
                    intent.getStringExtra(Intent.EXTRA_SUBJECT),
                )
                when {
                    urls.isEmpty() -> Toast.makeText(this, "No http(s) link found in the shared text", Toast.LENGTH_SHORT).show()
                    else -> pendingPreviewUrls.value = urls
                }
            }
        }
    }

    private companion object {
        const val EXTRA_SHARE_HANDLED = "com.victorious.browser.extra.SHARE_HANDLED"
    }
}

internal sealed class AppDestination {
    data object Launcher : AppDestination()
    data object Home : AppDestination()
    data object Browser : AppDestination()
    data class SandboxBrowser(val url: String, val sandbox: String) : AppDestination()
    data object Network : AppDestination()
    data object FleetControl : AppDestination()
    data object Recon : AppDestination()
    data object Scripts : AppDestination()
    data object Extensions : AppDestination()
    data object Sandboxes : AppDestination()
    data object Osint : AppDestination()
    data object Research : AppDestination()
    data object Scanner : AppDestination()
    data object NetId : AppDestination()
    data object Fuzz : AppDestination()
    data object Export : AppDestination()
    data object Msf : AppDestination()
    data object Certs : AppDestination()
    data object Evidence : AppDestination()
    data object AntiPhish : AppDestination()
    data object Help : AppDestination()
    data object WireGuard : AppDestination()
    data object Headscale : AppDestination()
}

@Composable
fun VictoriousBrowserApp(
    pendingOpenUrl: String? = null,
    onPendingConsumed: () -> Unit = {},
    pendingPreviewUrls: List<String>? = null,
    onPreviewConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    // Browser-first: the tabbed browser is the start destination. Fleet tools open as
    // overlays from inside the browser and always return to it (no separate home screen).
    var destination by remember { mutableStateOf<AppDestination>(AppDestination.Browser) }
    val tabStore = remember {
        TabStore().also { TabSessionStore.get(context).restoreInto(it) }
    }
    // Tools opened from the browser always return to the browser.
    var returnToBrowser by remember { mutableStateOf(true) }
    var replaySeed by remember { mutableStateOf<CapturedExchange?>(null) }
    var startRepeater by remember { mutableStateOf(false) }
    fun backFromTool() {
        destination = AppDestination.Browser
        returnToBrowser = true
        replaySeed = null
        startRepeater = false
    }
    fun openTool(dest: AppDestination) {
        returnToBrowser = true
        destination = dest
    }

    fun openUrl(url: String) {
        tabStore.openTab(url)
        destination = AppDestination.Browser
    }

    // Bridge-driven opens (service → UI).
    LaunchedEffect(Unit) {
        BridgeCommandBus.commands.collectLatest { cmd ->
            when (cmd) {
                is BridgeCommandBus.Command.OpenUrl -> {
                    openUrl(cmd.url)
                }
            }
        }
    }
    // Shared-in link(s) (ACTION_SEND): show the browser and hand them to its preview / picker.
    var browserPreviewUrls by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(pendingPreviewUrls) {
        val urls = pendingPreviewUrls ?: return@LaunchedEffect
        browserPreviewUrls = urls
        destination = AppDestination.Browser
        onPreviewConsumed()
    }
    LaunchedEffect(pendingOpenUrl) {
        val url = pendingOpenUrl ?: return@LaunchedEffect
        openUrl(url)
        onPendingConsumed()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        val pad = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        val dest = destination
        // Keep the browser alive under every tool overlay so tabs / WebViews survive.
        val keepBrowser = true
        Box(Modifier.fillMaxSize()) {
        if (keepBrowser) {
            TabbedBrowserScreen(
                tabStore = tabStore,
                modifier = pad,
                onOpenNetwork = { openTool(AppDestination.Network) },
                onOpenScripts = { openTool(AppDestination.Scripts) },
                onOpenExtensions = { openTool(AppDestination.Extensions) },
                onOpenTool = { id -> openTool(toolDestination(id)) },
                onOpenAntiPhish = { openTool(AppDestination.AntiPhish) },
                externalPreviewUrls = browserPreviewUrls,
                onExternalPreviewConsumed = { browserPreviewUrls = null },
                onSendToRepeater = { seed ->
                    replaySeed = seed
                    startRepeater = true
                    openTool(AppDestination.Network)
                },
            )
        }
        if (dest != AppDestination.Browser) {
        Surface(modifier = pad, color = MaterialTheme.colorScheme.background) {
        when (dest) {
            AppDestination.Browser -> { /* kept alive under overlay when returning to tools */ }
            AppDestination.Launcher -> FleetPanelScreen(
                modifier = pad,
                onOpenPanel = { panel -> openUrl(panel.url) },
                onOpenNetwork = { openTool(AppDestination.Network) },
                onOpenBrowser = { backFromTool() },
                onOpenFleetControl = { openTool(AppDestination.FleetControl) },
                onOpenRecon = { openTool(AppDestination.Recon) },
                onOpenSandboxes = { openTool(AppDestination.Sandboxes) },
                onOpenOsint = { openTool(AppDestination.Osint) },
                onOpenScanner = { openTool(AppDestination.Scanner) },
                onOpenNetId = { openTool(AppDestination.NetId) },
                onOpenFuzz = { openTool(AppDestination.Fuzz) },
                onOpenExport = { openTool(AppDestination.Export) },
                onOpenMsf = { openTool(AppDestination.Msf) },
                onOpenCerts = { openTool(AppDestination.Certs) },
                onOpenEvidence = { openTool(AppDestination.Evidence) },
                onOpenAntiPhish = { openTool(AppDestination.AntiPhish) },
                onOpenHome = { openTool(AppDestination.Home) },
                onOpenHelp = { openTool(AppDestination.Help) },
                onOpenExtensions = { openTool(AppDestination.Extensions) },
                onOpenWireGuard = { openTool(AppDestination.WireGuard) },
                onOpenHeadscale = { openTool(AppDestination.Headscale) },
                onBack = { backFromTool() },
            )
            AppDestination.Home -> HomeScreen(
                modifier = pad,
                onOpenUrl = { url -> openUrl(url) },
                onOpenPanels = { openTool(AppDestination.Launcher) },
                onOpenBrowser = { backFromTool() },
                onOpenRecon = { openTool(AppDestination.Recon) },
                onOpenOsint = { openTool(AppDestination.Osint) },
                onOpenScanner = { openTool(AppDestination.Scanner) },
            )
            AppDestination.Network -> NetworkHistoryScreen(
                modifier = pad,
                onBack = { backFromTool() },
                onSendToFleet = { openTool(AppDestination.Export) },
                initialReplay = replaySeed,
                startInRepeater = startRepeater,
            )
            AppDestination.FleetControl -> FleetControlScreen(
                modifier = pad,
                onBack = { backFromTool() },
            )
            AppDestination.Recon -> ReconWorkbenchScreen(
                modifier = pad,
                onBack = { backFromTool() },
                onOpenOsint = { openTool(AppDestination.Osint) },
            )
            AppDestination.Osint -> OsintScreen(
                modifier = pad,
                onBack = { backFromTool() },
                onOpenUrl = { url -> openUrl(url) },
            )
            AppDestination.Research -> DeepResearchScreen(
                modifier = pad,
                onBack = { backFromTool() },
                onOpenUrl = { url -> openUrl(url) },
                onOpenInBackground = { url -> tabStore.openTab(url, activate = false) },
                onOpenPrivate = { url ->
                    tabStore.openTab(url, activate = true, incognito = true)
                    destination = AppDestination.Browser
                },
                onSendToFleet = { targets ->
                    FleetExportStaging.stage(targets = targets)
                    openTool(AppDestination.Export)
                },
                onOpenScanner = { openTool(AppDestination.Scanner) },
            )
            AppDestination.Scanner -> ScannerScreen(
                modifier = pad,
                onBack = { backFromTool() },
                onSendToFleet = { findings ->
                    FleetExportStaging.stage(findings = findings)
                    openTool(AppDestination.Export)
                },
            )
            AppDestination.NetId -> NetIdScreen(
                modifier = pad,
                onBack = { backFromTool() },
            )
            AppDestination.Fuzz -> FuzzScreen(
                modifier = pad,
                onBack = { backFromTool() },
            )
            AppDestination.Export -> ExportScreen(
                modifier = pad,
                onBack = { backFromTool() },
            )
            AppDestination.Msf -> MsfConsoleScreen(
                modifier = pad,
                onBack = { backFromTool() },
            )
            AppDestination.Certs -> CertScreen(
                modifier = pad,
                onBack = { backFromTool() },
            )
            AppDestination.Evidence -> EvidenceScreen(
                modifier = pad,
                onBack = { backFromTool() },
            )
            AppDestination.AntiPhish -> AntiPhishScreen(
                modifier = pad,
                onBack = { backFromTool() },
            )
            AppDestination.Help -> HelpScreen(
                modifier = pad,
                onBack = { backFromTool() },
            )
            AppDestination.Extensions -> ExtensionsScreen(
                modifier = pad,
                onBack = { backFromTool() },
            )
            AppDestination.Scripts -> UserScriptsScreen(
                modifier = pad,
                onBack = { backFromTool() },
            )
            is AppDestination.SandboxBrowser -> BrowserScreen(
                modifier = pad,
                initialUrl = dest.url,
                sandboxName = dest.sandbox,
                onOpenNetwork = { openTool(AppDestination.Network) },
                onOpenScripts = { openTool(AppDestination.Scripts) },
                onBackToLauncher = { backFromTool() },
                onOpenAntiPhish = { openTool(AppDestination.AntiPhish) },
            )
            AppDestination.Sandboxes -> SandboxesScreen(
                modifier = pad,
                onBack = { backFromTool() },
                onOpenSandbox = { name ->
                    openTool(AppDestination.SandboxBrowser(url = "https://example.com", sandbox = name))
                },
            )
            AppDestination.WireGuard -> WireGuardScreen(
                modifier = pad,
                onBack = { backFromTool() },
            )
            AppDestination.Headscale -> HeadscaleScreen(
                modifier = pad,
                onBack = { backFromTool() },
            )
        }
        }
        }
        }
    }
}


/** Maps a Tools-sheet id to an [AppDestination]. Pure so it is unit-tested. Unknown → null. */
internal fun toolDestination(id: String): AppDestination = when (id) {
    "panels" -> AppDestination.Launcher
    "home" -> AppDestination.Home
    "network" -> AppDestination.Network
    "fleet" -> AppDestination.FleetControl
    "recon" -> AppDestination.Recon
    "scripts" -> AppDestination.Scripts
    "extensions" -> AppDestination.Extensions
    "sandboxes" -> AppDestination.Sandboxes
    "osint" -> AppDestination.Osint
    "research" -> AppDestination.Research
    "scanner" -> AppDestination.Scanner
    "netid" -> AppDestination.NetId
    "fuzz" -> AppDestination.Fuzz
    "export" -> AppDestination.Export
    "msf" -> AppDestination.Msf
    "certs" -> AppDestination.Certs
    "evidence" -> AppDestination.Evidence
    "antiphish" -> AppDestination.AntiPhish
    "help" -> AppDestination.Help
    "wireguard" -> AppDestination.WireGuard
    "headscale" -> AppDestination.Headscale
    else -> AppDestination.Browser
}

@Preview(showBackground = true)
@Composable
private fun VictoriousBrowserAppPreview() {
    VictoriousBrowserTheme {
        VictoriousBrowserApp()
    }
}
