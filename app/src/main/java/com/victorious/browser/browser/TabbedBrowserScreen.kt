package com.victorious.browser.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebView
import com.victorious.browser.evidence.EvidenceStore
import com.victorious.browser.evidence.WebViewCapture
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.victorious.browser.inject.InjectionDialog
import com.victorious.browser.reader.ReaderModeController
import com.victorious.browser.urltools.UrlToolsDialog
import com.victorious.browser.userscript.UserScriptInjector
import com.victorious.browser.userscript.UserScriptStore
import com.victorious.browser.webext.ExtensionEngine
import com.victorious.browser.webext.ExtensionStore
import com.victorious.browser.netid.NetIdSettings
import com.victorious.browser.netid.SpoofInjector
import com.victorious.browser.panels.MeshBlockReason
import com.victorious.browser.panels.MeshOpenDecision
import com.victorious.browser.panels.MeshReachability
import com.victorious.browser.panels.Panel
import com.victorious.browser.panels.PanelStore
import com.victorious.browser.ui.theme.NeonCyan
import com.victorious.browser.ui.theme.NeonMagenta
import com.victorious.browser.antiphish.AntiPhishController
import com.victorious.browser.antiphish.AntiPhishWarningBanner
import kotlinx.coroutines.launch
import java.net.URI

private const val HOME_URL = "https://example.com"

