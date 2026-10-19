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
- **Mesh awareness (lightweight):** per-panel `reachOverMesh` flag + UI note that
  mesh routing is provided by the separate Victorious Mesh app. No VPN/mesh
  networking is implemented here — `TODO(mesh-reachability)` marks where that
  will later resolve
- `TODO(control-bridge)` marks where future ops `browser.panel.list` /
  `browser.panel.open` will read/drive the store — Messenger bridge is **not**
  built in this layer
- WebView opens when a panel is tapped; **Panels** returns to the launcher;
  Network / free Browser remain reachable from the chrome

## Browser shell

The browsing surface is a single system **WebView** hosted in Jetpack Compose via
`AndroidView` (no embedded Chromium / Gecko binary).

- Address bar + Go, Back, Forward, Reload, and a load-progress indicator
- Custom `WebViewClient` / `WebChromeClient` keep http(s) navigation in-app
- Recon-oriented defaults: JavaScript + DOM storage on, cookies via `CookieManager`,
  mixed content set to `MIXED_CONTENT_COMPATIBILITY_MODE` (noted in code)
- Optional Desktop / Mobile UA toggle
- `androidx.webkit` `ProxyController` routes the WebView through the local
  inspection proxy when enabled (see below)

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

## Third-party licenses

See `NOTICE` for LittleProxy (Apache-2.0), BouncyCastle (Bouncy Castle Licence ~MIT),
Netty (Apache-2.0, transitive), and AndroidX attributions.
