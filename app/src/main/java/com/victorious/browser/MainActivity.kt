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

private enum class AppDestination { Browser, Network }

@Composable
fun VictoriousBrowserApp() {
    var destination by remember { mutableStateOf(AppDestination.Browser) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        when (destination) {
            AppDestination.Browser -> BrowserScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                onOpenNetwork = { destination = AppDestination.Network },
            )
            AppDestination.Network -> NetworkHistoryScreen(
                onBack = { destination = AppDestination.Browser },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
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
