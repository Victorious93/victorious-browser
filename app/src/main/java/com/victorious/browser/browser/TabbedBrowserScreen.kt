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
import android.net.http.SslCertificate
import android.os.Handler
import android.os.Looper
import android.webkit.URLUtil
import androidx.compose.ui.focus.onFocusChanged
import com.victorious.browser.inspect.CaptureLookup
import com.victorious.browser.inspect.CaptureStore
import com.victorious.browser.inspect.CapturedExchange
import com.victorious.browser.inspect.LocalProxyService
import com.victorious.browser.sandbox.SandboxManager
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
    onSendToRepeater: (CapturedExchange?) -> Unit = {},
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
    // History, downloads, search engine, long-press menu, recon chrome.
    val historyStore = remember { HistoryStore.get(appContext) }
    val history by historyStore.entries.collectAsState()
    val downloadStore = remember { DownloadStore.get(appContext) }
    val downloads by downloadStore.records.collectAsState()
    val browserPrefs = remember { BrowserPrefs.get(appContext) }
    val searchEngine by browserPrefs.searchEngine.collectAsState()
    val proxyOn by LocalProxyService.isRunning.collectAsState()
    var showHistory by remember { mutableStateOf(false) }
    var showDownloads by remember { mutableStateOf(false) }
    var showTabSwitcher by remember { mutableStateOf(false) }
    var showEngineDialog by remember { mutableStateOf(false) }
    var showCertInfo by remember { mutableStateOf(false) }
    var menuRecon by remember { mutableStateOf(false) }
    var linkMenu by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var urlFocused by remember { mutableStateOf(false) }
    // Per-tab TLS certificate as reported by WebView (null for http / not loaded).
    val certs = remember { mutableStateMapOf<String, SslCertificate?>() }
    val consoleLogs = remember { mutableStateMapOf<String, List<ConsoleEntry>>() }
    val sessionStore = remember { TabSessionStore.get(appContext) }

    var showCookies by remember { mutableStateOf(false) }
    var cookieRows by remember { mutableStateOf<List<CookieJarLogic.JarCookie>>(emptyList()) }
    var showStorage by remember { mutableStateOf(false) }
    var storageDump by remember { mutableStateOf<StorageInspector.Dump?>(null) }
    var storageLoading by remember { mutableStateOf(false) }
    var showConsole by remember { mutableStateOf(false) }
    var showClearSite by remember { mutableStateOf(false) }

    var meshChecking by remember { mutableStateOf(false) }
    var meshBlock by remember { mutableStateOf<MeshOpenDecision.Blocked?>(null) }
    var pendingMeshPanel by remember { mutableStateOf<Panel?>(null) }
    var pendingMeshNewTab by remember { mutableStateOf(true) }

    // Ensure there is always at least one tab while this screen is shown.
    LaunchedEffect(tabs.isEmpty()) {
        if (tabStore.count == 0) tabStore.openTab(HOME_URL)
    }

    // Persist non-private tabs so a process kill restores URLs (not JS heap / back-forward).
    LaunchedEffect(tabs, activeId) {
        sessionStore.save(tabs, activeId)
    }

    val activeTab = tabs.firstOrNull { it.id == activeId }

    // Keep the URL bar in sync with the active tab (follows navigation, like the
    // single-tab screen did).
    LaunchedEffect(activeId, activeTab?.url) {
        if (!urlFocused) urlBarText = activeTab?.url ?: ""
    }
    LaunchedEffect(activeId) {
        // Switching tabs always shows the new tab's URL.
        urlBarText = activeTab?.url ?: ""
    }

    fun startDownloadImpl(url: String, ua: String?, disposition: String?, mime: String?) {
        if (!URLUtil.isNetworkUrl(url)) {
            Toast.makeText(appContext, "Can't download this link type (only http/https)", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching { downloadStore.enqueue(url, ua, disposition, mime) }
            .onSuccess { Toast.makeText(appContext, "Downloading $it", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(appContext, "Download failed: ${it.message}", Toast.LENGTH_SHORT).show() }
    }

    fun appendConsole(tabId: String, level: String, message: String, source: String, line: Int) {
        val next = ConsoleLog.append(
            consoleLogs[tabId].orEmpty(),
            ConsoleEntry(level, message, source, line, System.currentTimeMillis()),
        )
        consoleLogs[tabId] = next
    }

    fun refreshCookies() {
        val url = tabStore.activeTab()?.url?.takeIf { it.isNotBlank() } ?: urlBarText
        cookieRows = BrowserRecon.cookiesForUrl(activeId?.let { webViews[it] }, url)
    }

    fun refreshStorage() {
        val wv = activeId?.let { webViews[it] }
        if (wv == null) {
            storageDump = StorageInspector.Dump("", emptyList(), emptyList(), error = "No active page")
            return
        }
        storageLoading = true
        wv.evaluateJavascript(StorageInspector.dumpScript()) { raw ->
            storageDump = StorageInspector.parseDump(raw)
            storageLoading = false
        }
    }

    fun evalStorage(script: String, thenRefresh: Boolean = true) {
        val wv = activeId?.let { webViews[it] } ?: return
        wv.evaluateJavascript(script) {
            if (thenRefresh) refreshStorage()
        }
    }

    fun buildWebView(tabId: String, initialUrl: String): WebView =
        WebView(appContext).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            val privateTab = tabStore.get(tabId)?.incognito == true
            if (privateTab) SandboxManager.applyToNewWebView(this, INCOGNITO_PROFILE)
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
                    if (!loading) {
                        certs[tabId] = certificate
                        val u = url.orEmpty()
                        val skipHistory = readerTabs.isActive(tabId) ||
                            tabStore.get(tabId)?.incognito == true
                        if (!skipHistory) historyStore.record(u, title.orEmpty())
                    }
                },
                onPageError = { errUrl, desc, code ->
                    appendConsole(tabId, "ERROR", "Page error $code: $desc", errUrl, 0)
                },
            )
            webChromeClient = VictoriousWebChromeClient(
                onProgressChanged = { p ->
                    progressMap[tabId] = p
                    if (p >= 100) loadingMap[tabId] = false
                },
                onTitleChanged = { t ->
                    tabStore.updateTitle(tabId, t)
                    if (tabStore.get(tabId)?.incognito != true) {
                        url?.let { historyStore.retitle(it, t) }
                    }
                },
                onIconChanged = { icon -> favicons[tabId] = icon },
                onConsoleMessage = { level, message, source, line ->
                    appendConsole(tabId, level, message, source, line)
                },
            )
            setDownloadListener { dUrl, ua, disposition, mime, _ ->
                startDownloadImpl(dUrl, ua, disposition, mime)
            }
            setOnLongClickListener { v ->
                val wv = v as WebView
                val hit = wv.hitTestResult
                when (hit.type) {
                    WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                        hit.extra?.let { linkMenu = it to false }
                        hit.extra != null
                    }
                    WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                        // hit.extra is the IMAGE src; ask WebView for the enclosing href.
                        val msg = Handler(Looper.getMainLooper()).obtainMessage()
                        wv.requestFocusNodeHref(msg)
                        val href = msg.data.getString("url")?.takeIf { it.isNotBlank() }
                        (href ?: hit.extra)?.let { linkMenu = it to (href == null) }
                        true
                    }
                    else -> false // let WebView handle text selection etc.
                }
            }
            loadUrl(initialUrl)
        }

    fun destroyTab(id: String) {
        webViews.remove(id)?.let { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.destroy()
        }
        favicons.remove(id); progressMap.remove(id); loadingMap.remove(id); certs.remove(id)
        consoleLogs.remove(id)
        if (readerTabs.remove(id)) readerVersion++
        val wasPrivate = tabStore.get(id)?.incognito == true
        tabStore.closeTab(id)
        if (wasPrivate && tabStore.tabs.value.none { it.incognito }) {
            // Last private tab gone — drop the isolated profile (cookies/storage) when supported.
            SandboxManager.delete(INCOGNITO_PROFILE)
        }
    }

    fun navigateTo(raw: String) {
        val id = activeId ?: return
        val target = normalizeUrl(raw, searchEngine.queryPrefix)
        urlBarText = target
        webViews[id]?.loadUrl(target)
        focusManager.clearFocus()
    }

    fun doOpenUrl(url: String, newTab: Boolean, incognito: Boolean = activeTab?.incognito == true) {
        val target = normalizeUrl(url, searchEngine.queryPrefix)
        if (newTab || tabStore.count == 0 || activeId == null) {
            tabStore.openTab(target, incognito = incognito)
        } else {
            val id = activeId!!
            tabStore.updateUrl(id, target)
            webViews[id]?.loadUrl(target)
        }
    }

    fun sendCurrentToRepeater() {
        val page = tabStore.activeTab()?.url?.takeIf { it.isNotBlank() } ?: urlBarText
        val seed = CaptureLookup.latestForPage(CaptureStore.exchanges.value, page)
        if (seed == null) {
            Toast.makeText(
                appContext,
                "No captured request for this origin. Enable PROXY, reload, then retry — or open an empty editor.",
                Toast.LENGTH_LONG,
            ).show()
        }
        onSendToRepeater(seed)
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


    fun shareText(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching { context.startActivity(Intent.createChooser(send, "Share link")) }
            .onFailure { Toast.makeText(appContext, "No app available to share", Toast.LENGTH_SHORT).show() }
    }

    fun copyText(text: String, toast: String = "Copied") {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("recon", text))
        Toast.makeText(appContext, toast, Toast.LENGTH_SHORT).show()
    }

    fun currentUrl(): String = activeTab?.url?.takeIf { it.isNotBlank() } ?: urlBarText

    fun shareUrl() {
        val url = currentUrl()
        if (url.isBlank()) {
            Toast.makeText(appContext, "No URL to share", Toast.LENGTH_SHORT).show()
            return
        }
        shareText(url)
    }

    fun copyUrl() {
        val url = currentUrl()
        if (url.isBlank()) return
        copyText(url, "Link copied")
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

    // Back priority: close find bar → dismiss address-bar editing → page back → launcher.
    BackHandler(enabled = showFind || urlFocused || (activeTab?.canGoBack == true) || onBackToLauncher != null) {
        val id = activeId
        when {
            showFind -> closeFind()
            urlFocused -> focusManager.clearFocus()
            id != null && webViews[id]?.canGoBack() == true -> webViews[id]?.goBack()
            else -> onBackToLauncher?.invoke()
        }
    }

    fun applyDesktop(on: Boolean) {
        desktopUa = on
        val netId = NetIdSettings.get(appContext)
        webViews.values.forEach {
            applyVictoriousWebSettings(it, on)
            // Bug fix: re-applying settings used to drop an active Net ID UA spoof.
            SpoofInjector.applyUserAgent(it, netId.fingerprint.value)
        }
        activeId?.let { webViews[it]?.reload() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Tab strip (quick switch) + tab-count button that opens the full switcher.
        TabBar(
            tabs = tabs,
            activeId = activeId,
            favicons = favicons,
            onSelect = { tabStore.activate(it) },
            onClose = { id -> destroyTab(id) },
            onNewTab = { tabStore.openTab(HOME_URL) },
            onShowAll = { showTabSwitcher = true },
        )

        // Address bar: suggestions from tabs/bookmarks/history; search vs URL.
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
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { urlFocused = it.isFocused },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                },
                trailingIcon = if (urlFocused && urlBarText.isNotEmpty()) {
                    {
                        Text(
                            "✕",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { urlBarText = "" }.padding(8.dp),
                        )
                    }
                } else null,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    cursorColor = NeonMagenta,
                    focusedLabelColor = NeonCyan,
                ),
                label = { Text("Search ${searchEngine.label} or enter address") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { navigateTo(urlBarText) }),
            )
            Spacer(Modifier.width(8.dp))
            com.victorious.browser.ui.components.NeonButton("Go", onClick = { navigateTo(urlBarText) })
        }

        val typed = urlBarText
        if (urlFocused && typed.isNotBlank() && typed != activeTab?.url) {
            val suggestions = remember(typed, tabs, bookmarks, history, activeId) {
                buildSuggestions(typed, tabs, bookmarks, history, excludeTabId = activeId, limit = 6)
            }
            SuggestionList(
                suggestions = suggestions,
                typed = typed,
                searchLabel = searchEngine.label,
                onPick = { s ->
                    if (s.tabId != null) {
                        focusManager.clearFocus()
                        tabStore.activate(s.tabId)
                    } else {
                        navigateTo(s.url)
                    }
                },
                onSearch = { navigateTo(typed) },
            )
        }

        // Nav controls: back / forward / reload-or-stop, recon chips, bookmark star, ⋮.
        val activeLoadingNow = activeId?.let { loadingMap[it] } == true
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
            if (activeLoadingNow) {
                NavButton("✕", enabled = true) { activeId?.let { webViews[it]?.stopLoading() } }
            } else {
                NavButton("↻", enabled = activeId != null) { activeId?.let { webViews[it]?.reload() } }
            }
            // Honest recon chips: only what we actually know.
            if (proxyOn) {
                ReconChip("PROXY", NeonMagenta) { onOpenNetwork() }
            }
            val curUrl = activeTab?.url.orEmpty()
            val cert = activeId?.let { certs[it] }
            val tls = tlsHint(curUrl, cert?.issuedBy?.cName, proxyOn)
            if (tls != null) {
                ReconChip(tls.label, if (tls.warn) NeonMagenta else NeonCyan) {
                    if (cert != null) showCertInfo = true
                }
            }
            val jsErrors = activeId?.let { ConsoleLog.errorCount(consoleLogs[it].orEmpty()) } ?: 0
            if (jsErrors > 0) {
                ReconChip("JS $jsErrors", NeonMagenta) { showConsole = true }
            }
            if (activeTab?.incognito == true) {
                ReconChip("PRIVATE", NeonMagenta) { }
            }
            Spacer(Modifier.weight(1f))

            val bmUrl = activeTab?.url ?: ""
            val isBookmarked = bmUrl.isNotBlank() && bookmarks.any { it.url == bmUrl }
            IconButton(
                enabled = bmUrl.isNotBlank(),
                onClick = {
                    val now = bookmarkStore.toggle(bmUrl, activeTab?.title ?: "")
                    Toast.makeText(appContext, if (now) "Bookmarked" else "Bookmark removed", Toast.LENGTH_SHORT).show()
                },
            ) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark",
                    tint = if (isBookmarked) NeonCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Box {
                IconButton(onClick = { menuRecon = false; menuOpen = true }) {
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
                    @Composable
                    fun item(label: String, enabled: Boolean = true, keepOpen: Boolean = false, action: () -> Unit) {
                        DropdownMenuItem(
                            text = { Text(label) },
                            enabled = enabled,
                            onClick = { if (!keepOpen) menuOpen = false; action() },
                        )
                    }
                    if (!menuRecon) {
                        item("New tab") { tabStore.openTab(HOME_URL) }
                        item("New private tab") { tabStore.openTab(HOME_URL, incognito = true) }
                        item("Tabs (${tabs.size})…") { showTabSwitcher = true }
                        HorizontalDivider()
                        item("History") { showHistory = true }
                        item("Bookmarks") { showBookmarks = true }
                        item("Downloads") { showDownloads = true }
                        HorizontalDivider()
                        item("Find in page") { showFind = true }
                        item("Share link") { shareUrl() }
                        item("Copy link") { copyUrl() }
                        item(if (desktopUa) "Desktop site: on" else "Desktop site: off") { applyDesktop(!desktopUa) }
                        item(if (readerOn) "Reader mode: on" else "Reader mode: off") { toggleReader() }
                        item(if (nightMode) "Night mode: on" else "Night mode: off") { nightSettings.setEnabled(!nightMode) }
                        HorizontalDivider()
                        item("Recon tools ▸", keepOpen = true) { menuRecon = true }
                        item("Search engine: ${searchEngine.label}") { showEngineDialog = true }
                        if (onBackToLauncher != null) item("All tools (launcher)") { onBackToLauncher() }
                    } else {
                        item("◂ Back", keepOpen = true) { menuRecon = false }
                        HorizontalDivider()
                        item(if (proxyOn) "Proxy history (proxy on)" else "Proxy history") { onOpenNetwork() }
                        item("Send to repeater") { sendCurrentToRepeater() }
                        item("Cookie jar") { refreshCookies(); showCookies = true }
                        item("Web storage") { showStorage = true; refreshStorage() }
                        item("Console") { showConsole = true }
                        item("Clear site data…", enabled = SiteData.originOf(currentUrl()) != null) { showClearSite = true }
                        if (onOpenAntiPhish != null) item("Anti-Phish") { onOpenAntiPhish() }
                        item("Inject JS / HTML") { showInject = true }
                        item("URL tools") { showUrlTools = true }
                        item("Screenshot → evidence") { captureShot() }
                        if (certs[activeId ?: ""] != null) item("Certificate info") { showCertInfo = true }
                        HorizontalDivider()
                        item(if (extsPaused) "Extensions: off here" else "Extensions: on here", enabled = activeHost != null) {
                            activeHost?.let { extStore.setHostPaused(it, it !in pausedHosts) }
                        }
                        item("Extensions") { onOpenExtensions() }
                        item("User scripts") { onOpenScripts() }
                        item("Fleet panels…") { showSwitcher = true }
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
        BookmarksSheet(
            bookmarks = bookmarks,
            onDismiss = { showBookmarks = false },
            onOpen = { bm -> showBookmarks = false; doOpenUrl(bm.url, newTab = false) },
            onOpenNewTab = { bm -> showBookmarks = false; doOpenUrl(bm.url, newTab = true) },
            onRemove = { bm -> bookmarkStore.remove(bm.url) },
        )
    }

    if (showHistory) {
        HistorySheet(
            history = history,
            onDismiss = { showHistory = false },
            onOpen = { h -> showHistory = false; doOpenUrl(h.url, newTab = false) },
            onOpenNewTab = { h -> showHistory = false; doOpenUrl(h.url, newTab = true) },
            onRemove = { h -> historyStore.remove(h.url) },
            onClearAll = { historyStore.clear() },
        )
    }

    if (showDownloads) {
        DownloadsSheet(
            records = downloads,
            statusOf = { downloadStore.status(it) },
            onDismiss = { showDownloads = false },
            onOpenFile = { r ->
                val uri = downloadStore.fileUri(r.downloadId)
                if (uri == null) {
                    Toast.makeText(appContext, "File not available", Toast.LENGTH_SHORT).show()
                } else {
                    val view = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, r.mimeType.ifBlank { context.contentResolver.getType(uri) ?: "*/*" })
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching { context.startActivity(view) }
                        .onFailure { Toast.makeText(appContext, "No app can open this file", Toast.LENGTH_SHORT).show() }
                }
            },
            onRemove = { r, deleteFile -> downloadStore.remove(r.downloadId, deleteFile) },
        )
    }

    if (showTabSwitcher) {
        TabSwitcherSheet(
            tabs = tabs,
            activeId = activeId,
            favicons = favicons,
            onDismiss = { showTabSwitcher = false },
            onSelect = { tabStore.activate(it); showTabSwitcher = false },
            onClose = { destroyTab(it) },
            onNewTab = { tabStore.openTab(HOME_URL); showTabSwitcher = false },
            onNewPrivate = { tabStore.openTab(HOME_URL, incognito = true); showTabSwitcher = false },
            onCloseOthers = {
                val keep = activeId
                tabs.map { it.id }.filter { it != keep }.forEach { destroyTab(it) }
            },
            onCloseAll = {
                tabs.map { it.id }.forEach { destroyTab(it) }
                showTabSwitcher = false
            },
        )
    }

    if (showEngineDialog) {
        SearchEngineDialog(
            current = searchEngine,
            onPick = { browserPrefs.setSearchEngine(it); showEngineDialog = false },
            onDismiss = { showEngineDialog = false },
        )
    }

    linkMenu?.let { (url, isImage) ->
        LinkMenuDialog(
            url = url,
            isImage = isImage,
            onDismiss = { linkMenu = null },
            onOpen = { doOpenUrl(url, newTab = false) },
            onOpenNewTab = { doOpenUrl(url, newTab = true) },
            onOpenBackground = {
                tabStore.openTab(url, activate = false, incognito = activeTab?.incognito == true)
                Toast.makeText(appContext, "Opened in background tab", Toast.LENGTH_SHORT).show()
            },
            onCopy = { copyText(url) },
            onShare = { shareText(url) },
            onBookmark = {
                bookmarkStore.add(url, "")
                Toast.makeText(appContext, "Bookmarked", Toast.LENGTH_SHORT).show()
            },
            onDownload = {
                startDownloadImpl(url, activeId?.let { webViews[it]?.settings?.userAgentString }, null, null)
            },
        )
    }

    if (showCookies) {
        CookieJarSheet(
            url = currentUrl(),
            cookies = cookieRows,
            onDismiss = { showCookies = false },
            onRefresh = { refreshCookies() },
            onSave = { name, value ->
                runCatching {
                    BrowserRecon.setCookie(activeId?.let { webViews[it] }, currentUrl(), name, value)
                }.onFailure {
                    Toast.makeText(appContext, "Cookie not set: ${it.message}", Toast.LENGTH_SHORT).show()
                }
                refreshCookies()
            },
            onDelete = { name ->
                BrowserRecon.deleteCookie(activeId?.let { webViews[it] }, currentUrl(), name)
                refreshCookies()
            },
            onExport = {
                val json = CookieJarLogic.exportJson(currentUrl(), cookieRows, System.currentTimeMillis())
                copyText(json, "Cookie jar JSON copied")
            },
        )
    }

    if (showStorage) {
        StorageInspectorSheet(
            dump = storageDump,
            loading = storageLoading,
            onDismiss = { showStorage = false },
            onRefresh = { refreshStorage() },
            onSet = { store, key, value -> evalStorage(StorageInspector.setScript(store, key, value)) },
            onDelete = { store, key -> evalStorage(StorageInspector.removeScript(store, key)) },
            onClear = { store -> evalStorage(StorageInspector.clearScript(store)) },
            onExport = {
                val dump = storageDump
                if (dump == null) {
                    Toast.makeText(appContext, "Nothing to export yet", Toast.LENGTH_SHORT).show()
                } else {
                    copyText(StorageInspector.exportJson(dump, System.currentTimeMillis()), "Storage JSON copied")
                }
            },
        )
    }

    if (showConsole) {
        val id = activeId
        ConsoleSheet(
            entries = id?.let { consoleLogs[it] }.orEmpty(),
            onDismiss = { showConsole = false },
            onClear = { if (id != null) consoleLogs[id] = emptyList() },
            onCopy = {
                val text = id?.let { consoleLogs[it] }.orEmpty().joinToString("\n") { ConsoleLog.format(it) }
                copyText(text.ifBlank { "(empty)" }, "Console copied")
            },
        )
    }

    if (showClearSite) {
        val origin = SiteData.originOf(currentUrl())
        ClearSiteDataDialog(
            origin = origin,
            onDismiss = { showClearSite = false },
            onConfirm = {
                val wv = activeId?.let { webViews[it] }
                val url = currentUrl()
                BrowserRecon.clearCookiesForUrl(wv, url)
                origin?.let { BrowserRecon.clearOriginStorage(wv, it) }
                wv?.evaluateJavascript(SiteData.clearStorageScript()) { }
                showClearSite = false
                Toast.makeText(appContext, "Cleared site data for ${origin ?: url}", Toast.LENGTH_SHORT).show()
            },
        )
    }

    if (showCertInfo) {
        val cert = activeId?.let { certs[it] }
        AlertDialog(
            onDismissRequest = { showCertInfo = false },
            title = { Text("TLS certificate (as seen by WebView)") },
            text = {
                Text(
                    if (cert == null) "No certificate for this page." else certSummary(cert, proxyOn),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = { TextButton(onClick = { showCertInfo = false }) { Text("Close", color = NeonCyan) } },
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
    onShowAll: () -> Unit,
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
        // Tab-count square, Chrome-style: opens the full tab switcher.
        Box(
            Modifier
                .size(28.dp)
                .border(1.5.dp, NeonCyan, RoundedCornerShape(6.dp))
                .clickable(onClick = onShowAll),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (tabs.size > 99) ":D" else tabs.size.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = NeonCyan,
            )
        }
    }
}

@Composable
private fun ReconChip(label: String, color: Color, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .padding(start = 4.dp)
            .widthIn(max = 150.dp)
            .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private fun certSummary(cert: SslCertificate, proxyOn: Boolean): String {
    val fmt = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
    val sb = StringBuilder()
    sb.append("Issued to: ").append(cert.issuedTo?.dName.orEmpty()).append("\n\n")
    sb.append("Issued by: ").append(cert.issuedBy?.dName.orEmpty()).append("\n\n")
    cert.validNotBeforeDate?.let { sb.append("Valid from: ").append(fmt.format(it)).append("\n") }
    cert.validNotAfterDate?.let { sb.append("Valid until: ").append(fmt.format(it)).append("\n") }
    if (proxyOn) {
        sb.append("\nThe inspection proxy is on, so this is the certificate the proxy presented to WebView ")
            .append("(normally issued by the Victorious inspection CA), not the origin server's certificate.")
    } else {
        sb.append("\nShown as reported by WebView after the load. It is not an independent validation.")
    }
    return sb.toString()
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
        if (tab.incognito) {
            Spacer(Modifier.width(4.dp))
            Text("P", style = MaterialTheme.typography.labelSmall, color = NeonMagenta)
        }
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
