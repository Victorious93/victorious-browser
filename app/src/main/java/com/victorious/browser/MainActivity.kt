package com.victorious.browser

import android.content.Intent
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
import com.victorious.browser.headscale.HeadscaleScreen
import com.victorious.browser.inspect.NetworkHistoryScreen
import com.victorious.browser.help.HelpScreen
import com.victorious.browser.home.HomeScreen
import com.victorious.browser.panels.FleetPanelScreen
import com.victorious.browser.osint.OsintScreen
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeOpenUrl(intent)
        val themeSettings = ThemeSettings.get(this)
        setContent {
            val useLightTheme by themeSettings.useLightTheme.collectAsState()
            LaunchedEffect(useLightTheme) { enableThemedEdgeToEdge(darkTheme = !useLightTheme) }
            VictoriousBrowserTheme(darkTheme = !useLightTheme) {
                VictoriousBrowserApp(
                    pendingOpenUrl = pendingOpenUrl.value,
                    onPendingConsumed = { pendingOpenUrl.value = null },
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
        val url = intent?.getStringExtra(BridgeDispatcher.EXTRA_OPEN_URL) ?: return
        pendingOpenUrl.value = url
        intent.removeExtra(BridgeDispatcher.EXTRA_OPEN_URL)
    }
}

private sealed class AppDestination {
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
) {
    val context = LocalContext.current
    var destination by remember { mutableStateOf<AppDestination>(AppDestination.Launcher) }
    val tabStore = remember {
        TabStore().also { TabSessionStore.get(context).restoreInto(it) }
    }
    // Tools opened from the browser's ⋮ menu return to the browser, not the launcher.
    var returnToBrowser by remember { mutableStateOf(false) }
    var replaySeed by remember { mutableStateOf<CapturedExchange?>(null) }
    var startRepeater by remember { mutableStateOf(false) }
    fun backFromTool() {
        destination = if (returnToBrowser) AppDestination.Browser else AppDestination.Launcher
        returnToBrowser = false
        replaySeed = null
        startRepeater = false
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
        val keepBrowser =
            dest == AppDestination.Browser || (returnToBrowser && dest != AppDestination.Launcher)
        Box(Modifier.fillMaxSize()) {
        if (keepBrowser) {
            TabbedBrowserScreen(
                tabStore = tabStore,
                modifier = pad,
                onOpenNetwork = { returnToBrowser = true; destination = AppDestination.Network },
                onOpenScripts = { returnToBrowser = true; destination = AppDestination.Scripts },
                onOpenExtensions = { returnToBrowser = true; destination = AppDestination.Extensions },
                onBackToLauncher = { destination = AppDestination.Launcher },
                onOpenAntiPhish = { returnToBrowser = true; destination = AppDestination.AntiPhish },
                onSendToRepeater = { seed ->
                    replaySeed = seed
                    startRepeater = true
                    returnToBrowser = true
                    destination = AppDestination.Network
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
                onOpenNetwork = { destination = AppDestination.Network },
                onOpenBrowser = { destination = AppDestination.Browser },
                onOpenFleetControl = { destination = AppDestination.FleetControl },
                onOpenRecon = { destination = AppDestination.Recon },
                onOpenSandboxes = { destination = AppDestination.Sandboxes },
                onOpenOsint = { destination = AppDestination.Osint },
                onOpenScanner = { destination = AppDestination.Scanner },
                onOpenNetId = { destination = AppDestination.NetId },
                onOpenFuzz = { destination = AppDestination.Fuzz },
                onOpenExport = { destination = AppDestination.Export },
                onOpenMsf = { destination = AppDestination.Msf },
                onOpenCerts = { destination = AppDestination.Certs },
                onOpenEvidence = { destination = AppDestination.Evidence },
                onOpenAntiPhish = { destination = AppDestination.AntiPhish },
                onOpenHome = { destination = AppDestination.Home },
                onOpenHelp = { destination = AppDestination.Help },
                onOpenExtensions = { destination = AppDestination.Extensions },
                onOpenWireGuard = { destination = AppDestination.WireGuard },
                onOpenHeadscale = { destination = AppDestination.Headscale },
            )
            AppDestination.Home -> HomeScreen(
                modifier = pad,
                onOpenUrl = { url -> openUrl(url) },
                onOpenPanels = { destination = AppDestination.Launcher },
                onOpenBrowser = { destination = AppDestination.Browser },
                onOpenRecon = { destination = AppDestination.Recon },
                onOpenOsint = { destination = AppDestination.Osint },
                onOpenScanner = { destination = AppDestination.Scanner },
            )
            AppDestination.Network -> NetworkHistoryScreen(
                modifier = pad,
                onBack = { backFromTool() },
                onSendToFleet = { destination = AppDestination.Export },
                initialReplay = replaySeed,
                startInRepeater = startRepeater,
            )
            AppDestination.FleetControl -> FleetControlScreen(
                modifier = pad,
                onBack = { destination = AppDestination.Launcher },
            )
            AppDestination.Recon -> ReconWorkbenchScreen(
                modifier = pad,
                onBack = { destination = AppDestination.Launcher },
                onOpenOsint = { destination = AppDestination.Osint },
            )
            AppDestination.Osint -> OsintScreen(
                modifier = pad,
                onBack = { destination = AppDestination.Launcher },
                onOpenUrl = { url -> openUrl(url) },
            )
            AppDestination.Scanner -> ScannerScreen(
                modifier = pad,
                onBack = { destination = AppDestination.Launcher },
                onSendToFleet = { findings ->
                    FleetExportStaging.stage(findings = findings)
                    destination = AppDestination.Export
                },
            )
            AppDestination.NetId -> NetIdScreen(
                modifier = pad,
                onBack = { destination = AppDestination.Launcher },
            )
            AppDestination.Fuzz -> FuzzScreen(
                modifier = pad,
                onBack = { destination = AppDestination.Launcher },
            )
            AppDestination.Export -> ExportScreen(
                modifier = pad,
                onBack = { destination = AppDestination.Launcher },
            )
            AppDestination.Msf -> MsfConsoleScreen(
                modifier = pad,
                onBack = { destination = AppDestination.Launcher },
            )
            AppDestination.Certs -> CertScreen(
                modifier = pad,
                onBack = { destination = AppDestination.Launcher },
            )
            AppDestination.Evidence -> EvidenceScreen(
                modifier = pad,
                onBack = { destination = AppDestination.Launcher },
            )
            AppDestination.AntiPhish -> AntiPhishScreen(
                modifier = pad,
                onBack = { backFromTool() },
            )
            AppDestination.Help -> HelpScreen(
                modifier = pad,
                onBack = { destination = AppDestination.Launcher },
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
                onOpenNetwork = { destination = AppDestination.Network },
                onOpenScripts = { destination = AppDestination.Scripts },
                onBackToLauncher = { destination = AppDestination.Launcher },
                onOpenAntiPhish = { destination = AppDestination.AntiPhish },
            )
            AppDestination.Sandboxes -> SandboxesScreen(
                modifier = pad,
                onBack = { destination = AppDestination.Launcher },
                onOpenSandbox = { name ->
                    destination = AppDestination.SandboxBrowser(url = "https://example.com", sandbox = name)
                },
            )
            AppDestination.WireGuard -> WireGuardScreen(
                modifier = pad,
                onBack = { destination = AppDestination.Launcher },
            )
            AppDestination.Headscale -> HeadscaleScreen(
                modifier = pad,
                onBack = { destination = AppDestination.Launcher },
            )
        }
        }
        }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VictoriousBrowserAppPreview() {
    VictoriousBrowserTheme {
        VictoriousBrowserApp()
    }
}
