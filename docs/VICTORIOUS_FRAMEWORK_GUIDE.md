# Victorious Framework — Victorious Browser Living Guide

Last updated: 2026-10-19 (sandboxes — Hermit-reference feature set complete). This guide is updated whenever project state changes.
Confidence labels: **[Certain]** known/verified, **[Likely]** strong inference, **[Guessing]** assumption.

## 1. Current project status

| Area | Status |
|---|---|
| Shell engine (system WebView in Compose) | Finished |
| Proxy / MITM library (LittleProxy + BouncyCastle local CA) | Finished |
| Fleet-panel launcher | Finished |
| Fleet control bridge (Messenger IPC) | Finished |
| Proxy modify-and-replay | Finished |
| **Hermit-reference feature set** (this guide's focus) | **Finished — all 6 features landed on this branch** |

Tally: Finished 6 (5 pre-existing + the Hermit-reference set, all 6 of its features done) · In Progress 0 · Deferred 0 · Abandoned 0.
[Certain] All six compile, `assembleDebug` succeeds and `testDebugUnitTest` passes (55 tests). [Guessing] On-device visual/behavioral verification is still outstanding for every one of them — see each row in section 3 for what specifically hasn't been checked on a device.
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

| Hermit feature | Existing hook in this repo | Planned implementation | Effort | Status |
|---|---|---|---|---|
| Night mode | `browser/NightMode.kt`, `BrowserScreen.kt` | Algorithmic darkening, falling back to force-dark; persisted "Night" chip in the browser chrome. CSS injection not done. | S | **Done** (unit-tested strategy selection; not verified on a device) |
| Lite Apps | `panels/Panel`, `PanelStore`, launcher | Pinned launcher shortcuts opening a panel in its own task, no address bar | S | **Done** (`liteapp/`; unit-tested link parsing; not verified on a device) |
| Content blocker | LittleProxy request path (`CaptureFiltersSource`) + `VictoriousWebViewClient.shouldInterceptRequest` | Host-suffix match against a small per-category sample list (`ContentBlocker`); blocked at the proxy when running, else in the WebView client; every block is recorded in `CaptureStore` with `CapturedExchange.blocked` set, never silently dropped | M | **Done** (sample blocklist only — not a maintained list; unit-tested matching; not verified on a device) |
| User scripts | none | `userscript/` — `WebViewCompat.addDocumentStartJavaScript` where supported (`WebViewFeature.DOCUMENT_START_SCRIPT`), `evaluateJavascript` in `onPageStarted` as a fallback elsewhere; scripts stored per-host like `PanelStore` | M | **Done** (unit-tested host matching + origin-rule generation; not verified on a device, and the fallback path's "runs later than real document-start" caveat is real, not hypothetical) |
| Reader mode | none | `reader/` — Mozilla's Readability.js (Apache-2.0, vendored verbatim at `assets/readability.js`) run via `evaluateJavascript`, output rendered back via `loadDataWithBaseURL` after this app's own sanitization pass | S–M | **Done** (unit-tested extraction-script building, double-encoded result decoding, and the sanitizer; not verified on a device or against a real page's Readability output) |
| Sandboxes | none | `sandbox/` — directly on `androidx.webkit`'s Profile API (`ProfileStore`/`WebViewCompat.setProfile`), gated by `WebViewFeature.MULTI_PROFILE`; no separate local store, a sandbox's existence is exactly what `ProfileStore` reports | M, highest risk | **Done** (unit-tested name validation only — the Profile API itself can't be exercised in a plain JUnit test; **completely unverified on a device**, more than any other feature in this set) |

Build order: night mode → Lite Apps → content blocker → user scripts → reader mode → sandboxes.

## 4. Implementation decisions

1. Hermit is a spec only; no Hermit code or assets are reused. [Certain]
2. Blocking in the proxy must not hide traffic from inspection history — blocked requests are recorded as blocked entries. **Implemented**: `CaptureFiltersSource` records before returning the blocked response; the WebView fallback records before returning an empty `WebResourceResponse`.
3. Sandboxes depend on device WebView version. [Likely] Older devices lack Profile API support; the feature must feature-detect. **Implemented**: every `SandboxManager` entry point checks `WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)` first and falls back to the single default (unsandboxed) profile — never crashes, never claims isolation it can't provide. [Guessing] I have not run this against a real device's WebView; `assembleDebug`/unit tests only prove the code compiles and `SandboxName`'s pure validation logic is correct, not that `ProfileStore`/`WebViewCompat.setProfile` behave as their (decompiled, not hand-verified-at-runtime) signatures suggest.
4. New persistent settings follow the existing `SharedPreferences + JSON` pattern (`PanelStore`) until a Room migration is justified.
5. Reader mode loads Readability's extracted HTML back into a JS-enabled WebView with this app's cookies attached. **Implemented**: `ReaderMode.sanitizeContentHtml` strips `<script>`/`<style>`/`<iframe>`/`<object>`/`<embed>`, `on*` handlers, and `javascript:` URLs before rendering — a regex pass, not a full sanitizer; harden with something like DOMPurify if this is ever exposed to less-trusted content than "pages the operator chose to browse to."

## 5. Maintenance procedures

- Lite Apps: a shortcut stores only the panel id (`victorious-lite://panel/<id>`); the URL is resolved from `PanelStore` at launch, so editing a panel updates its shortcut and deleting it makes the shortcut show "Lite App no longer exists". `LiteAppActivity` is non-exported. Pinned shortcuts are not removed automatically when a panel is deleted.

- Reader mode: the "Reader" chip resets to off whenever the WebView navigates to a URL different from the one it was showing when reader mode was entered (e.g. a link tapped inside the rendered article) — see the comment at that check in `BrowserScreen.kt`.

- Sandboxes: a sandbox is not persisted by this app — `SandboxManager.list()` reads straight from `androidx.webkit.ProfileStore`. Deleting one that a live WebView is still assigned to fails (returns `false`, shown as a toast); there's no way from this UI to see which sandboxes are "in use" versus just idle. Lite Apps are not wired to sandboxes — a Lite App always uses the default profile.

- Build: `./gradlew assembleDebug`; unit tests: `./gradlew testDebugUnitTest` (needs `ANDROID_HOME` pointing at an SDK with platform 35; 55 tests pass).
- Any new third-party library: add an entry to `NOTICE` with license and URL in the same commit.
- Update this guide's status table and tally in the same commit as each feature lands.
- Bridge protocol changes (new `browser.*` ops) require updating the README operations table and `BridgeCodecTest`.

## 6. Remaining work

All six Hermit-reference features are built. What's genuinely left is verification, not
construction: none of the six has been run on a real device or a real page, only
compiled and unit-tested. Night mode follow-ups: optional per-panel override, bridge op.

## 7. Future improvements

- Bridge ops for new features (e.g. toggle blocker, list sandboxes).
- Per-panel night mode / script / blocker overrides.
- User scripts: document-start injection is installed once per WebView instance at browser-screen creation, so a script added or edited while that screen is already open doesn't take effect until the WebView is recreated (navigating back to the launcher and reopening the panel/browser). The fallback path (`onPageStarted`) always reads the live store, so it's unaffected.
- Blocklist update mechanism (swap `ContentBlocker`'s sample host sets for a maintained subscription, e.g. EasyList-format) — the current list is illustrative only. Category selection UI is done; the update mechanism is not.
- Room-backed stores if data volume grows.
- Reader mode's HTML sanitizer is a regex pass, not a full parser (see implementation decision 5); replace with DOMPurify or similar if the threat model ever widens beyond "pages the operator chose to browse to."
- Sandboxes: Lite App / panel integration (open a given panel inside a chosen sandbox), an "in use" indicator before delete, and bridge ops (`browser.sandbox.*`) to list/open sandboxes from the fleet controller.
- **On-device verification pass across all six features** — this is the actual next step, not a seventh feature.

## 8. Artifacts generated

- `docs/VICTORIOUS_FRAMEWORK_GUIDE.md` (this file)
- `NightMode.kt`, `NightModeTest.kt`
- `liteapp/LiteAppLinks.kt`, `LiteAppShortcuts.kt`, `LiteAppActivity.kt`, `LiteAppLinksTest.kt`
- `inspect/ContentBlocker.kt`, `ContentBlockerTest.kt`
- `userscript/UserScript.kt`, `UserScriptStore.kt`, `UserScriptInjector.kt`, `UserScriptsScreen.kt`, `UserScriptMatcherTest.kt`, `UserScriptInjectorTest.kt`
- `reader/ReaderMode.kt`, `ReaderModeController.kt`, `ReaderModeTest.kt`, `assets/readability.js`
- `sandbox/Sandbox.kt` (`SandboxName`), `SandboxManager.kt`, `SandboxesScreen.kt`, `SandboxNameTest.kt`
