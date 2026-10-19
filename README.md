# Victorious Browser

Fleet security / recon browser for the Victorious fleet.

Kotlin + Jetpack Compose Android app shell with a fixed debug keystore, dark brand
theme, a system WebView browsing surface, and an on-device HTTP(S) inspection layer
for the developer's own traffic.

## Status

- **Shell engine** — system WebView via `AndroidView` (landed)
- **Proxy / MITM library** — LittleProxy (localhost socket proxy) + BouncyCastle
  local CA (landed on `feature/intercept-proxy`)

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

- History of method, URL, host, status, timestamps, byte sizes, headers
- Persisted in an in-memory `CaptureStore` (`StateFlow`, capped) — a simple local
  store; Room can replace it later without a model change. Bodies are modeled
  as nullable placeholders for a later depth pass
- Helpers: surface cookies, detect `Authorization: Bearer`, decode JWT header /
  payload (base64 only — **no** signature checking)
- Export captured entries to a JSON file under the app's external files
  (`CaptureExporter`) for handoff to other first-party analysis tools

### UI

Compose **Network / History** screen (reachable from the browser chrome):

- On/off switch for the inspection proxy
- List + detail view (headers, cookies, decoded JWT)
- Clear + Export JSON actions
- `TODO(control-bridge)` marks where a later control-bridge will toggle the proxy;
  that bridge is **not** built in this layer

### Deferred

- Control-bridge toggle
- Modify-and-replay
- Body capture depth (fields exist; not filled yet)

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
