package com.victorious.browser.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.victorious.browser.ui.components.BrandRing
import com.victorious.browser.ui.components.GradientTitle
import com.victorious.browser.ui.components.NeonButton
import com.victorious.browser.ui.components.NeonSectionHeader
import com.victorious.browser.ui.components.QuickAction
import com.victorious.browser.ui.theme.NeonCyan
import com.victorious.browser.ui.theme.NeonMagenta
import com.victorious.browser.ui.theme.NeonViolet

/**
 * Branded home / new-tab surface: a glowing brand ring hero, a quick-open bar
 * that launches a URL or search into the browser, and quick actions into the
 * fleet panels and core tools.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onOpenUrl: (String) -> Unit,
    onOpenPanels: () -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenRecon: () -> Unit,
    onOpenOsint: () -> Unit,
    onOpenScanner: () -> Unit,
) {
    val focus = LocalFocusManager.current
    var query by remember { mutableStateOf("") }

    fun submit() {
        val q = query.trim()
        if (q.isEmpty()) return
        val target = when {
            q.startsWith("http://") || q.startsWith("https://") -> q
            q.contains('.') && !q.contains(' ') -> "https://$q"
            else -> "https://duckduckgo.com/?q=" + java.net.URLEncoder.encode(q, "UTF-8")
        }
        focus.clearFocus()
        onOpenUrl(target)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        BrandRing(size = 112.dp, strokeWidth = 7.dp)
        Spacer(Modifier.height(16.dp))
        GradientTitle("Victorious", style = MaterialTheme.typography.headlineLarge)
        Text(
            "The fleet security & recon browser",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search or enter address") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = NeonCyan) },
            shape = MaterialTheme.shapes.large,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonCyan,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = NeonMagenta,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit() }),
        )
        Spacer(Modifier.height(12.dp))
        NeonButton("Go", onClick = { submit() }, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(28.dp))
        NeonSectionHeader("Quick actions", modifier = Modifier.align(Alignment.Start))
        Spacer(Modifier.height(10.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickAction("Fleet panels", Icons.Filled.Home, NeonMagenta, onOpenPanels)
            QuickAction("Browser", Icons.Filled.Home, NeonCyan, onOpenBrowser)
            QuickAction("Recon", Icons.Filled.Search, NeonCyan, onOpenRecon)
            QuickAction("OSINT", Icons.Filled.Place, NeonViolet, onOpenOsint)
            QuickAction("Scanner", Icons.Filled.Warning, NeonMagenta, onOpenScanner)
        }
        Spacer(Modifier.height(24.dp))
    }
}
