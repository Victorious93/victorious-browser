package com.victorious.browser

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.victorious.browser.bridge.BridgeCommandBus
import com.victorious.browser.bridge.BridgeDispatcher
import com.victorious.browser.bridge.FleetControlScreen
import com.victorious.browser.browser.BrowserScreen
import com.victorious.browser.browser.TabStore
import com.victorious.browser.browser.TabbedBrowserScreen
import com.victorious.browser.inspect.NetworkHistoryScreen
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
import com.victorious.browser.msf.MsfConsoleScreen
import com.victorious.browser.sandbox.SandboxesScreen
import com.victorious.browser.ui.theme.VictoriousBrowserTheme
import com.victorious.browser.ui.theme.enableAlwaysDarkEdgeToEdge
import com.victorious.browser.userscript.UserScriptsScreen
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    private val pendingOpenUrl = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeOpenUrl(intent)
        enableAlwaysDarkEdgeToEdge()
        setContent {
            VictoriousBrowserTheme {
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
    data object Browser : AppDestination()
    data class SandboxBrowser(val url: String, val sandbox: String) : AppDestination()
    data object Network : AppDestination()
    data object FleetControl : AppDestination()
    data object Recon : AppDestination()
    data object Scripts : AppDestination()
    data object Sandboxes : AppDestination()
    data object Osint : AppDestination()
    data object Scanner : AppDestination()
    data object NetId : AppDestination()
    data object Fuzz : AppDestination()
    data object Export : AppDestination()
    data object Msf : AppDestination()
    data object Certs : AppDestination()
    data object Evidence : AppDestination()
}

@Composable
fun VictoriousBrowserApp(
    pendingOpenUrl: String? = null,
    onPendingConsumed: () -> Unit = {},
) {
    var destination by remember { mutableStateOf<AppDestination>(AppDestination.Launcher) }
    val tabStore = remember { TabStore() }

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
        when (val dest = destination) {
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
            )
            AppDestination.Browser -> TabbedBrowserScreen(
                tabStore = tabStore,
                modifier = pad,
                onOpenNetwork = { destination = AppDestination.Network },
                onOpenScripts = { destination = AppDestination.Scripts },
                onBackToLauncher = { destination = AppDestination.Launcher },
            )
            AppDestination.Network -> NetworkHistoryScreen(
                modifier = pad,
                onBack = { destination = AppDestination.Launcher },
                onSendToFleet = { destination = AppDestination.Export },
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
            AppDestination.Scripts -> UserScriptsScreen(
                modifier = pad,
                onBack = { destination = AppDestination.Browser },
            )
            is AppDestination.SandboxBrowser -> BrowserScreen(
                modifier = pad,
                initialUrl = dest.url,
                sandboxName = dest.sandbox,
                onOpenNetwork = { destination = AppDestination.Network },
                onOpenScripts = { destination = AppDestination.Scripts },
                onBackToLauncher = { destination = AppDestination.Launcher },
            )
            AppDestination.Sandboxes -> SandboxesScreen(
                modifier = pad,
                onBack = { destination = AppDestination.Launcher },
                onOpenSandbox = { name ->
                    destination = AppDestination.SandboxBrowser(url = "https://example.com", sandbox = name)
                },
            )
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
