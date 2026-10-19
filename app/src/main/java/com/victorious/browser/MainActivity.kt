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
import com.victorious.browser.browser.BrowserScreen
import com.victorious.browser.inspect.NetworkHistoryScreen
import com.victorious.browser.panels.FleetPanelScreen
import com.victorious.browser.ui.theme.VictoriousBrowserTheme
import com.victorious.browser.userscript.UserScriptsScreen
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
    data class Browser(val url: String) : AppDestination()
    data object Network : AppDestination()
    data object FleetControl : AppDestination()
    data class Scripts(val returnUrl: String) : AppDestination()
}

@Composable
fun VictoriousBrowserApp(
    pendingOpenUrl: String? = null,
    onPendingConsumed: () -> Unit = {},
) {
    var destination by remember { mutableStateOf<AppDestination>(AppDestination.Launcher) }

    // Bridge-driven opens (service → UI).
    LaunchedEffect(Unit) {
        BridgeCommandBus.commands.collectLatest { cmd ->
            when (cmd) {
                is BridgeCommandBus.Command.OpenUrl -> {
                    destination = AppDestination.Browser(cmd.url)
                }
            }
        }
    }
    LaunchedEffect(pendingOpenUrl) {
        val url = pendingOpenUrl ?: return@LaunchedEffect
        destination = AppDestination.Browser(url)
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
                onOpenPanel = { panel -> destination = AppDestination.Browser(panel.url) },
                onOpenNetwork = { destination = AppDestination.Network },
                onOpenBrowser = { destination = AppDestination.Browser("https://example.com") },
                onOpenFleetControl = { destination = AppDestination.FleetControl },
            )
            is AppDestination.Browser -> BrowserScreen(
                modifier = pad,
                initialUrl = dest.url,
                onOpenNetwork = { destination = AppDestination.Network },
                onOpenScripts = { destination = AppDestination.Scripts(dest.url) },
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
            is AppDestination.Scripts -> UserScriptsScreen(
                modifier = pad,
                onBack = { destination = AppDestination.Browser(dest.returnUrl) },
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
