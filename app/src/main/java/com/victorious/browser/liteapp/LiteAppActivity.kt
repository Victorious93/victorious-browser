package com.victorious.browser.liteapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.victorious.browser.browser.BrowserScreen
import com.victorious.browser.panels.PanelStore
import com.victorious.browser.ui.theme.ThemeSettings
import com.victorious.browser.ui.theme.VictoriousBrowserTheme
import com.victorious.browser.ui.theme.enableThemedEdgeToEdge

/**
 * Chrome-less host for a single panel, launched from a pinned shortcut. Runs in its
 * own task (documentLaunchMode) so it appears as a separate app in Recents.
 * Not exported: only the launcher's shortcut intents (and this app) can start it.
 */
class LiteAppActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val panelId = LiteAppLinks.parsePanelId(intent?.dataString)
        val panel = panelId?.let { PanelStore.get(this).get(it) }
        if (panel == null) {
            Toast.makeText(this, "Lite App no longer exists", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        title = panel.name
        val themeSettings = ThemeSettings.get(this)
        setContent {
            val useLightTheme by themeSettings.useLightTheme.collectAsState()
            LaunchedEffect(useLightTheme) { enableThemedEdgeToEdge(darkTheme = !useLightTheme) }
            VictoriousBrowserTheme(darkTheme = !useLightTheme) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                ) { innerPadding ->
                    BrowserScreen(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        initialUrl = panel.url,
                        liteMode = true,
                    )
                }
            }
        }
    }
}
