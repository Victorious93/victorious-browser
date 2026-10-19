# Victorious Framework — Victorious Browser Living Guide

Last updated: 2026-10-19 (night mode). This guide is updated whenever project state changes.
Confidence labels: **[Certain]** known/verified, **[Likely]** strong inference, **[Guessing]** assumption.

## 1. Current project status

| Area | Status |
|---|---|
| Shell engine (system WebView in Compose) | Finished |
| Proxy / MITM library (LittleProxy + BouncyCastle local CA) | Finished |
| Fleet-panel launcher | Finished |
| Fleet control bridge (Messenger IPC) | Finished |
| Proxy modify-and-replay | Finished |
| **Hermit-reference feature set** (this guide's focus) | **In progress — night mode landed on this branch; 5 features planned** |

Tally: Finished 5 · In Progress 1 (Hermit-reference set: 1 of 6 features done) · Deferred 0 · Abandoned 0.
[Certain] Night mode compiles and `testDebugUnitTest` passes (25 tests). [Guessing] On-device visual behavior is unverified; algorithmic darkening needs WebView 114+ and a dark host theme (`Theme.VictoriousBrowser` is dark).
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
| Lite Apps | `panels/Panel`, `PanelStore`, launcher | Pinned launcher shortcuts opening a panel in its own task, no address bar | S | Planned |
| Content blocker | LittleProxy request path | Blocklist match in proxy + `shouldInterceptRequest` fallback; **log blocked requests as "blocked" in `CaptureStore`** | M | Planned |
| User scripts | none | `WebViewCompat.addDocumentStartJavaScript`; per-site rules in a store modeled on `PanelStore` | M | Planned |
| Reader mode | none | Inject Mozilla Readability.js (Apache-2.0), render result | S–M | Planned |
| Sandboxes | none | WebView Profile API (separate cookie/storage jars); runtime `isFeatureSupported` check, degrade gracefully | M, highest risk | Planned |

Build order: night mode → Lite Apps → content blocker → user scripts → reader mode → sandboxes.

## 4. Implementation decisions

1. Hermit is a spec only; no Hermit code or assets are reused. [Certain]
2. Blocking in the proxy must not hide traffic from inspection history — blocked requests are recorded as blocked entries.
3. Sandboxes depend on device WebView version. [Likely] Older devices lack Profile API support; the feature must feature-detect.
4. New persistent settings follow the existing `SharedPreferences + JSON` pattern (`PanelStore`) until a Room migration is justified.

## 5. Maintenance procedures

- Build: `./gradlew assembleDebug`; unit tests: `./gradlew testDebugUnitTest` (needs `ANDROID_HOME` pointing at an SDK with platform 35; run for night mode, 25 tests pass).
- Any new third-party library: add an entry to `NOTICE` with license and URL in the same commit.
- Update this guide's status table and tally in the same commit as each feature lands.
- Bridge protocol changes (new `browser.*` ops) require updating the README operations table and `BridgeCodecTest`.

## 6. Remaining work

Lite Apps, content blocker, user scripts, reader mode, sandboxes (section 3). Night mode follow-ups: optional per-panel override, bridge op.

## 7. Future improvements

- Bridge ops for new features (e.g. toggle blocker, list sandboxes).
- Per-panel night mode / script / blocker overrides.
- Blocklist update mechanism and category selection UI.
- Room-backed stores if data volume grows.

## 8. Artifacts generated

- `docs/VICTORIOUS_FRAMEWORK_GUIDE.md` (this file)
- `NightMode.kt`, `NightModeTest.kt`
