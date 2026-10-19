package com.victorious.browser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.victorious.browser.browser.BrowserScreen
import com.victorious.browser.inspect.NetworkHistoryScreen
import com.victorious.browser.panels.FleetPanelScreen
import com.victorious.browser.ui.theme.VictoriousBrowserTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VictoriousBrowserTheme {
                VictoriousBrowserApp()
            }
        }
    }
}

private sealed class AppDestination {
    data object Launcher : AppDestination()
    data class Browser(val url: String) : AppDestination()
    data object Network : AppDestination()
}

@Composable
fun VictoriousBrowserApp() {
    // Fleet Panels is the start screen; WebView opens when a panel is tapped.
    var destination by remember { mutableStateOf<AppDestination>(AppDestination.Launcher) }

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
            )
            is AppDestination.Browser -> BrowserScreen(
                modifier = pad,
                initialUrl = dest.url,
                onOpenNetwork = { destination = AppDestination.Network },
                onBackToLauncher = { destination = AppDestination.Launcher },
            )
            AppDestination.Network -> NetworkHistoryScreen(
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
