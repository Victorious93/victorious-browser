<!-- victorious-fleet-readme-refresh:start -->
<p align="center">
  <img src="assets/logo.png" alt="Victorious Browser logo" width="200">
</p>

# Victorious Browser

Fleet security / recon browser with web interception.

**Package id:** `com.victorious.browser`

**Status:** CI green *(from [fleet-manifest ROADMAP](https://github.com/Victorious93/fleet-manifest/blob/main/ROADMAP.md))*

## Install / build

Kotlin + Jetpack Compose (`app/build.gradle.kts`). Typical: `./gradlew :app:assembleDebug`.

_Steps above are taken from this repo’s build files / existing docs; anything not exercised in this refresh is unlabeled as verified on a device._

## Victorious fleet

- Monorepo: [https://github.com/Victorious93/victorious-fleet](https://github.com/Victorious93/victorious-fleet)
- Fleet site: [https://victorious-framework.pages.dev](https://victorious-framework.pages.dev)

<!-- victorious-fleet-readme-refresh:end -->

Fleet security / recon browser for the Victorious fleet.

Kotlin + Jetpack Compose Android app shell with a fixed debug keystore, dark brand
theme, a system WebView browsing surface, and an on-device HTTP(S) inspection layer
for the developer's own traffic.

## Status

- **Shell engine** — system WebView via `AndroidView` (landed)
- **Userscript Extensions** — WebView-native Greasemonkey-style engine + manager UI (not .crx/.xpi); starters under `assets/userscripts/` (landed on `feature/webext-engine`)
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

- **Net ID / Spoof** — upstream proxy chaining (HTTP / SOCKS5 via LittleProxy
  `ChainedProxyManager`; HTTPS-type stored but mapped to HTTP CONNECT — see UI),
  HTML5 geolocation override (JS shim + WebChromeClient deny for real OS location),
  per-session UA presets + optional fingerprint surface shims (platform, languages,
  hardwareConcurrency, screen, timezone offset, WebGL vendor/renderer). All **off by
  default**. **"MAC spoof" is intentionally not implemented**: a normal Android app
  cannot change the Wi-Fi MAC; the UI surfaces randomized-MAC status read-only and
  documents the limitation. (landed on `feature/proxy-spoof`)

- **Fleet export + Metasploit RPC** — push captured endpoints / recon /
  scanner findings to other fleet tools via a generic **fleet sink** (HTTP POST
  of normalized JSON to a user-configured local/mesh endpoint, or share-sheet /
  clipboard fallback), with a **Metasploit RPC console** (msgpack over HTTP
  msgrpc: auth.login/logout, console.create/read/write/destroy, session list,
  generic call). Exports strip cookies/passwords/proxy-creds/certs. Both are
  **off by default** and user-configured; the receiving fleet-API contract is
  **user-supplied, not bundled** (landed on `feature/fleet-export-msf`)

- **Fuzz / Templates** — ffuf-style directory/endpoint brute-forcer (FUZZ-marker
  or append mode, wordlist + extensions, concurrency cap, status/size/word/line
  filters, live dedup'd results table with pause/stop), a Nuclei-**compatible-subset**
  template scanner (hand-rolled minimal YAML parser; templates with
  id/info/requests/matchers where matchers are status|word|regex|size over
  body|header|status with and|or conditions — NOT full Nuclei: no DSL, extractors,
  workflows, payloads, or variables), and a SecLists-style wordlist library
  (import from file/paste/URL, store/list/preview/delete; small curated starter
  lists bundled, full SecLists NOT bundled — import your own). Both runners reuse
  `ReplayClient` and are **scope-gated** on the shared `ScannerConfig` allowlist:
  off by default, require an explicit in-scope host and explicit Run, never run on
  browsing traffic, with default/max concurrency caps. Pure logic (FUZZ
  substitution, filter matching, dedup, YAML parsing, matcher evaluation,
  wordlist parsing) is unit-tested. (landed on `feature/fuzz-templates`)

- **Inspection CA export/install + Evidence logger** — a **Certs** screen to
  export the local inspection root (the BouncyCastle CA the intercepting proxy
  uses for per-host leaf certs) to Downloads as **`.pem`** and **`.crt`/DER**,
  show its **SHA-256 fingerprint**, launch the **guided (manual) OS install**
  flow (`KeyChain.createInstallIntent()` with a fallback to
  `Settings.ACTION_SECURITY_SETTINGS`), and optionally **regenerate** the root
  (confirmation-gated; persists to app files dir and takes effect on next proxy
  start — **invalidates any previously exported/installed CA**). Honest by design:
  Android cannot silently install a user CA, and apps targeting modern Android do
  **not** trust user-installed CAs — this browser trusts the local root for its
  OWN WebView only (via `network_security_config`); other apps will not. Only the
  **public** certificate is ever exported — never the private key. An **Evidence**
  screen + per-page browser **"Shot"** toolbar action capture recon evidence into
  timestamped **cases** (one folder per case under app files dir, JSON index per
  case): screenshots (WebView→PNG), **secret-stripped** request/response
  exchanges (reuses the fleet `SecretStripper` — no cookies/auth/proxy-creds/
  tokens/certs), scanner findings, and notes. A case exports as a **ZIP**
  (screenshots + generated **Markdown & HTML** report + `index.json`) to Downloads
  via MediaStore/SAF. Pure logic (PEM/DER encoding, fingerprint formatting, case
  index serialization, report generation, filename sanitization, exchange
  stripping) is unit-tested. (landed on `feature/cert-evidence`)

- **Anti-Phish (defensive)** — in-app phishing-awareness training (red-flag
  cards + brand-neutral "spot the phish" quiz), a **passive** page analyzer that
  scores navigations for lookalike/typosquat domains, punycode/IDN homoglyphs,
  mixed-script labels, suspicious TLDs, structural host oddities, password fields
  on non-HTTPS, credential forms on IP-literal hosts, cross-domain form `action`,
  hidden/off-screen credential fields, and title/brand-keyword mismatches vs a
  user-editable known-good brand list. Non-blocking advisory banner; local host-only
  report log (no query/secrets/credentials) with JSON export to Downloads. Off by
  default. Pure detection logic unit-tested (`java.net.IDN`, no new deps). **Not** a
  credential harvester, page-clone wizard, capture server, or browser-hooking tool.
  (landed on `feature/anti-phish`)

- **In-app Help / Manual** — a searchable, sectioned on-device manual covering
  every tool (Browser/tabs, intercepting proxy, Recon, OSINT + dork builder,
  Scanner, Fuzz/templates/wordlists, Net ID / spoof, Fleet export, Metasploit
  console, Certs, Evidence, Anti-Phish, Fleet bridge v1 / remote-control) with
  what-it-does / how-to-use / safety notes, plus an About & Safety section
  (authorized-use, scope-gating, off-by-default, external-control-off). Content is
  sourced from `docs/MANUAL.md`, bundled verbatim as the asset
  `assets/manual/MANUAL.md` and rendered by a tiny hand-rolled, dependency-free
  Markdown renderer (`help/ManualParser.kt`, pure + unit-tested: headings, lists,
  code/pipe-tables, paragraphs, keyword section search). Reached from the
  **Help / Manual** launcher tile (`AppDestination.Help`), styled with the neon
  design system (`ToolScaffold` + cyan→magenta `BrandRing`). No new dependencies.
  (landed on `feature/in-app-help`)


## Fleet export + Metasploit RPC

### Export to fleet (`com.victorious.browser.export`)
A generic **fleet sink** abstraction to hand captured endpoints, requests, and
recon/scanner findings to other fleet tools (**HackerAI-Commander**,
**Pentest-Swarm-AI**).

- **Normalized payload** (`FleetExportPayload`): `source = com.victorious.browser`,
  `schemaVersion`, `exportedAt`, `targets[]`, `endpoints[{method,url,params,headers?,
  statusCode}]`, `findings[]`, and `counts`.
- **Secret stripping** (`SecretStripper`): cookies, `Authorization` /
  `Proxy-Authorization`, API keys, and any `password`/`token`/`secret`/`session`/
  `cert`/`key`-like header or param are dropped before export. This mirrors the
  capture-export secret rule — **no cookies, passwords, proxy creds, or certs ever
  leave the device in an export.**
- **Sinks**: (a) **HTTP sink** — POST the JSON to a user-configured local/mesh URL
  with an optional bearer token; (b) **Intent/clipboard sink** — Android
  share-sheet or clipboard copy fallback.
- **UI**: "Send to fleet" actions on the Network/History and Scanner result
  screens, plus an **Export** screen (sink config + dry-run JSON preview).
- Config is `SharedPreferences` (`ExportSettings`), **empty by default**.

> **CAVEAT — fleet API contract unknown.** The exact request path, headers, and
> body shape expected by HackerAI-Commander and Pentest-Swarm-AI are **not
> confirmed**. This app does **not** invent or bundle their endpoints: the target
> URL + auth are fully user-configurable and default unset. Supply the endpoint
> yourself and confirm the contract with those tools' owners. See the
> `TODO(contract)` in `HttpFleetSink`.

### Metasploit RPC console (`com.victorious.browser.msf`)
Client for the Metasploit RPC API (**MessagePack over HTTP**, the `msgrpc`
interface — POST to `/api/1.0/` with a msgpack `[method, token?, args...]` body;
auth via `auth.login` returning a token).

- Configurable host/port/ssl/user/pass (`MsfConfig`, `SharedPreferences`,
  **off/empty by default**). The msgrpc service's default self-signed TLS cert is
  accepted by a permissive trust manager scoped to this client only (owner's own
  MSF host).
- Implements `auth.login`/`auth.logout`, `console.create`/`read`/`write`/`destroy`,
  `session.list`, and a generic `call(method, args)`.
- Compose console UI: connect, interactive read/write, session list. Nothing
  connects without user-entered creds + an explicit **Connect**.
- **MessagePack is hand-rolled** (`MsgPack.kt`) — a minimal, first-party
  encoder/decoder (nil/bool/int/float/str/bin/array/map), unit-tested. No msgpack
  dependency was added.

Pure logic in both features (payload normalization, secret-stripping, JSON
serialization, msgpack encode/decode, MSF request/response mapping) is
unit-tested.

## Fleet control bridge

First-party inter-app automation: a bound **Messenger** service so the owner's
OWN controller app (`ai.droidcommand.app`) can drive Victorious Browser on the
owner's own devices (EXTERNAL_NODE class).

### Protocol
- Intent action: `com.victorious.browser.action.BRIDGE`
- `Message.what` = `1` request / `2` response
- Wire: newline-delimited JSON, `BRIDGE_PROTOCOL_MAJOR = 1`, `MINOR = 1`
- **Legacy envelope** (still accepted): `BridgeRequest { id, op, params }` →
  `BridgeResponse { id, ok, result?, error? }` with `error = { category, message }`
- **Frozen remote-control envelope v1**: flat top-level fields
  `{ op, requestId, targetNodeRef?, actionRef?, params, nonce (128-bit hex),
  timestampMs, ttlMs (default 120000, max 300000), destructive, trigger,
  …macro/config fields }` → same response shape (`id` = `requestId`)

### Security model
1. **Caller identity** via `Message.sendingUid` (not `Binder.getCallingUid()`):
   resolve packages for that uid and verify signing-cert SHA-256 against the
   pinned DEBUG + RELEASE fleet-provided controller cert SHA-256 for package
   `ai.droidcommand.app` only (no `ai.droidcommand.app.debug` pin — dropped by the fleet).
   RELEASE pin verifies the CALLER; it is not this app's own signing key.
   Source of truth: `ControllerAllowlist.DEBUG_CERT_SHA256` /
   `RELEASE_CERT_SHA256` (unchanged; v1 ops reuse the same gate).
2. **External control** persisted switch — **OFF by default**. Ops with
   `remoteAllowed:false` (remote.command, macro.*, config.*) and all legacy
   ops are rejected with `not_paired` when off. Reads
   `capabilities.get` / `node.status.get` may proceed when off.
3. **Replay/expiry (v1)**: reject when `now > timestampMs+ttlMs`; reject reused
   nonces (in-memory nonce cache). ttl clamped to max 300000 ms.
4. Envelope / parameter validity → `invalid_parameters` on any invalid field.

Check order: identity → envelope → ttl/nonce (v1) → external-control
(remoteAllowed=false) → dispatch.

### Frozen v1 operations (capabilities.get advertisement)

| op | kind | remoteAllowed | requiresConfirm |
|---|---|---|---|
| `capabilities.get` | read | true | false |
| `remote.command` | action | false | false |
| `macro.step` | action | false | false |
| `macro.run` | action | false | false |
| `node.status.get` | read | true | false |
| `config.export` | action | false | false |
| `config.import` | action | false | true |

- **remote.command / macro.*** — `actionRef` targets existing `browser.*` ops
  (capabilities.get, open_url, panel.list/open, recon.summary, proxy.set/status).
  `macro.run` supports `dryRun` (validate only) and `onError: abort|continue`.
- **node.status.get** — `NodeStatusSnapshot` with domains at least `browser` and
  `node_health`. Detail ≤200 chars; no URLs/cookies/personal data.
- **config.export** — ONE preferences-only entry for `com.victorious.browser`.
  Never cookies, passwords, proxy credentials, or certs (`invalid_parameters`).
- **config.import** — `dryRun` + `conflictPolicy skip|overwrite|merge-by-id`
  (default skip); requires on-device confirmation (Fleet control screen);
  **never** auto-enables external control via import.

### Legacy browser.* operations
| op | params | result |
|---|---|---|
| `browser.capabilities.get` | `{}` | `{ protocol_major, protocol_minor, package, node_id, operations, v1_operations, proxy_supported }` |
| `browser.open_url` | `{ url }` | `{ status:"opened" }` |
| `browser.panel.list` | `{}` | `{ panels:[{id,name,url,kind}] }` |
| `browser.panel.open` | `{ panel_id }` | `{ status:"opened" }` |
| `browser.recon.summary` | `{ limit?, since? }` | `{ count, entries:[{method,url,status,host,ts}] }` |
| `browser.proxy.set` | `{ enabled:bool }` | fire-and-forget ack |
| `browser.proxy.status` | `{}` | `{ running, port, ca_installed }` |

Parameterless legacy ops must receive exactly `{}` — non-empty params → `invalid_parameters`.

Error categories: `invalid_parameters`, `not_paired`, `not_found`, `proxy_error`,
`internal`, `requires_confirm`.

Node-id: generated/persisted on first run, matches `^browser-[0-9a-f]{32}$`.

In-app **Fleet control** settings screen (from the launcher **Control** button):
external-control switch, pending `config.import` approve/reject, read-only
node-id, pinned controller info. See `docs/MANUAL.md` for the v1 envelope.

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
a plain TCP check:

1. **Classify the host** (pure, unit-tested): loopback / mesh IP inside the
   configured ranges / MagicDNS name under the configured Headscale
   `base_domain` / bare node name / direct.
2. **Detect the tunnel** (strongest first): Victorious Mesh bridge
   `mesh.status` (if enabled and authorized) → `ConnectivityManager`
   `TRANSPORT_VPN` network correlated with `com.victorious.mesh` (owner UID on
   API 29+ when visible, or a VPN link address in the mesh ranges) → local
   address in the mesh ranges → any VPN transport → interface-name prefixes
   (weak fallback only; `tunN` naming is unreliable).
3. **Resolve the name**, then **TCP-probe** host:port.
4. **Decide**: open on success; otherwise "mesh node unreachable", "unknown mesh
   host", or an actionable **"Victorious Mesh tunnel appears down"** dialog with
   **Retry** / **Open Victorious Mesh**.

### Mesh config (Fleet Panels → **Mesh settings**)

| Setting | Default | Notes |
|---|---|---|
| Mesh package | `com.victorious.mesh` | Confirmed, debug + release, not editable |
| Mesh IP ranges | `100.64.0.0/10`, `fd7a:115c:a1e0::/48` | Editable CIDR list (IPv4/IPv6) — override for a custom Headscale prefix |
| Headscale `base_domain` | unset | MagicDNS suffix. **Not** `*.ts.net`. When unset, suffix matching is skipped |
| Use Victorious Mesh bridge | off | Read-only, best-effort |

Stored in SharedPreferences `mesh_config`. Mesh IPs are never stored.

### Mesh bridge client

`MeshBridgeClient` binds to Victorious Mesh's Messenger service
(`com.victorious.mesh.action.BRIDGE`, spec: `docs/FLEET_BRIDGE.md` in
`Victorious93/victorious-mesh`) and sends **only** read ops `mesh.status` and
`mesh.ip` (wire `mesh.ip.get`) — legacy request until the node id is learned,
then the frozen v1.1 envelope (`remote.command`, nonce, TTL clamped to ≤300 s).
No `node.status.get` (the droidcommand-AI controller is the sole status-tray
aggregator), no `mesh.up`/`mesh.down`, no control ops.

Victorious Browser is **not yet on Mesh's package+cert allowlist**, so the bridge
currently answers `unauthorized`. The client degrades gracefully: the UI shows
"mesh bridge: not authorized yet — using passive detection" (or
unavailable / not installed / external control off) and reachability falls back
to passive `NetworkCapabilities` inference. No crash on denial, absence, or timeout.

`mesh.ip` is **sensitive**: never logged, never persisted, and any `mesh_ip`-like
key is stripped from `config.export` (and refused if requested).

This browser does not hold the VpnService slot.

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

### Userscript Extensions (WebView-native)

**Not** Chrome/Firefox `.crx`/`.xpi` support — a system WebView has no WebExtension
API. Victorious runs Greasemonkey/Tampermonkey-style **userscripts** injected into
pages:

- Metadata parser (`@name`, `@match`/`@include`/`@exclude`, `@run-at`, `@grant`, …)
- Chrome-style match patterns + globs; inject at document-start/end/idle via the
  existing WebView client hooks
- Minimal GM shim (`GM_getValue`/`GM_setValue` via a scoped JS bridge, `GM_addStyle`,
  `GM_log`, `unsafeWindow`); `@require` and `GM_xmlhttpRequest` are stubbed
- **Extensions** manager UI (Fleet panel → Browse, or browser **Exts** button):
  paste or load `.user.js` (≤ 2 MB), enable/disable/delete, per-host pause chip
- Three self-authored starter scripts in `assets/userscripts/` (see NOTICE)

The older host-pattern **Scripts** screen remains for simple snippets.

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

## Permissions

- `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` (read-only) — as before.
- `WRITE_EXTERNAL_STORAGE` (**`maxSdkVersion=28`**) — only for the legacy
  (API ≤ 28) path that writes CA / evidence exports into the public Downloads
  folder. On API 29+ exports use **MediaStore** scoped storage and require **no**
  runtime permission. No new runtime-dangerous permission is requested on modern
  Android. Screenshot capture draws this app's OWN WebView to a `Bitmap` (no
  `MediaProjection`, no camera permission).

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

## Design system (neon brand)

The app ships a cohesive dark neon design system under
`app/src/main/java/com/victorious/browser/ui/`:

- **Theme** (`ui/theme/`): charcoal base ramp + cyan→magenta accent tokens
  (`Color.kt`), a Material3 dark `ColorScheme`, full `Typography`, and `Shapes`.
  `BrandGradient.kt` is the single source of truth for the cyan→violet→magenta
  brand gradient and exposes pure, unit-tested color math (`blendArgb`,
  `sampleRing`) plus `Brush`/`BorderStroke` builders (`Brand`).
- **Components** (`ui/components/`): `BrandRing` (the glowing cyan→magenta ring
  brand mark, drawn with Compose Canvas — no assets), `GradientCard`,
  `NeonSectionHeader`, `GradientTitle`, `StatusChip` (on/off/running/risk/warn/idle),
  `NeonButton` / `NeonOutlineButton`, `ToolScaffold` + `NeonToolHeader` (the shared
  tool-screen app bar: ring accent + gradient title + back + actions + underline),
  and `ToolTile` / `QuickAction` for the launcher dashboard.

**Surfaces restyled:** the launcher (`FleetPanelScreen`) is now a grouped
dashboard (Browse · Recon · Offense/Testing · Net ID · Fleet · Defense) with a
brand-ring hero; a new branded **Home / new-tab** screen (`home/HomeScreen.kt`)
with the ring hero, quick-open bar and quick actions; the tabbed browser address
bar; and every tool screen shares the `NeonToolHeader`. All existing tools and
navigation entries are preserved.

No new runtime dependencies were added; the ring/brand mark is pure Compose
Canvas (no Lottie, no external icon packs — Material `material-icons-core` only).
