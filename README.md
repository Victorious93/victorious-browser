# Victorious Browser

Fleet security / recon browser for the Victorious fleet.

Kotlin + Jetpack Compose Android app shell with a fixed debug keystore, dark brand
theme, a system WebView browsing surface, and an on-device HTTP(S) inspection layer
for the developer's own traffic.

## Status

- **Shell engine** — system WebView via `AndroidView` (landed)
- **Proxy / MITM library** — LittleProxy (localhost socket proxy) + BouncyCastle
  local CA (landed on `feature/intercept-proxy`)
- **Fleet-panel launcher** — home screen for the owner's own dashboards
  (landed on `feature/fleet-panel-launcher`)
- **Fleet control bridge** — bound Messenger IPC for the owner's controller
  (landed on `feature/fleet-bridge`)
- **Proxy modify-and-replay** — Burp-lite intercept / edit / replay
  (landed on `feature/proxy-modify-replay`)
- **Deep capture** — full req/resp bodies, cookies+attrs, JWT decode,
  searchable history (landed on `feature/deep-capture`)
- **Mesh reachability** — resolve & probe mesh-flagged fleet panels over
  Victorious Mesh before opening (landed on `feature/mesh-reachability`)
- **Multi-tab browsing + quick fleet-panel switching** — multiple WebView
  tabs (tab bar: new / close / switch, title + favicon-or-host) with each tab's
  live WebView instance preserved across switches, plus a bottom-sheet palette to
  jump to any configured fleet panel in the current or a new tab; mesh-flagged
  panels still pass the mesh-reachability check (landed on `feature/multi-tab`)
- **Recon Workbench** — offline encode/decode, hasher/identifier, URL tools
  (landed on `feature/recon-workbench`)
- **Google Dork Builder + OSINT Panel** — compose dork queries from operators /
  templates and open them in a WebView tab against Google/DuckDuckGo/Bing; pivot
  links for public OSINT sources (crt.sh, DNS, reverse-IP, Wayback, Shodan,
  Censys, WHOIS, VirusTotal, GitHub/Google dorks, username/email lookups); passive
  tech fingerprint over already-captured responses (landed on `feature/osint-dork`)
- **Scanner (Burp-lite web-testing toolset)** — Sitemap/crawler (site tree from
  captured history + bounded, scope-gated same-origin crawl), opt-in active
  scanner (reflected XSS, error/boolean/time-based SQLi, SSRF indicators per
  insertion point), header fuzzer, CL.TE/TE.CL request-smuggling heuristic probe
  (detection only), and a bring-your-own OOB collaborator client (no bundled
  server). All intrusive actions are **off by default**, require an explicit
  in-scope target host and an explicit Run action, and never run on browsing
  traffic. Pure detection logic is unit-tested. (landed on `feature/burp-scanner`)

## Fleet control bridge

First-party inter-app automation: a bound **Messenger** service so the owner's
OWN controller app (`ai.droidcommand.app`) can drive Victorious Browser on the
owner's own devices (EXTERNAL_NODE class).

### Protocol
- Intent action: `com.victorious.browser.action.BRIDGE`
- `Message.what` = `1` request / `2` response
- Wire: newline-delimited JSON, `BRIDGE_PROTOCOL_MAJOR = 1`
- Envelope: `BridgeRequest { id, op, params }` →
  `BridgeResponse { id, ok, result?, error? }` with `error = { category, message }`

### Security model
1. **Caller identity** via `Message.sendingUid` (not `Binder.getCallingUid()`):
   resolve packages for that uid and verify signing-cert SHA-256 against the
   pinned DEBUG + RELEASE fleet-provided controller cert SHA-256 for package
   `ai.droidcommand.app` only (no `ai.droidcommand.app.debug` pin — dropped by the fleet).
   RELEASE pin verifies the CALLER; it is not this app's own signing key
2. **External control** persisted switch — **OFF by default**; when off every
   op is rejected with `not_paired`
3. Envelope / parameter validity

Check order on each request: identity → external-control switch → envelope.

