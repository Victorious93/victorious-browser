# Victorious Framework — Victorious Browser Living Guide

Last updated: 2026-10-19 (URL tools + injection tools). This guide is updated whenever project state changes.
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
| **URL tools + injection tools** (this guide's current focus; not from Hermit) | **In progress — landed on this branch** |

Tally: Finished 7 (5 original + the completed Hermit-reference set) · In Progress 1 (URL/injection tools) · Deferred 0 · Abandoned 0.
[Certain] URL tools and injection tools compile, `assembleDebug` succeeds and `testDebugUnitTest` passes (68 tests). [Guessing] On-device/behavioral verification is still outstanding for every feature in this guide — see each item for what specifically hasn't been checked on a device.
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
| JS injection | `inject/` | `InjectionController.runJavaScript` — raw `evaluateJavascript(code, callback)` against the current page, one-off, not persisted (unlike `UserScript`, which reinstalls on every matching page load) | **Done** (trivially thin; nothing non-trivial to unit-test beyond the shared decode helper below) |
| HTML injection | `inject/` | `InjectionTools.buildHtmlInjectionScript` inserts raw HTML at the end of `<body>` via `insertAdjacentHTML`, **deliberately unsanitized** — the point of the tool is testing how the current page renders/sanitizes arbitrary markup, so stripping anything would defeat it (contrast with Reader mode's sanitizer, which exists for a different reason — see 4.6) | **Done** (unit-tested script building and the double-encoded callback decode) |

Both injection tools operate on `BrowserScreen`'s live `WebView` directly (via dialogs,
not a separate screen/destination) because `evaluateJavascript` needs the actual
`WebView` instance — there was no reason to route this through `MainActivity`'s
destination graph the way Scripts/Network do.

## 4. Implementation decisions

1. Hermit is a spec only; no Hermit code or assets are reused. [Certain]
2. Blocking in the proxy must not hide traffic from inspection history — blocked requests are recorded as blocked entries. **Implemented**: `CaptureFiltersSource` records before returning the blocked response; the WebView fallback records before returning an empty `WebResourceResponse`.
3. Sandboxes depend on device WebView version. [Likely] Older devices lack Profile API support; the feature must feature-detect. **Implemented**: every `SandboxManager` entry point checks `WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)` first and falls back to the single default (unsandboxed) profile — never crashes, never claims isolation it can't provide. [Guessing] I have not run this against a real device's WebView; `assembleDebug`/unit tests only prove the code compiles and `SandboxName`'s pure validation logic is correct, not that `ProfileStore`/`WebViewCompat.setProfile` behave as their (decompiled, not hand-verified-at-runtime) signatures suggest.
4. New persistent settings follow the existing `SharedPreferences + JSON` pattern (`PanelStore`) until a Room migration is justified.
5. Reader mode loads Readability's extracted HTML back into a JS-enabled WebView with this app's cookies attached. **Implemented**: `ReaderMode.sanitizeContentHtml` strips `<script>`/`<style>`/`<iframe>`/`<object>`/`<embed>`, `on*` handlers, and `javascript:` URLs before rendering — a regex pass, not a full sanitizer; harden with something like DOMPurify if this is ever exposed to less-trusted content than "pages the operator chose to browse to."
6. HTML injection does the opposite of Reader mode on purpose: Reader mode sanitizes because it loads *someone else's* page content back into a cookie-bearing WebView the operator didn't ask to see raw; the injection tool renders *the operator's own typed input*, on a page they're already looking at, specifically to see how it's handled unsanitized. Conflating these two would break the injection tool's purpose.
7. Link shortening uses a public, keyless third-party endpoint (TinyURL). [Guessing] That endpoint could change its response format or go away; `LinkShortener.shorten` treats any non-200, blank, or `"Error..."`-prefixed body as a failure rather than guessing, so a format change fails loud (a `Shorten failed: …` message) instead of silently returning garbage.

## 5. Maintenance procedures

- Lite Apps: a shortcut stores only the panel id (`victorious-lite://panel/<id>`); the URL is resolved from `PanelStore` at launch, so editing a panel updates its shortcut and deleting it makes the shortcut show "Lite App no longer exists". `LiteAppActivity` is non-exported. Pinned shortcuts are not removed automatically when a panel is deleted.

- Reader mode: the "Reader" chip resets to off whenever the WebView navigates to a URL different from the one it was showing when reader mode was entered (e.g. a link tapped inside the rendered article) — see the comment at that check in `BrowserScreen.kt`.

- Sandboxes: a sandbox is not persisted by this app — `SandboxManager.list()` reads straight from `androidx.webkit.ProfileStore`. Deleting one that a live WebView is still assigned to fails (returns `false`, shown as a toast); there's no way from this UI to see which sandboxes are "in use" versus just idle. Lite Apps are not wired to sandboxes — a Lite App always uses the default profile.

- URL tools / injection tools: both dialogs hold their own local state (`BrowserScreen`'s `showUrlTools`/`showInject`) and close on dismiss without persisting anything — closing and reopening either dialog starts blank. Neither is available in Lite App mode (gated the same way as Scripts/Network).

- Build: `./gradlew assembleDebug`; unit tests: `./gradlew testDebugUnitTest` (needs `ANDROID_HOME` pointing at an SDK with platform 35; 68 tests pass).
- Any new third-party library or endpoint: add an entry to `NOTICE` (library) or note the dependency here (hosted endpoint, e.g. TinyURL) in the same commit.
- Update this guide's status table and tally in the same commit as each feature lands.
- Bridge protocol changes (new `browser.*` ops) require updating the README operations table and `BridgeCodecTest`.

## 6. Remaining work

All six Hermit-reference features and the URL/injection tools are built. What's
genuinely left is verification, not construction: none of this guide's features has
been run on a real device or a real page, only compiled and unit-tested. Night mode
follow-ups: optional per-panel override, bridge op.

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
- `inject/InjectionTools.kt`, `InjectionController.kt`, `InjectionDialog.kt`, `InjectionToolsTest.kt`
