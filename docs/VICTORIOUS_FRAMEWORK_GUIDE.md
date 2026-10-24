# Victorious Framework — Victorious Browser Living Guide

Last updated: 2026-10-24 (bug-testing pass + 0.2.0 release push — see §9). This guide is updated whenever project state changes.
[Certain] §§1–8 below predate several features merged to `main` after 2026-10-23 (light theme toggle, Mesh fleet bridge, WebView-native userscript/extensions engine, help/manual screen, and the recon/pentest tool surface: MSF console, active scanner, fuzzer, OSINT, evidence export). This pass's scope was bug-testing + a release push, not a full resync — §§1–8's inventory is not re-verified against current `main` and should not be read as current status for anything landed after WireGuard/Headscale.
Last bridge update: 2026-10-20 — frozen remote-control envelope v1 (MINOR=1): capabilities.get, remote.command, macro.step/run, node.status.get, config.export/import. See docs/MANUAL.md.
Confidence labels: **[Certain]** known/verified, **[Likely]** strong inference, **[Guessing]** assumption.

## 1. Current project status

| Area | Status |
|---|---|
| Shell engine (system WebView in Compose) | Finished |
| Proxy / MITM library (LittleProxy + BouncyCastle local CA) | Finished |
| Fleet-panel launcher | Finished |
| Fleet control bridge (Messenger IPC) | Finished |
| Proxy modify-and-replay | Finished |
| Hermit-reference feature set (6 features) | Finished — merged to `main` |
| URL tools + injection tools, URL changer | Finished — merged to `main` |
| Virus/malware scan (corrected after live testing) | Finished — merged to `main` |
| System-bar icon fix for the always-dark theme | Finished — landed on this branch |
| **WireGuard + Headscale settings** (this guide's current focus) | **In progress — landed on this branch** |

Tally: Finished 10 (5 original + the completed Hermit-reference set + URL/injection tools/changer + corrected virus scan + system-bar fix) · In Progress 1 (WireGuard/Headscale) · Deferred 0 · Abandoned 0.
[Certain] Everything compiles, `assembleDebug` succeeds and `testDebugUnitTest` passes (90 tests, 11 new for WireGuard/Headscale). [Certain] The WireGuard integration is the first feature in this app to use Android's `VpnService` — added at the operator's explicit request after being told this conflicts with the documented "VpnService slot reserved for the Victorious Mesh app" design (section 4.10). [Guessing] On-device/behavioral verification (including whether the tunnel actually connects, and whether the system-bar fix renders correctly) is outstanding for everything in this guide — no device or emulator in this build environment.
[Certain] Status above for the first five rows is taken from `README.md` and git history, not from a build or test run in this session.

## 2. Purpose of the Hermit-reference work

Hermit (Chimbori's Lite Apps Browser) is used as a **behavioral feature spec**, not a code source.

- [Certain] Hermit's feature list below comes from public store listings/release notes
  ([APKMirror](https://apkmirror.com/apk/chimbori/hermit-lite-apps-browser)).
- [Guessing] Hermit's source is not public; license unverified. **Do not decompile or copy its APK.**
  Build features independently from open, permissively licensed components, and record
  them in `NOTICE` (same convention used for Lightning Browser).
- Do not chase parity with Hermit's paid/ad-free tiers; Victorious Browser is a recon/inspection tool.

## 3. Feature map and architecture

### 3a. Hermit-reference set (finished, merged)

| Hermit feature | Existing hook in this repo | Implementation | Effort | Status |
|---|---|---|---|---|
| Night mode | `browser/NightMode.kt`, `BrowserScreen.kt` | Algorithmic darkening, falling back to force-dark; persisted "Night" chip in the browser chrome. CSS injection not done. | S | **Done** (unit-tested strategy selection; not verified on a device) |
| Lite Apps | `panels/Panel`, `PanelStore`, launcher | Pinned launcher shortcuts opening a panel in its own task, no address bar | S | **Done** (`liteapp/`; unit-tested link parsing; not verified on a device) |
| Content blocker | LittleProxy request path (`CaptureFiltersSource`) + `VictoriousWebViewClient.shouldInterceptRequest` | Host-suffix match against a small per-category sample list (`ContentBlocker`); blocked at the proxy when running, else in the WebView client; every block is recorded in `CaptureStore` with `CapturedExchange.blocked` set, never silently dropped | M | **Done** (sample blocklist only — not a maintained list; unit-tested matching; not verified on a device) |
| User scripts | none | `userscript/` — `WebViewCompat.addDocumentStartJavaScript` where supported (`WebViewFeature.DOCUMENT_START_SCRIPT`), `evaluateJavascript` in `onPageStarted` as a fallback elsewhere; scripts stored per-host like `PanelStore` | M | **Done** (unit-tested host matching + origin-rule generation; not verified on a device, and the fallback path's "runs later than real document-start" caveat is real, not hypothetical) |
| Reader mode | none | `reader/` — Mozilla's Readability.js (Apache-2.0, vendored verbatim at `assets/readability.js`) run via `evaluateJavascript`, output rendered back via `loadDataWithBaseURL` after this app's own sanitization pass | S–M | **Done** (unit-tested extraction-script building, double-encoded result decoding, and the sanitizer; not verified on a device or against a real page's Readability output) |
| Sandboxes | none | `sandbox/` — directly on `androidx.webkit`'s Profile API (`ProfileStore`/`WebViewCompat.setProfile`), gated by `WebViewFeature.MULTI_PROFILE`; no separate local store, a sandbox's existence is exactly what `ProfileStore` reports | M, highest risk | **Done** (unit-tested name validation only — the Profile API itself can't be exercised in a plain JUnit test; **completely unverified on a device**, more than any other feature in this set) |

Build order: night mode → Lite Apps → content blocker → user scripts → reader mode → sandboxes.

### 3b. URL tools + injection tools (not Hermit-reference — dev/pentest tools for this app's own recon role)

These extend the Burp-lite (modify-and-replay) and user-scripts tooling already in this
app, consistent with its stated purpose: a recon/inspection tool for the owner's own
fleet traffic. They're new "quick action" dialogs on `BrowserScreen`, not a persisted
feature like Scripts or a Hermit-parity item.

| Tool | Package | Implementation | Status |
|---|---|---|---|
| URL tools | `urltools/` | Pure percent-encode/decode and query-param parse/build (`UrlTools`); link shortening via TinyURL's keyless `api-create.php` endpoint (`LinkShortener`, one plain HTTP GET, no API key). `UrlToolsDialog` composable: Encode / Decode / Params / Shorten / Copy / Open. | **Done** (pure functions unit-tested; the actual network call in `LinkShortener.shorten` is not — no network in this test environment — and has not been exercised against the real endpoint) |
| URL changer | `urltools/` | `UrlTools.UrlParts`/`parseParts`/`buildFromParts` — a structured editor (scheme/host/port/path/query/fragment) in the same dialog, seeded from the current URL, "Rebuild URL" reassembles and feeds the existing Copy/Open actions | **Done** (unit-tested parse/build round trip, including the "don't invent a port that wasn't there" case) |
| Virus / malware scan | `urltools/` | `UrlScanner` — **both backends require the operator's own key**; URLhaus's Auth-Key (`UrlHausSettings`) and VirusTotal's API key (`VirusTotalSettings`), neither shipped, both persisted locally and sent only to their own API's auth header — submit + poll up to 3 times for VirusTotal since its analyses are async | **Done, and corrected after live testing** — see 4.8; unit-tested response-interpretation for both backends, including the two real auth-failure bodies captured live; the HTTP plumbing itself is still untested (no network in the Gradle sandbox) and neither backend has been called with a *valid* key |
| JS injection | `inject/` | `InjectionController.runJavaScript` — raw `evaluateJavascript(code, callback)` against the current page, one-off, not persisted (unlike `UserScript`, which reinstalls on every matching page load) | **Done** (trivially thin; nothing non-trivial to unit-test beyond the shared decode helper below) |
| HTML injection | `inject/` | `InjectionTools.buildHtmlInjectionScript` inserts raw HTML at the end of `<body>` via `insertAdjacentHTML`, **deliberately unsanitized** — the point of the tool is testing how the current page renders/sanitizes arbitrary markup, so stripping anything would defeat it (contrast with Reader mode's sanitizer, which exists for a different reason — see 4.6) | **Done** (unit-tested script building and the double-encoded callback decode) |

Both injection tools operate on `BrowserScreen`'s live `WebView` directly (via dialogs,
not a separate screen/destination) because `evaluateJavascript` needs the actual
`WebView` instance — there was no reason to route this through `MainActivity`'s
destination graph the way Scripts/Network do.

### 3c. System-bar icon color (not a Hermit feature or a new screen — a theme-consistency fix)

`VictoriousBrowserTheme` (`ui/theme/Theme.kt`) has always been a single, hardcoded
`darkColorScheme` — there is no light variant, and no toggle. The app's UI is
permanently dark by design. `MainActivity` and `LiteAppActivity` both called the
bare `enableEdgeToEdge()`, whose default status/navigation-bar icon color follows
the *device's* system day/night setting, not this app's actual (always-dark)
content — so on a phone set to system Light mode, the bars would render dark icons
over our charcoal background. `ui/theme/EdgeToEdge.kt`'s `enableAlwaysDarkEdgeToEdge()`
forces light (white) system-bar icons unconditionally, called from both activities
in place of the bare call. **Done** (compiles; the actual rendering is unverified — no
device or emulator in this build environment, so this is reasoned from
`androidx.activity`'s documented `SystemBarStyle` semantics, not observed).

### 3d. WireGuard + Headscale settings (operator-requested; NOT Hermit-reference)

Requested directly as "built-in advanced WireGuard and Headscale settings." The user
was asked first whether this meant a WireGuard VPN client (which needs `VpnService`
and conflicts with this app's documented VPN-slot-reserved-for-Mesh design) or a
keyless Headscale API settings screen (no VPN involved) — and answered: both.

| Tool | Package | Implementation | Status |
|---|---|---|---|
| WireGuard VPN client | `wireguard/` | `com.wireguard.android:tunnel` (Apache-2.0; see NOTICE) — the official embeddable engine (`GoBackend`, JNI-backed `wireguard-go`), not a hand-rolled reimplementation of the protocol. `WireGuardConfigBuilder` turns structured fields into wg-quick text and parses it with the library's own `Config.parse`. `WireGuardTunnelManager` implements `Tunnel` as a singleton and wraps `GoBackend.setState`. `WireGuardScreen` manages named profiles and the standard Android VPN-consent flow (`VpnService.prepare` + `ActivityResultContracts.StartActivityForResult`). | **Done — see 4.10 for the VPN-slot conflict.** Unit-tested unusually strongly for this app: `WireGuardConfigBuilderTest` runs against the **real** `com.wireguard.config.Config` parser (that package has no Android or native-code dependency, confirmed by decompiling the AAR, so it runs in a plain JUnit test) — a malformed key/CIDR/endpoint is rejected by the actual library, not a guess about it. `GoBackend`'s own native/VpnService path is NOT exercised — no device or emulator in this session. |
| Headscale API settings | `headscale/` | `HeadscaleApiClient` — plain `HttpURLConnection` calls to a self-hosted Headscale server's `/api/v1` endpoints (list nodes, list/create pre-auth keys, expire a node), `Authorization: Bearer <key>`. No VPN; doesn't touch `VpnService`. | **Done, but the one API client in this app NOT verified against anything live** — Headscale is self-hosted, so (unlike URLhaus/VirusTotal, which this guide corrected after real `curl` testing — section 4.8) there was no server to test against. Request/response shapes are from Headscale's published docs and training knowledge; unit-tested only against hand-written JSON built from that documentation, explicitly flagged in the code as unverified and version-drift-prone (Headscale's `user` field has changed shape across releases). |

Reachable from the launcher's **WireGuard** / **Headscale** buttons (new destinations
in `MainActivity`, next to Sandboxes).

## 4. Implementation decisions

1. Hermit is a spec only; no Hermit code or assets are reused. [Certain]
2. Blocking in the proxy must not hide traffic from inspection history — blocked requests are recorded as blocked entries. **Implemented**: `CaptureFiltersSource` records before returning the blocked response; the WebView fallback records before returning an empty `WebResourceResponse`.
3. Sandboxes depend on device WebView version. [Likely] Older devices lack Profile API support; the feature must feature-detect. **Implemented**: every `SandboxManager` entry point checks `WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)` first and falls back to the single default (unsandboxed) profile — never crashes, never claims isolation it can't provide. [Guessing] I have not run this against a real device's WebView; `assembleDebug`/unit tests only prove the code compiles and `SandboxName`'s pure validation logic is correct, not that `ProfileStore`/`WebViewCompat.setProfile` behave as their (decompiled, not hand-verified-at-runtime) signatures suggest.
4. New persistent settings follow the existing `SharedPreferences + JSON` pattern (`PanelStore`) until a Room migration is justified.
5. Reader mode loads Readability's extracted HTML back into a JS-enabled WebView with this app's cookies attached. **Implemented**: `ReaderMode.sanitizeContentHtml` strips `<script>`/`<style>`/`<iframe>`/`<object>`/`<embed>`, `on*` handlers, and `javascript:` URLs before rendering — a regex pass, not a full sanitizer; harden with something like DOMPurify if this is ever exposed to less-trusted content than "pages the operator chose to browse to."
6. HTML injection does the opposite of Reader mode on purpose: Reader mode sanitizes because it loads *someone else's* page content back into a cookie-bearing WebView the operator didn't ask to see raw; the injection tool renders *the operator's own typed input*, on a page they're already looking at, specifically to see how it's handled unsanitized. Conflating these two would break the injection tool's purpose.
7. Link shortening uses a public, keyless third-party endpoint (TinyURL). [Guessing] That endpoint could change its response format or go away; `LinkShortener.shorten` treats any non-200, blank, or `"Error..."`-prefixed body as a failure rather than guessing, so a format change fails loud (a `Shorten failed: …` message) instead of silently returning garbage.
8. **Corrected, not just designed, after live testing.** The original version of this feature assumed URLhaus was "free and keyless" — true of that service once, not true as of this session. With real network access (granted this session for this specific purpose) I `curl`ed both `urlhaus-api.abuse.ch` and `www.virustotal.com` directly: URLhaus returns HTTP 401 `{"error":"Unauthorized"}` with no `Auth-Key` header, and HTTP 403 `{"query_status":"unknown_auth_key"}` with a wrong one; VirusTotal returns HTTP 401 `{"error":{"code":"AuthenticationRequiredError",...}}` without `x-apikey`. Both backends now require the operator's own key (`UrlHausSettings`, `VirusTotalSettings` — free accounts at auth.abuse.ch and virustotal.com respectively, neither of which I can create on the operator's behalf), stored the same way `BridgeSettings`/`NightModeSettings` store their state (SharedPreferences, this device only), each sent only to its own API's auth header. This is the one place in this guide where "verified" means something stronger than "compiles": the error-handling branches in `interpretUrlHausJson` are built from real response bodies, not from documentation or memory.
9. A URLhaus miss is reported as "not listed", not "safe" — `UrlScanner.ScanVerdict.malicious = false` means absence from one database, and the UI text for that result says so rather than implying a clean bill of health.
10. **WireGuard conflicts with this app's own documented architecture, and was built anyway because the operator explicitly chose that after being told so.** Every other networking feature in this app (`LocalProxyService`'s README, `WireGuardTunnelManager`'s own doc) exists specifically because "the device's single VPN/tun slot is reserved for a separate mesh-networking app" (Victorious Mesh) — and `com.wireguard.android:tunnel`'s `GoBackend` is, necessarily, a `VpnService`. Android allows exactly one active VPN system-wide; connecting this app's tunnel will tear down Victorious Mesh's if it's running, and vice versa, with no coordination possible from either app alone. `WireGuardScreen` says this plainly before Connect. This decision is not mine to walk back silently if it turns out to be a problem in practice — it was an explicit choice, made with the trade-off stated up front.
11. WireGuard private keys are stored in plain SharedPreferences (`WireGuardProfileStore`) — the same mechanism, and the same honesty, as this app's other secrets, but a private key is categorically more sensitive (it's the device's identity on the mesh, not just an API credential). Flagged in the store's own doc as a candidate for `EncryptedSharedPreferences` if the threat model ever calls for it.
12. Disconnect (`WireGuardTunnelManager.disconnect`) passes `null` as the `Config` argument to `GoBackend.setState` — verified by decompiling `GoBackend.class`'s bytecode, not assumed: the library's own public `setState` method hardcodes `null` internally on every DOWN transition regardless of what's passed, so this isn't a risky guess about undocumented behavior.

## 5. Maintenance procedures

- Lite Apps: a shortcut stores only the panel id (`victorious-lite://panel/<id>`); the URL is resolved from `PanelStore` at launch, so editing a panel updates its shortcut and deleting it makes the shortcut show "Lite App no longer exists". `LiteAppActivity` is non-exported. Pinned shortcuts are not removed automatically when a panel is deleted.

- Reader mode: the "Reader" chip resets to off whenever the WebView navigates to a URL different from the one it was showing when reader mode was entered (e.g. a link tapped inside the rendered article) — see the comment at that check in `BrowserScreen.kt`.

- Sandboxes: a sandbox is not persisted by this app — `SandboxManager.list()` reads straight from `androidx.webkit.ProfileStore`. Deleting one that a live WebView is still assigned to fails (returns `false`, shown as a toast); there's no way from this UI to see which sandboxes are "in use" versus just idle. Lite Apps are not wired to sandboxes — a Lite App always uses the default profile.

- URL tools / injection tools: both dialogs hold their own local state (`BrowserScreen`'s `showUrlTools`/`showInject`) and close on dismiss without persisting anything except the VirusTotal API key — closing and reopening either dialog starts blank otherwise. Neither is available in Lite App mode (gated the same way as Scripts/Network).

- Virus scan: VirusTotal's analysis is asynchronous — `checkVirusTotal` polls up to 3 times (2s then 3s gaps) before giving up and reporting "still queued" rather than blocking indefinitely. A scan that reports "still queued" is not a failure; re-running Scan a bit later often resolves it. URLhaus has no such delay (synchronous lookup). Both backends need their own key entered and saved in the dialog before Scan does anything useful — without one, that backend's line in the result just says "no key set, skipped".

- WireGuard: only one tunnel/profile can be connected at a time (`WireGuardTunnelManager` is a singleton `Tunnel` named "victorious"); connecting a second profile while one is up is a reconnect, not a parallel tunnel. Editing a profile that's currently connected doesn't live-reconfigure the tunnel — disconnect and reconnect. See implementation decisions 10–12 for the VPN-slot conflict, key storage, and the verified disconnect-passes-null behavior.

- Headscale: the "user" field's expected shape (username vs. numeric ID) depends on your server's Headscale version — if `createPreAuthKey` fails, that's the first thing to check against your server's actual API docs, not assume this client is right.

- Build: `./gradlew assembleDebug`; unit tests: `./gradlew testDebugUnitTest` (needs `ANDROID_HOME` pointing at an SDK with platform 35; 90 tests pass).
- Any new third-party library or endpoint: add an entry to `NOTICE` (library) or note the dependency here (hosted endpoint, e.g. TinyURL) in the same commit.
- Update this guide's status table and tally in the same commit as each feature lands.
- Bridge protocol changes (new `browser.*` ops) require updating the README operations table and `BridgeCodecTest`.

## 6. Remaining work

All six Hermit-reference features, URL/injection tools, the URL changer, virus
scanning, the always-dark system-bar fix, and WireGuard/Headscale settings are
built. What's genuinely left is on-device verification for everything — and for
WireGuard specifically, that means the one thing this session categorically could
not test: whether the tunnel actually connects on a real device with a real VPN
consent prompt. For virus scan, a run with an actual valid key for each backend.
Night mode follow-ups: optional per-panel override, bridge op.

## 7. Future improvements

- Bridge ops for new features (e.g. toggle blocker, list sandboxes).
- Per-panel night mode / script / blocker overrides.
- User scripts: document-start injection is installed once per WebView instance at browser-screen creation, so a script added or edited while that screen is already open doesn't take effect until the WebView is recreated (navigating back to the launcher and reopening the panel/browser). The fallback path (`onPageStarted`) always reads the live store, so it's unaffected.
- Blocklist update mechanism (swap `ContentBlocker`'s sample host sets for a maintained subscription, e.g. EasyList-format) — the current list is illustrative only. Category selection UI is done; the update mechanism is not.
- Room-backed stores if data volume grows.
- Reader mode's HTML sanitizer is a regex pass, not a full parser (see implementation decision 5); replace with DOMPurify or similar if the threat model ever widens beyond "pages the operator chose to browse to."
- Sandboxes: Lite App / panel integration (open a given panel inside a chosen sandbox), an "in use" indicator before delete, and bridge ops (`browser.sandbox.*`) to list/open sandboxes from the fleet controller.
- URL tools: a self-hosted/alternate shortener option, since TinyURL's keyless endpoint has no uptime guarantee this app controls.
- Injection tools: no history of what was run/injected (by design — these are one-off and not meant to be a second capture store); revisit if that proves useful.
- Virus scan: no scan history either, same reasoning. Could add a third backend (e.g. Google Safe Browsing, also requires its own API key) behind the same "only call it if a key is set" pattern. A run with a real, valid key for each backend (never done this session) would confirm the "ok"/completed-analysis success paths, which are currently only unit-tested against hand-written JSON, not a captured real response.
- A real light theme / theme toggle, if that's ever actually wanted — the app is currently permanently dark by design (no light `MaterialTheme`, no preference), and the system-bar fix in 3c assumes that stays true. If a light mode is ever added, `enableAlwaysDarkEdgeToEdge` needs to become theme-aware instead of unconditional.
- WireGuard: per-app split tunneling (`Interface.Builder.includeApplication`/`excludeApplication` are already in the library's API, just not wired into `WireGuardScreen`), import a raw wg-quick file instead of only structured fields, statistics (`Backend.getStatistics`), a bridge op to connect/disconnect from the fleet controller. Coordinating with the Victorious Mesh app so the two don't fight over the VPN slot would need changes on that app's side too — out of reach from here.
- Headscale: user/ACL-policy management, routes, expiring a pre-auth key (currently only node-expire is wired), and (the real priority) confirming the request/response shapes against an actual running server, since none was available this session.
- **On-device verification pass across every feature in this guide** — this is the actual next step, not a new feature.

## 8. Artifacts generated

- `docs/VICTORIOUS_FRAMEWORK_GUIDE.md` (this file)
- `NightMode.kt`, `NightModeTest.kt`
- `liteapp/LiteAppLinks.kt`, `LiteAppShortcuts.kt`, `LiteAppActivity.kt`, `LiteAppLinksTest.kt`
- `inspect/ContentBlocker.kt`, `ContentBlockerTest.kt`
- `userscript/UserScript.kt`, `UserScriptStore.kt`, `UserScriptInjector.kt`, `UserScriptsScreen.kt`, `UserScriptMatcherTest.kt`, `UserScriptInjectorTest.kt`
- `reader/ReaderMode.kt`, `ReaderModeController.kt`, `ReaderModeTest.kt`, `assets/readability.js`
- `sandbox/Sandbox.kt` (`SandboxName`), `SandboxManager.kt`, `SandboxesScreen.kt`, `SandboxNameTest.kt`
- `urltools/UrlTools.kt`, `LinkShortener.kt`, `UrlToolsDialog.kt`, `UrlToolsTest.kt`, `LinkShortenerTest.kt`
- `urltools/UrlScanner.kt`, `VirusTotalSettings.kt`, `UrlHausSettings.kt`, `UrlScannerTest.kt`
- `inject/InjectionTools.kt`, `InjectionController.kt`, `InjectionDialog.kt`, `InjectionToolsTest.kt`
- `ui/theme/EdgeToEdge.kt`
- `wireguard/WireGuardConfigBuilder.kt`, `WireGuardProfileStore.kt`, `WireGuardTunnelManager.kt`, `WireGuardScreen.kt`, `WireGuardConfigBuilderTest.kt`
- `headscale/HeadscaleSettings.kt`, `HeadscaleApiClient.kt`, `HeadscaleScreen.kt`, `HeadscaleApiClientTest.kt`

## 9. Bug-testing pass + 0.2.0 release (2026-10-24)

[Certain] Found by reading `bridge/BridgeDispatcher.kt` and `certmgr/CertScreen.kt` together, not by a report: `proxy.status`'s `ca_installed` field was hardcoded `true` unconditionally — there was no check anywhere in the codebase for whether the active inspection root is actually one the browser's own WebView trusts.

The real defect this was hiding: `network_security_config.xml`'s trust-anchor is a compiled resource (`@raw/inspect_root`), fixed to the ORIGINAL baked-in root at build time and not swappable at runtime. `certmgr/CertScreen.kt`'s "Regenerate root" action (`LocalCaProvider.persistRegenerated`) writes a brand-new root to `filesDir`, and `LocalProxyService` prefers that regenerated root for signing intercepted leaf certs on next proxy start — but the WebView still only trusts the old baked root, so **every HTTPS page fails to load through the proxy** after regenerating, until the user taps "Revert to baked". The on-screen warnings (both the inline card and the confirm dialog) only mentioned previously-exported/installed CAs breaking — not that the browser breaks its own interception. A fleet controller polling `proxy.status` over the bridge would be told the CA is fine the whole time.

**Fixed:**
- `BridgeDispatcher.proxyStatus`: `ca_installed` now reports `!LocalCaProvider.hasRegenerated(context)` — true only when the active signing root is the one the WebView's static trust-anchor actually matches.
- `CertScreen.kt`: both warning strings (inline card + confirm dialog) now say plainly that regenerating also breaks this browser's own HTTPS interception until the new root is installed or reverted.

[Guessing] Not covered by a new unit test: `BridgeDispatcher` takes a real `android.content.Context` (SharedPreferences, Intent, Uri) and this project has no Robolectric dependency, so — consistent with every other `Context`-coupled class here (`CallerVerifier`, `LocalProxyService`, etc.) — it isn't unit-testable in plain JUnit. The fix is a one-line boolean flip calling an already-tested-by-nothing-else pure function (`LocalCaProvider.hasRegenerated`); verifying it end-to-end needs a device or Robolectric, same gap as everything else in §1's "on-device verification outstanding" row.

[Certain] `versionCode`/`versionName` bumped 1/`0.1.0` → 2/`0.2.0` (first bump since this project's inception — six feature PRs had landed since 0.1.0 without one). No other behavior changed in this pass; this was a bug-fix + version-bump push, not a new feature.

[Likely] This session's build environment had a flaky egress proxy (same request to the same real, existing Maven artifact alternated between a correct 200 and a bogus 404 roughly half the time) that made Gradle dependency resolution unreliable; this is environment-specific to this sandbox and not expected to affect GitHub Actions CI, which has normal network access. Worked around by looping `./gradlew test` until every dependency landed in the local cache, not by changing any repository configuration in the committed project.