### Operations
| op | params | result |
|---|---|---|
| `browser.capabilities.get` | `{}` | `{ protocol_major, package, node_id, operations, proxy_supported }` |
| `browser.open_url` | `{ url }` | `{ status:"opened" }` |
| `browser.panel.list` | `{}` | `{ panels:[{id,name,url,kind}] }` |
| `browser.panel.open` | `{ panel_id }` | `{ status:"opened" }` |
| `browser.recon.summary` | `{ limit?, since? }` | `{ count, entries:[{method,url,status,host,ts}] }` |
| `browser.proxy.set` | `{ enabled:bool }` | fire-and-forget ack |
| `browser.proxy.status` | `{}` | `{ running, port, ca_installed }` |

Parameterless ops must receive exactly `{}` — non-empty params → `invalid_parameters`.

Error categories: `invalid_parameters`, `not_paired`, `not_found`, `proxy_error`, `internal`.

Node-id: generated/persisted on first run, matches `^browser-[0-9a-f]{32}$`.

In-app **Fleet control** settings screen (from the launcher **Control** button):
external-control switch, read-only node-id, pinned controller info.

## Fleet-panel launcher

The app starts on a **Fleet Panels** Compose home screen that lists the owner's
own locally-hosted / fleet web dashboards and opens them in the in-app WebView.

- Grid of panel cards: name, URL, kind tag (`LOCAL_HTTP`, `HEADSCALE_ADMIN`,
  `METASPLOIT_WEB`, `KYC_AUDITLAB`, `OTHER`), and a reachability indicator
  (lightweight TCP connect to host:port with a short timeout → up / down /
  unknown)
- Add / edit / delete panels from a simple form; persisted across launches via
  SharedPreferences + JSON (`PanelStore` — simple local store, not Room)
- Seeded once with editable DEFAULT panels (placeholder `127.0.0.1` URLs/ports —
  the owner configures real addresses; no credentials or private IPs invented):
  KYC-AuditLab, Headscale admin, Metasploit web console, Local HTTP tool template