/**
 * Multi-tab browsing host.
 *
 * WebView-state preservation: each tab keeps its OWN live [WebView] instance,
 * held in a composable-scoped map keyed by tab id. Switching tabs detaches the
 * previous WebView from the container and attaches the selected one — the
 * instances are never destroyed on switch, so each tab's full in-memory state
 * (DOM, scroll, JS heap, and WebBackForwardList) is preserved exactly. WebViews
 * are destroyed only when their tab is closed or the screen leaves composition.
 * (This is the most faithful approach; the saveState/restoreState Bundle route is
 * lossy — it drops JS heap and often the full back/forward list — so it is used
 * only as a fallback by other hosts, not here.)
 *
 * All tabs share the one process-wide WebView proxy override that
 * [LocalProxyService] installs via androidx.webkit ProxyController, so every tab
 * routes through the localhost inspector just like the single-tab screen did.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabbedBrowserScreen(
    tabStore: TabStore,
    modifier: Modifier = Modifier,
    onOpenNetwork: () -> Unit = {},
    onOpenScripts: () -> Unit = {},
    onOpenExtensions: () -> Unit = {},
    onBackToLauncher: (() -> Unit)? = null,
    onOpenAntiPhish: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    val tabs by tabStore.tabs.collectAsState()
    val activeId by tabStore.activeTabId.collectAsState()

    // Live WebView instances + per-tab transient UI state (kept out of TabStore so
    // the store stays pure/testable).
    val webViews = remember { mutableStateMapOf<String, WebView>() }
    val favicons = remember { mutableStateMapOf<String, Bitmap?>() }
    val progressMap = remember { mutableStateMapOf<String, Int>() }
    val loadingMap = remember { mutableStateMapOf<String, Boolean>() }

    var desktopUa by remember { mutableStateOf(false) }
    var urlBarText by remember { mutableStateOf("") }

    // Parity with single-tab BrowserScreen: Night (global setting, applied to every
    // tab's WebView), Reader (per-tab), URL tools + Inject (act on the active tab).
    val nightSettings = NightModeSettings.get(appContext)
    val nightMode by nightSettings.enabled.collectAsState()
    val extStore = remember { ExtensionStore.get(appContext) }
    val pausedHosts by extStore.pausedHosts.collectAsState()
    val readerTabs = remember { ReaderTabState() }
    var readerVersion by remember { mutableStateOf(0) }
    var showUrlTools by remember { mutableStateOf(false) }
    var showInject by remember { mutableStateOf(false) }

    val antiPhish = remember { AntiPhishController.get(appContext) }
    val phishResult by antiPhish.lastResult.collectAsState()
    val phishBanner by antiPhish.bannerVisible.collectAsState()

    val panelStore = remember { PanelStore.get(context) }
    val panels by panelStore.panels.collectAsState()
    var showSwitcher by remember { mutableStateOf(false) }

    // Regular-browser chrome: bookmarks, overflow menu, find-in-page.
    val bookmarkStore = remember { BookmarkStore.get(appContext) }
    val bookmarks by bookmarkStore.bookmarks.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showFind by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var findMatches by remember { mutableStateOf(0) }
    var findOrdinal by remember { mutableStateOf(0) }

    // Mesh gating (same contract as FleetPanelScreen).
    var meshChecking by remember { mutableStateOf(false) }
    var meshBlock by remember { mutableStateOf<MeshOpenDecision.Blocked?>(null) }
    var pendingMeshPanel by remember { mutableStateOf<Panel?>(null) }
    var pendingMeshNewTab by remember { mutableStateOf(true) }

    // Ensure there is always at least one tab while this screen is shown.
    LaunchedEffect(Unit) {
        if (tabStore.count == 0) tabStore.openTab(HOME_URL)
    }

    val activeTab = tabs.firstOrNull { it.id == activeId }

    // Keep the URL bar in sync with the active tab (follows navigation, like the
    // single-tab screen did).
    LaunchedEffect(activeId, activeTab?.url) {
        urlBarText = activeTab?.url ?: ""
    }

    fun buildWebView(tabId: String, initialUrl: String): WebView =
        WebView(appContext).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            applyVictoriousWebSettings(this, desktopUa)
            applyNightMode(this, nightMode)
            // Document-start user scripts, per tab (same as BrowserScreen). The
            // onPageStarted fallback in VictoriousWebViewClient covers the rest.
            UserScriptInjector.installAll(this, UserScriptStore.get(appContext).scripts.value)
            ExtensionEngine.attachBridgeIfNeeded(this)
            val netId = NetIdSettings.get(appContext)
            SpoofInjector.installDocumentStart(this, netId)
            SpoofInjector.applyUserAgent(this, netId.fingerprint.value)
            webViewClient = VictoriousWebViewClient(
                onUrlChanged = { url ->
                    if (readerTabs.onUrlChanged(tabId, url)) readerVersion++
                    tabStore.updateUrl(tabId, url)
                    tabStore.updateNav(tabId, canGoBack(), canGoForward())
                },
                onLoadingChanged = { loading ->
                    loadingMap[tabId] = loading
                    tabStore.updateNav(tabId, canGoBack(), canGoForward())
                },
            )
            webChromeClient = VictoriousWebChromeClient(
                onProgressChanged = { p ->
                    progressMap[tabId] = p
                    if (p >= 100) loadingMap[tabId] = false
                },
                onTitleChanged = { title -> tabStore.updateTitle(tabId, title) },
                onIconChanged = { icon -> favicons[tabId] = icon },
            )
            loadUrl(initialUrl)
        }

    fun navigateTo(raw: String) {
        val id = activeId ?: return
        val target = normalizeUrl(raw)
        urlBarText = target
        webViews[id]?.loadUrl(target)
        focusManager.clearFocus()
    }

    fun doOpenUrl(url: String, newTab: Boolean) {
        val target = normalizeUrl(url)
        if (newTab || tabStore.count == 0 || activeId == null) {
            tabStore.openTab(target)
        } else {
            val id = activeId!!
            tabStore.updateUrl(id, target)
            webViews[id]?.loadUrl(target)
        }
    }

    fun openPanelInTab(panel: Panel, newTab: Boolean) {
        showSwitcher = false
        if (!panel.reachOverMesh) {
            doOpenUrl(panel.url, newTab)
            return
        }
        meshChecking = true
        scope.launch {
            val decision = MeshReachability.resolveAndDecide(panel, appContext)
            meshChecking = false
            when (decision) {
                is MeshOpenDecision.Open -> doOpenUrl(panel.url, newTab)
                is MeshOpenDecision.Blocked -> {
                    pendingMeshPanel = panel
                    pendingMeshNewTab = newTab
                    meshBlock = decision
                }
            }
        }
    }

    fun currentUrl(): String = activeTab?.url?.takeIf { it.isNotBlank() } ?: urlBarText

    fun shareUrl() {
        val url = currentUrl()
        if (url.isBlank()) {
            Toast.makeText(appContext, "No URL to share", Toast.LENGTH_SHORT).show()
            return
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        runCatching { context.startActivity(Intent.createChooser(send, "Share link")) }
            .onFailure { Toast.makeText(appContext, "No app available to share", Toast.LENGTH_SHORT).show() }
    }

    fun copyUrl() {
        val url = currentUrl()
        if (url.isBlank()) return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("url", url))
        Toast.makeText(appContext, "Link copied", Toast.LENGTH_SHORT).show()
    }

    fun toggleReader() {
        val id = activeId ?: return
        val wv = webViews[id] ?: return
        if (readerTabs.isActive(id)) {
            val url = readerTabs.exit(id) ?: tabStore.get(id)?.url
            readerVersion++
            url?.let { wv.loadUrl(it) }
        } else {
            val url = tabStore.get(id)?.url ?: wv.url.orEmpty()
            ReaderModeController.enter(wv, nightMode) { found ->
                if (found) {
                    readerTabs.enter(id, url)
                    readerVersion++
                } else {
                    Toast.makeText(appContext, "Reader mode isn't available for this page", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun captureShot() {
        val id = activeId
        val wv = id?.let { webViews[it] }
        if (wv == null) {
            Toast.makeText(appContext, "No active page to capture", Toast.LENGTH_SHORT).show()
            return
        }
        val bmp = WebViewCapture.capture(wv)
        if (bmp == null) {
            Toast.makeText(appContext, "Capture failed", Toast.LENGTH_SHORT).show()
            return
        }
        val store = EvidenceStore.get(appContext)
        val case = store.ensureActiveCase()
        val host = hostOf(tabStore.get(id)?.url ?: wv.url.orEmpty()).orEmpty()
        store.addScreenshot(case.id, bmp, host = host, title = host.ifEmpty { "screenshot" })
        Toast.makeText(appContext, "Saved to evidence: " + case.name, Toast.LENGTH_SHORT).show()
    }

    fun runFind(q: String) {
        val wv = activeId?.let { webViews[it] } ?: return
        wv.setFindListener { active, count, done ->
            if (done) {
                findMatches = count
                findOrdinal = if (count > 0) active + 1 else 0
            }
        }
        if (q.isBlank()) {
            wv.clearMatches()
            findMatches = 0
            findOrdinal = 0
        } else {
            wv.findAllAsync(q)
        }
    }

    fun closeFind() {
        showFind = false
        findQuery = ""
        findMatches = 0
        findOrdinal = 0
        activeId?.let { webViews[it]?.clearMatches() }
    }

    // Destroy all WebViews when the screen leaves composition.
    DisposableEffect(Unit) {
        onDispose {
            webViews.values.forEach { wv ->
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.destroy()
            }
            webViews.clear()
        }
    }

    BackHandler(enabled = (activeTab?.canGoBack == true) || onBackToLauncher != null) {
        val id = activeId
        if (id != null && webViews[id]?.canGoBack() == true) {
            webViews[id]?.goBack()
        } else {
            onBackToLauncher?.invoke()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Tab bar
        TabBar(
            tabs = tabs,
            activeId = activeId,
            favicons = favicons,
            onSelect = { tabStore.activate(it) },
            onClose = { id ->
                webViews.remove(id)?.let { wv ->
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    wv.destroy()
                }
                favicons.remove(id); progressMap.remove(id); loadingMap.remove(id)
                if (readerTabs.remove(id)) readerVersion++
                tabStore.closeTab(id)
            },
            onNewTab = { tabStore.openTab(HOME_URL) },
        )

        // Address bar + Go
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBackToLauncher != null) {
                Box(
                    Modifier
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .clickable(onClick = onBackToLauncher)
                        .padding(4.dp),
                ) {
                    com.victorious.browser.ui.components.BrandRing(size = 30.dp, strokeWidth = 3.dp, glow = false)
                }
                Spacer(Modifier.width(6.dp))
            }
            OutlinedTextField(
                value = urlBarText,
                onValueChange = { urlBarText = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                leadingIcon = {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Filled.Search,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(18.dp),
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    cursorColor = NeonMagenta,
                    focusedLabelColor = NeonCyan,
                ),
                label = { Text("Search or enter address") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { navigateTo(urlBarText) }),
            )
            Spacer(Modifier.width(8.dp))
            com.victorious.browser.ui.components.NeonButton("Go", onClick = { navigateTo(urlBarText) })
        }

        // Nav controls: compact — back / forward / reload, then bookmark + overflow
        // menu. Everything else (tools, toggles, panels) lives in the ⋮ menu so the
        // chrome no longer overflows and clips controls off-screen.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavButton("◀", enabled = activeTab?.canGoBack == true) {
                activeId?.let { webViews[it]?.goBack() }
            }
            NavButton("▶", enabled = activeTab?.canGoForward == true) {
                activeId?.let { webViews[it]?.goForward() }
            }
            NavButton("↻", enabled = activeId != null) {
                activeId?.let { webViews[it]?.reload() }
            }
            Spacer(Modifier.weight(1f))

            val bmUrl = activeTab?.url ?: ""
            val isBookmarked = bmUrl.isNotBlank() && bookmarks.any { it.url == bmUrl }
            IconButton(
                enabled = bmUrl.isNotBlank(),
                onClick = {
                    val now = bookmarkStore.toggle(bmUrl, activeTab?.title ?: "")
                    Toast.makeText(
                        appContext,
                        if (now) "Bookmarked" else "Bookmark removed",
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            ) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark",
                    tint = if (isBookmarked) NeonCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = NeonCyan)
                }
                @Suppress("UNUSED_EXPRESSION") readerVersion // recompose on reader changes
                val readerOn = activeId?.let { readerTabs.isActive(it) } == true
                val activeHost = run {
                    val u = activeId?.let { tabStore.get(it)?.url } ?: ""
                    runCatching { URI(u).host?.lowercase() }.getOrNull()
                }
                val extsPaused = activeHost != null && activeHost in pausedHosts
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("New tab") },
                        onClick = { menuOpen = false; tabStore.openTab(HOME_URL) },
                    )
                    DropdownMenuItem(
                        text = { Text("Find in page") },
                        onClick = { menuOpen = false; showFind = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Share link") },
                        onClick = { menuOpen = false; shareUrl() },
                    )
                    DropdownMenuItem(
                        text = { Text("Copy link") },
                        onClick = { menuOpen = false; copyUrl() },
                    )
                    DropdownMenuItem(
                        text = { Text("Bookmarks") },
                        onClick = { menuOpen = false; showBookmarks = true },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(if (readerOn) "Reader mode: on" else "Reader mode: off") },
                        onClick = { menuOpen = false; toggleReader() },
                    )
                    DropdownMenuItem(
                        text = { Text(if (nightMode) "Night mode: on" else "Night mode: off") },
                        onClick = { nightSettings.setEnabled(!nightMode); menuOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text(if (desktopUa) "Desktop site: on" else "Desktop site: off") },
                        onClick = {
                            desktopUa = !desktopUa
                            webViews.values.forEach { applyVictoriousWebSettings(it, desktopUa) }
                            activeId?.let { webViews[it]?.reload() }
                            menuOpen = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (extsPaused) "Extensions: off here" else "Extensions: on here") },
                        enabled = activeHost != null,
                        onClick = {
                            activeHost?.let { extStore.setHostPaused(it, it !in pausedHosts) }
                            menuOpen = false
                        },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Fleet panels…") },
                        onClick = { menuOpen = false; showSwitcher = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Extensions") },
                        onClick = { menuOpen = false; onOpenExtensions() },
                    )
                    DropdownMenuItem(
                        text = { Text("User scripts") },
                        onClick = { menuOpen = false; onOpenScripts() },
                    )
                    DropdownMenuItem(
                        text = { Text("URL tools") },
                        onClick = { menuOpen = false; showUrlTools = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Inject JS / HTML") },
                        onClick = { menuOpen = false; showInject = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Screenshot → evidence") },
                        onClick = { menuOpen = false; captureShot() },
                    )
                    DropdownMenuItem(
                        text = { Text("Network log") },
                        onClick = { menuOpen = false; onOpenNetwork() },
                    )
                    if (onBackToLauncher != null) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Back to launcher") },
                            onClick = { menuOpen = false; onBackToLauncher() },
                        )
                    }
                }
            }
        }

        // Find-in-page bar (regular-browser feature).
        if (showFind) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = findQuery,
                    onValueChange = { findQuery = it; runFind(it) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Find in page") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        activeId?.let { webViews[it]?.findNext(true) }
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        cursorColor = NeonMagenta,
                        focusedLabelColor = NeonCyan,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        findMatches > 0 -> "$findOrdinal/$findMatches"
                        findQuery.isBlank() -> ""
                        else -> "0/0"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NavButton("▲", enabled = findMatches > 0) {
                    activeId?.let { webViews[it]?.findNext(false) }
                }
                NavButton("▼", enabled = findMatches > 0) {
                    activeId?.let { webViews[it]?.findNext(true) }
                }
                TextButton(
                    onClick = { closeFind() },
                    colors = ButtonDefaults.textButtonColors(contentColor = NeonMagenta),
                ) { Text("Done") }
            }
        }

        val activeProgress = activeId?.let { progressMap[it] } ?: 0
        val activeLoading = activeId?.let { loadingMap[it] } ?: false
        if (activeLoading || (activeProgress in 1..99)) {
            LinearProgressIndicator(
                progress = { activeProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = NeonCyan,
                trackColor = Color.Transparent,
            )
        } else {
            Spacer(Modifier.height(3.dp))
        }


        if (phishBanner) {
            phishResult?.let { r ->
                AntiPhishWarningBanner(
                    result = r,
                    onDismiss = { antiPhish.dismissCurrent() },
                    onReport = {
                        antiPhish.reportCurrent()
                        Toast.makeText(appContext, "Reported to Anti-Phish log", Toast.LENGTH_SHORT).show()
                    },
                    onOpenModule = onOpenAntiPhish,
                )
            }
        }

        // WebView host: a single container; we attach the active tab's WebView and
        // keep inactive ones alive (detached) so their state is preserved.
        AndroidView(
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            update = { container ->
                val id = activeId
                if (id == null) {
                    container.removeAllViews()
                    return@AndroidView
                }
                val wv = webViews.getOrPut(id) {
                    buildWebView(id, tabStore.get(id)?.url ?: HOME_URL)
                }
                // Night mode is a global toggle: keep every live tab in sync.
                webViews.values.forEach { applyNightMode(it, nightMode) }
                val current = container.getChildAt(0)
                if (current !== wv) {
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    container.removeAllViews()
                    container.addView(wv)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }

    if (showUrlTools) {
        UrlToolsDialog(
            initialUrl = activeTab?.url ?: urlBarText,
            onOpenUrl = { url -> navigateTo(url) },
            onDismiss = { showUrlTools = false },
        )
    }
    if (showInject) {
        InjectionDialog(webView = activeId?.let { webViews[it] }, onDismiss = { showInject = false })
    }

    if (showSwitcher) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        FleetPanelSwitcherSheet(
            panels = panels,
            sheetState = sheetState,
            onDismiss = { showSwitcher = false },
            onOpenCurrent = { openPanelInTab(it, newTab = false) },
            onOpenNewTab = { openPanelInTab(it, newTab = true) },
        )
    }

    if (showBookmarks) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        BookmarksSheet(
            bookmarks = bookmarks,
            sheetState = sheetState,
            onDismiss = { showBookmarks = false },
            onOpen = { bm -> showBookmarks = false; doOpenUrl(bm.url, newTab = false) },
            onOpenNewTab = { bm -> showBookmarks = false; doOpenUrl(bm.url, newTab = true) },
            onRemove = { bm -> bookmarkStore.remove(bm.url) },
        )
    }

    if (meshChecking) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Checking mesh…") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Resolving mesh target and probing reachability.")
                }
            },
            confirmButton = {},
        )
    }

    meshBlock?.let { blocked ->
        AlertDialog(
            onDismissRequest = { meshBlock = null },
            title = {
                Text(
                    when (blocked.reason) {
                        MeshBlockReason.TUNNEL_DOWN -> "Mesh tunnel down"
                        MeshBlockReason.UNREACHABLE -> "Mesh node unreachable"
                        MeshBlockReason.UNKNOWN_HOST -> "Unknown mesh host"
                        MeshBlockReason.NOT_MESH_PANEL -> "Not a mesh panel"
                    },
                )
            },
            text = { Text(blocked.message) },
            confirmButton = {
                val retryPanel = pendingMeshPanel
                if (retryPanel != null && blocked.reason != MeshBlockReason.NOT_MESH_PANEL) {
                    TextButton(onClick = {
                        meshBlock = null
                        openPanelInTab(retryPanel, pendingMeshNewTab)
                    }) { Text("Retry", color = NeonCyan) }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    meshBlock = null
                    pendingMeshPanel = null
                }) { Text("Dismiss", color = NeonMagenta) }
            },
        )
    }
}

@Composable
private fun TabBar(
    tabs: List<BrowserTab>,
    activeId: String?,
    favicons: SnapshotStateMap<String, Bitmap?>,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onNewTab: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(tabs, key = { it.id }) { tab ->
                TabChip(
                    tab = tab,
                    active = tab.id == activeId,
                    favicon = favicons[tab.id],
                    onSelect = { onSelect(tab.id) },
                    onClose = { onClose(tab.id) },
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        TextButton(
            onClick = onNewTab,
            colors = ButtonDefaults.textButtonColors(contentColor = NeonCyan),
        ) { Text("＋", style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun TabChip(
    tab: BrowserTab,
    active: Boolean,
    favicon: Bitmap?,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val border = if (active) NeonCyan else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    Row(
        modifier = Modifier
            .height(32.dp)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .background(
                if (active) NeonCyan.copy(alpha = 0.12f) else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (favicon != null) {
            Image(
                bitmap = favicon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = tabLabel(tab),
            style = MaterialTheme.typography.labelMedium,
            color = if (active) NeonCyan else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 120.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "✕",
            style = MaterialTheme.typography.labelMedium,
            color = NeonMagenta,
            modifier = Modifier
                .clickable(onClick = onClose)
                .padding(horizontal = 2.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FleetPanelSwitcherSheet(
    panels: List<Panel>,
    sheetState: androidx.compose.material3.SheetState,
    onDismiss: () -> Unit,
    onOpenCurrent: (Panel) -> Unit,
    onOpenNewTab: (Panel) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(
                "Quick panel switch",
                style = MaterialTheme.typography.titleMedium,
                color = NeonMagenta,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Filter panels") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            val filtered = panels.filter {
                query.isBlank() ||
                    it.name.contains(query, ignoreCase = true) ||
                    it.url.contains(query, ignoreCase = true)
            }
            if (filtered.isEmpty()) {
                Text(
                    "No matching panels.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column {
                filtered.forEach { panel ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    panel.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (panel.reachOverMesh) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "mesh",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NeonMagenta,
                                    )
                                }
                            }
                            Text(
                                panel.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(onClick = { onOpenCurrent(panel) }) {
                            Text("Open", color = NeonCyan)
                        }
                        TextButton(onClick = { onOpenNewTab(panel) }) {
                            Text("New tab", color = NeonCyan)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarksSheet(
    bookmarks: List<Bookmark>,
    sheetState: androidx.compose.material3.SheetState,
    onDismiss: () -> Unit,
    onOpen: (Bookmark) -> Unit,
    onOpenNewTab: (Bookmark) -> Unit,
    onRemove: (Bookmark) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(
                "Bookmarks",
                style = MaterialTheme.typography.titleMedium,
                color = NeonMagenta,
            )
            Spacer(Modifier.height(8.dp))
            if (bookmarks.isEmpty()) {
                Text(
                    "No bookmarks yet. Tap the ☆ in the toolbar to save the current page.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column {
                bookmarks.forEach { bm ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            Modifier
                                .weight(1f)
                                .clickable { onOpen(bm) },
                        ) {
                            Text(
                                bm.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                bm.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(onClick = { onOpenNewTab(bm) }) {
                            Text("New tab", color = NeonCyan)
                        }
                        TextButton(onClick = { onRemove(bm) }) {
                            Text("Remove", color = NeonMagenta)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            contentColor = NeonCyan,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

/** Tab label: page title if known, else the host, else the raw URL. */
internal fun tabLabel(tab: BrowserTab): String {
    if (tab.title.isNotBlank()) return tab.title
    return hostOf(tab.url) ?: tab.url.ifBlank { "New tab" }
}

internal fun hostOf(url: String): String? =
    runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() }
