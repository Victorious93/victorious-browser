package com.victorious.browser

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.victorious.browser.browser.TabStore
import com.victorious.browser.browser.TabbedBrowserScreen
import com.victorious.browser.inspect.NetworkHistoryScreen
import com.victorious.browser.panels.FleetPanelScreen
import com.victorious.browser.recon.ReconWorkbenchScreen
import com.victorious.browser.ui.theme.VictoriousBrowserTheme
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    private val pendingOpenUrl = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeOpenUrl(intent)
        enableEdgeToEdge()
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
    data object Network : AppDestination()
    data object FleetControl : AppDestination()
    data object Recon : AppDestination()
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
        when (destination) {
            AppDestination.Launcher -> FleetPanelScreen(
                modifier = pad,
                onOpenPanel = { panel -> openUrl(panel.url) },
                onOpenNetwork = { destination = AppDestination.Network },
                onOpenBrowser = { destination = AppDestination.Browser },
                onOpenFleetControl = { destination = AppDestination.FleetControl },
                onOpenRecon = { destination = AppDestination.Recon },
            )
            AppDestination.Browser -> TabbedBrowserScreen(
                tabStore = tabStore,
                modifier = pad,
                onOpenNetwork = { destination = AppDestination.Network },
                onBackToLauncher = { destination = AppDestination.Launcher },
            )
            AppDestination.Network -> NetworkHistoryScreen(
                modifier = pad,
                onBack = { destination = AppDestination.Launcher },
            )
            AppDestination.FleetControl -> FleetControlScreen(
                modifier = pad,
                onBack = { destination = AppDestination.Launcher },
            )
            AppDestination.Recon -> ReconWorkbenchScreen(
                modifier = pad,
                onBack = { destination = AppDestination.Launcher },
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