- **Mesh reachability:** per-panel `reachOverMesh` flag; opening a mesh panel
  goes through host classification + tunnel-signal / TCP probe (see
  [Mesh reachability](#mesh-reachability) below). No VpnService in this app.
- Fleet bridge ops `browser.panel.list` / `browser.panel.open` already read/drive
  the store via the Messenger bridge (landed on `feature/fleet-bridge`)
- WebView opens when a panel is tapped; **Panels** returns to the launcher;
  Network / free Browser remain reachable from the chrome


## Mesh reachability

For fleet panels with **Reach over mesh** enabled, opening goes through a
best-effort resolution layer (`MeshResolution` + `MeshReachability`) instead of
jumping straight into the WebView:

1. **Classify** the panel URL host: loopback / direct / Tailscale CGNAT
   `100.64.0.0/10` / mesh IPv6 `fd7a:115c:a1e0::/48` / MagicDNS suffix
   (`.ts.net`, `.tailnet`, `.mesh.victorious`) / bare node name.
2. **Probe tunnel signals** (best-effort — no published Victorious Mesh API is
   used or invented): local `NetworkInterface` name prefixes
   (`tun`, `tailscale`, `wg`, …) and presence of a CGNAT / mesh IPv6 address;
   optional soft signal via `NetworkCapabilities.TRANSPORT_VPN`; package-presence
   hint for candidate Victorious Mesh package ids.
3. **Resolve** name-based hosts (`InetAddress` lookup), then **TCP connect** to
   host:port (same idea as the direct reachability checker).
4. **Decide**:
   - TCP connect succeeds → open in WebView
   - tunnel signals present, name resolves / IP literal, TCP fail → "mesh node unreachable"
   - tunnel signals present but name does not resolve → "unknown mesh host"
   - no tunnel signals + (name fails or TCP fails) → actionable **"Victorious Mesh
     tunnel appears down — bring it up in the Victorious Mesh app"**
   - unparseable host → "unknown mesh host"
5. The blocked dialog offers **Retry** and, when a candidate Victorious Mesh
   package is installed, **Open Victorious Mesh** (standard launcher intent only).

This browser does **not** hold the Android `VpnService` slot (reserved for
Victorious Mesh). Routing is provided by that app when its tunnel is up; this
layer only resolves names/addresses and reports clear UX states.

**Honest contract status:** no ContentProvider / Intent / Broadcast contract from
Victorious Mesh was confirmed at implementation time. Interface-name heuristics
are labelled `[Guessing]` in code; CGNAT/MagicDNS conventions `[Likely]`; TCP
probe + host classification `[Certain]`. Follow-up: coordinate with the
Victorious Mesh agent for a published status contract.

No new third-party dependencies.

## Lite Apps

Tap **Pin** on a panel card to pin it to the launcher as a Lite App: a chrome-less
single-panel browser (back / forward / reload / Night / Reader only) in its own task, so it
shows up as a separate app in Recents. Shortcuts carry only the panel id; the URL is
read from `PanelStore` at launch. `LiteAppActivity` is not exported.

## Browser shell

The browsing surface is a single system **WebView** hosted in Jetpack Compose via
`AndroidView` (no embedded Chromium / Gecko binary).

- Address bar + Go, Back, Forward, Reload, and a load-progress indicator
- Custom `WebViewClient` / `WebChromeClient` keep http(s) navigation in-app
- Recon-oriented defaults: JavaScript + DOM storage on, cookies via `CookieManager`,
  mixed content set to `MIXED_CONTENT_COMPATIBILITY_MODE` (noted in code)
- Optional Desktop / Mobile UA toggle
- **Night** chip: dark rendering for any site via `androidx.webkit` algorithmic darkening
  (falls back to force-dark on older WebViews); persisted across launches
- `androidx.webkit` `ProxyController` routes the WebView through the local
  inspection proxy when enabled (see below)

### Content blocker

Host-suffix blocking by category (adware & malware, coin miners, trackers, adult —
toggle each from **Network / History**). Matches at the local proxy when it's
running, and via `shouldInterceptRequest` on the WebView when it's not, so blocking
works either way. A blocked request is still recorded in capture history, marked
`BLOCKED`, never silently dropped — the blocker must not hide traffic from the
inspection tool it shares a code path with. The bundled host list (`ContentBlocker`)
is a small illustrative sample, not a maintained blocklist.

### User scripts

Per-site JavaScript, managed from the **Scripts** button in the browser chrome
(hidden in Lite App mode). Each script has a name, a host pattern (`example.com`
or `*.example.com`, subdomain-inclusive either way), and a body. Injected via
`WebViewCompat.addDocumentStartJavaScript` where the device's WebView supports it
(`WebViewFeature.DOCUMENT_START_SCRIPT`) — scoped to origin-match rules built from
the host pattern, so it runs before the page's own scripts. Falls back to
`evaluateJavascript` in `onPageStarted` on older WebViews; that path runs after the
page has already started, so a script relying on running first can be too late.
Document-start scripts are installed once when the browser screen's WebView is
created — a script added or edited while that screen stays open takes effect on
the next time it's recreated, not immediately.

### Reader mode

**Reader** chip in the browser chrome (available in Lite App mode too). Runs
[Mozilla's Readability.js](https://github.com/mozilla/readability) (Apache-2.0,
vendored verbatim at `assets/readability.js`) inside the page's own WebView via
`evaluateJavascript` against a cloned `document`, so the live page is untouched
even if extraction fails. The extracted HTML is passed through this app's own
sanitizer (`ReaderMode.sanitizeContentHtml` — strips `<script>`/`<style>`/
`<iframe>`/`<object>`/`<embed>`, inline event handlers, and `javascript:` URLs)
before being rendered back with `loadDataWithBaseURL`, because Readability
extracts content, it doesn't guarantee it's free of the source page's own script
tags, and this WebView still carries that page's cookies. If no article is found,
a toast says so and the chip doesn't flip. Tapping a link inside the rendered
article exits reader mode (it's a real navigation, not the article anymore).

### Sandboxes

**Sandboxes** button on the launcher opens a list of named sandboxes — isolated
containers for separating accounts, backed directly by `androidx.webkit`'s WebView
Profile API (`ProfileStore` / `WebViewCompat.setProfile`), gated by
`WebViewFeature.MULTI_PROFILE`. Each sandbox is its own Chromium profile: separate
cookies, storage, geolocation permissions, service workers. There's no separate
local store for sandboxes — a sandbox "exists" exactly when `ProfileStore` says so.
A profile is assigned to a WebView once, immediately after it's created and before
any navigation; it can't be reassigned mid-session, so sandboxes are chosen when
opening a browser tab, not toggled while browsing. Deleting a sandbox that's still
assigned to an open tab fails with a toast rather than succeeding destructively.
On a WebView that doesn't support multiple profiles, everything falls back to the
one shared default profile — no isolation, but no crash either. **This is the
least-verified feature in the app**: the Profile API's behavior here is read from
its decompiled method signatures, not exercised on a real device.

### URL tools

**URL** button in the browser chrome opens percent-encode/decode, a query-parameter
view, and link shortening (via TinyURL's keyless `api-create.php` endpoint — no
account, no key). Results can be copied or opened directly in the browser. This is
the one feature in the app that calls a public third-party HTTP endpoint rather than
only the owner's own fleet traffic; `LinkShortener` treats any non-200 response, a
blank body, or a body starting with `"Error"` as a failure rather than guessing.

Also in the same dialog:

- **URL changer** — a structured editor (scheme, host, port, path, query, fragment)
  seeded from the current URL. "Rebuild URL" reassembles the parts and feeds the
  same Copy/Open actions as the rest of the dialog. Round-trips cleanly: a URL with
  no port stays without one; a bare host is invalid (needs a scheme and host to
  parse at all).
- **Virus / malware scan** — **URLhaus** (abuse.ch) and **VirusTotal**, both
  checking the URL in the box above. **Both require your own free API key** —
  neither is keyless, confirmed by calling both APIs live without one: URLhaus
  answers HTTP 401, VirusTotal HTTP 401 with its own error shape, neither runs a
  scan. Get a URLhaus Auth-Key at [auth.abuse.ch](https://auth.abuse.ch/) and a
  VirusTotal key at [virustotal.com](https://www.virustotal.com/) (both free), paste
  each into its field, hit **Save key**, then **Scan URL**. A backend with no key
  saved is skipped and says so in the result rather than silently doing nothing. A
  URLhaus hit means the URL is specifically known-malicious; a miss means only "not
  in this database", not "safe" — the result text says so rather than implying a
  clean bill of health. VirusTotal analyses are asynchronous — the app submits the
  URL and polls up to three times (a few seconds apart) before reporting "still
  queued" rather than hanging; a queued result isn't a failure, just scan again
  shortly. Each key is saved locally on-device and sent only to its own API's auth
  header, nowhere else. **Neither backend has been scanned with a real, valid key**
  in this session — only the "no key" / "wrong key" error responses were confirmed
  live.

### Injection tools

**Inject** button in the browser chrome: run arbitrary JavaScript against the current
page (`evaluateJavascript`, one-off, not persisted — unlike the **Scripts** feature,
which reinstalls per matching host on every page load), or insert raw HTML at the end
of `<body>`. Both operate on the page currently open in this browser — the same page
the operator already navigated to, nothing more. HTML injection is **deliberately
not sanitized** (unlike Reader mode's output): the point of the tool is seeing how
the current page renders and sanitizes arbitrary markup, so stripping the input
first would defeat it. Meant for testing the owner's own pages' handling of
untrusted input, in the same spirit as the Burp-lite modify-and-replay tooling below.

## Network inspection

Developer HTTP(S) inspection for traffic from **this app's own in-app WebView**
while browsing the owner's own locally-hosted dashboards. Similar in spirit to
Charles Proxy / mitmproxy / Chrome DevTools' network panel, scoped to first-party
use on the owner's own devices.

### Localhost proxy (no VpnService)

- An ordinary socket proxy bound to `127.0.0.1:<port>` (default 8888)
- **Does not** use Android `VpnService` — the device's single VPN/tun slot is
  reserved for a separate mesh-networking app; the localhost-proxy approach is
  chosen precisely so it coexists with that VPN
- Core: [LittleProxy](https://github.com/LittleProxy/LittleProxy)
  (`xyz.rogfam:littleproxy`, Apache-2.0). On Android ART we force Netty's **NIO**
  transport via `TransportProtocol.TCP` (native epoll is unavailable and unused)

### App-only TLS trust

- A local root CA + per-host leaf certificates generated with **BouncyCastle**
  (`bcpkix` / `bcprov`, Bouncy Castle Licence ~MIT)
- The public root is packaged at `res/raw/inspect_root.pem` and trusted **only
  within this app** via `res/xml/network_security_config.xml`
- **No** system-wide / device-wide certificate installation (that needs root and
  is out of scope)
- The WebView is routed through the proxy via `androidx.webkit.ProxyController`

### Capture + helpers

- History of method, URL, host, status, timestamps, byte sizes, headers, and
  **request + response bodies** (aggregated by LittleProxy, capped by
  `CaptureConfig`, default 256 KiB, clampable 4 KiB–2 MiB)
- Bodies tagged with Content-Type; text shown as UTF-8, binary as a hex preview;
  truncation flagged when wire size exceeds the cap
- Persisted in an in-memory `CaptureStore` (`StateFlow`, capped) — a simple local
  store; Room was **not** introduced (history is session-scoped inspection data)
- Cookies: `Cookie` / `Set-Cookie` broken out into name / value / attributes
  (`Path`, `Domain`, `Secure`, `HttpOnly`, `SameSite`, …)
- JWT: detect bearer / JWT-looking tokens in `Authorization`, cookie values, and
  textual bodies; decode header + payload (base64url) and surface claims.
  **Signatures are never verified** — UI and export label results
  `decoded-not-verified`
- Searchable / filterable history (`HistoryFilter`): free-text search across URL,
  headers, body text, cookies, and decoded JWT claims; filters for method,
  status, content-type, host
- Export captured entries (incl. body base64 + cookies + JWTs) to JSON under the
  app's external files (`CaptureExporter`) for handoff to other first-party tools

### UI

Compose **Network / History** screen (reachable from the browser chrome):

- On/off switch for the inspection proxy + intercept toggle
- Search bar + optional filters (method / status / host / content-type)
- List + deep detail view (bodies with content-type, cookies with attributes,
  decoded-not-verified JWTs)
- Clear + Export JSON + Modify / Replay actions

### Modify-and-replay (Burp-lite)

Intercept in-flight HTTP(S) on the localhost proxy, edit method / URL / headers /
body, then forward or drop. Replay any history entry with the same editor; when
the proxy is running, replay is sent through `127.0.0.1:<port>` and recorded in
`CaptureStore` (an internal marker skips the intercept gate so replay cannot
deadlock the UI).

- `InterceptController` — pause queue + Forward / Drop
- `RequestEditMerge` — pure edit-merge / header parse / Content-Length sync
- `ReplayClient` — rebuild + fire (`HttpURLConnection`)
- Compose **Modify / Replay** screen from Network / History (also **Edit & Replay**
  on a capture detail). Request bodies up to 256 KiB are aggregated for edit.

### Deferred

- Control-bridge ops for intercept/replay
- UI control to change `CaptureConfig` body-size cap at runtime

## Build

```bash
./gradlew assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

Debug signing uses the committed `app/debug.keystore` (alias `androiddebugkey`,
passwords `android`/`android`) so fleet installs update cleanly over prior builds.
Release signing is left to CI secrets — no release key is committed.

## Requirements

- JDK 17+
- Android SDK (compile/target SDK 35; minSdk 26)

## Brand

Dark charcoal UI with neon cyan (`#00E5FF`) → magenta (`#FF00E5`) accent ring theme.
`VictoriousBrowserTheme` is a single, hardcoded dark `MaterialTheme` — there is no
light variant and no toggle; the app is permanently dark by design.
`enableAlwaysDarkEdgeToEdge()` (`ui/theme/EdgeToEdge.kt`), called from every
Activity, forces light (white) status/navigation-bar icons unconditionally — the
stock `enableEdgeToEdge()` instead follows the *device's* system day/night setting,
which would otherwise mismatch this app's always-dark background on a phone set to
system Light mode.

## Third-party licenses

See `NOTICE` for LittleProxy (Apache-2.0), BouncyCastle (Bouncy Castle Licence ~MIT),
Netty (Apache-2.0, transitive), and AndroidX attributions.
