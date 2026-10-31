# Victorious Browser — In-App Manual

Last updated: 2026-10-30. Authorized-testing use only. This manual is the single
source of truth for the in-app Help / Manual screen: the app bundles this file as
an asset and renders it, so the on-device help and this document never drift.

## About & Safety

Victorious Browser is a fleet security / recon browser for the Victorious fleet.
Use it **only against systems you own or are explicitly authorized to test.**

- **Authorized use only.** Every offensive / testing tool here (Scanner, Fuzz,
  Templates, Metasploit console, injection tools) is for the owner's own assets
  or engagements with written permission. Unauthorized scanning or access is
  illegal in most jurisdictions.
- **Scope gating.** Active Scanner, Fuzz and Template runs are gated on a shared
  in-scope host allowlist (`ScannerConfig`). They never run on ordinary browsing
  traffic, require an explicit in-scope target host, and require an explicit Run.
- **Off by default.** The intercepting proxy, active scanning, Net ID / spoofing
  overrides, fleet export, Metasploit RPC, Anti-Phish detection, and external
  fleet control are all **off** until you turn them on.
- **External control off by default.** The fleet bridge accepts remote control
  only from the paired controller app and only when you enable external control
  on the Fleet Control screen. Reads may proceed; actions may not.
- **Secrets stay on device.** Exports and evidence strip cookies, passwords,
  proxy credentials, tokens and certs. Only public CA certificates are ever
  exported — never a private key.
- **Branding.** Dark charcoal UI with the neon cyan → magenta brand ring.

## Browser & Tabs

**What it does.** A tabbed browsing shell built on the system WebView (no bundled
Chromium). Each tab keeps its own live WebView across switches.

**How to use.** Type in the address bar: anything that looks like a URL is opened,
anything else is searched with your chosen **search engine** (DuckDuckGo by default;
change it under ⋮ → Search engine). While typing, suggestions come from open tabs,
bookmarks and history (picking an open tab switches to it). Go, Back, Forward and
Reload (which becomes **Stop** while a page loads). The tab strip switches/closes
tabs; the **tab-count** square opens the tab switcher (grid with favicon, title and
URL; New tab, Close others, Close all). Long-press a link for Open, New tab,
Background tab, Copy, Share, Bookmark and Download.

The **⋮ menu** holds New tab, **New private tab**, Tabs, History, Bookmarks,
Downloads, Find in page (next/previous match), Share/Copy link, Desktop site,
Reader, Night and Search engine. **Recon tools ▸** opens a second page: Proxy
history, **Send to repeater** (seeds Modify / Replay from the latest capture for
this origin), **Cookie jar** (per-URL CookieManager inspector: edit / delete /
export JSON, JWT decode-not-verify), **Web storage** (localStorage / sessionStorage
for the current origin, with the same JWT decode), **Console** (WebView
`console.*` + main-frame load errors), **Clear site data** (cookies + web storage
for this origin only), Anti-Phish, Inject (one-off JS or raw HTML), URL tools
(encode/decode, query view, shortener, URLhaus / VirusTotal with your own keys),
Screenshot → evidence, Certificate info, per-site Extensions toggle, Extensions,
User scripts and Fleet panels. History (max 500 entries) and bookmarks are stored
on the device and can be searched and deleted. Downloads go through Android's
DownloadManager and are listed under Downloads. Opening Network / Anti-Phish /
Extensions / Scripts from this menu **keeps the live WebViews composed**
underneath so a round-trip to those tools does not destroy tab state.

**Private tabs.** A magenta **P** badge / **PRIVATE** chip marks them. They skip
browsing-history writes and are never written to the tab session. When the WebView
Profile API is available they share an isolated `incognito` profile (cookies and
storage); closing the last private tab deletes that profile. Without multi-profile
support they still skip history/restore but share the default cookie jar — use
**Sandboxes** for named isolation on those devices.

**Session restore.** Non-private open tabs (URL + title, max 20) are saved and
reloaded after the app process is killed. JS heap and the back-forward list are
not restored; each tab loads its last URL fresh.

**Recon chips.** **PROXY** appears while the intercepting proxy runs (tap it for
history). The transport chip shows **HTTP · plain** for unencrypted pages or
**TLS · issuer** with the certificate WebView reported (tap it for details). With
the proxy on, that is the proxy's inspection certificate, not the origin's. A
magenta **JS N** chip appears when this tab has console errors (tap for the
console). There is deliberately no lock icon: the chip reports what was seen, not
a verdict.

**Limitations.** Downloads are fetched by DownloadManager directly and do not
pass through the proxy. blob: and data: downloads are not supported. Session
restore reloads URLs only (not JS heap / history list). CookieManager does not
expose HttpOnly / Secure / SameSite flags — the jar shows name=value for cookies
that would be sent on a request. Clear site data does not wipe Cache / IndexedDB /
service workers. HAR export of proxy history is **secret-stripped** (no
cookies/Authorization, no bodies). The on-device JSON export still keeps full
captures for local analysis.

**Safety.** The Inject tool deliberately does not sanitize HTML — it is for
testing how your own pages handle untrusted markup. User scripts are scoped to
host patterns. Reader output is sanitized before re-rendering.

## Sandboxes

**What it does.** Named, isolated browsing containers backed by the WebView
Profile API — each sandbox has its own cookies, storage and permissions.

**How to use.** Open **Sandboxes** from the launcher, pick or name a sandbox, and
open a browser tab inside it. A profile is assigned once at WebView creation and
cannot be reassigned mid-session, so choose the sandbox when opening the tab.

**Safety.** On WebViews without multi-profile support everything falls back to one
shared profile (no isolation, no crash). This is the least device-verified feature.

## Intercepting Proxy (Network / History)

**What it does.** On-device HTTP(S) inspection of this app's own WebView traffic —
a localhost socket proxy (LittleProxy on `127.0.0.1`, default port 8888), **not**
an Android VpnService. A BouncyCastle local root CA mints per-host leaf certs,
trusted only inside this app.

**How to use.** Open **Network** from the launcher or browser chrome. Toggle the
inspection proxy and intercept on. Browse; the history lists method, URL, host,
status, sizes, headers, request + response bodies, broken-out cookies, and decoded
(not verified) JWTs. Search and filter by method / status / host / content-type.
Clear, Export JSON (full local capture), **Export HAR** (HAR 1.2 with cookies /
Authorization / tokens stripped and bodies omitted), or Modify / Replay an
exchange. From the browser, **Send to repeater** jumps here with the latest
same-origin capture already loaded in the editor.

**Modify-and-replay (Burp-lite).** Intercept in-flight requests, edit method / URL
/ headers / body, then Forward or Drop; or replay any history entry through the
proxy.

**Safety.** Scoped to first-party traffic on your own devices. JWT signatures are
never verified (labelled `decoded-not-verified`). The content blocker records
blocked requests as `BLOCKED` rather than hiding them.

## Recon Workbench

**What it does.** Offline recon utilities — encoders/decoders, hashers and hash
identifier, and URL tools. All pure, local computation; no network.

**How to use.** Open **Recon** from the launcher. Pick a sub-tool, paste input,
read the result. Pivot into OSINT from here.

**Safety.** Entirely offline and non-intrusive.

## OSINT & Dork Builder

**What it does.** Compose Google-dork queries from operators / templates and open
them against Google / DuckDuckGo / Bing; pivot links for public OSINT sources
(crt.sh, DNS, reverse-IP, Wayback, Shodan, Censys, WHOIS, VirusTotal, GitHub /
Google dorks, username / email lookups); and a passive tech fingerprint over
already-captured responses.

**How to use.** Open **OSINT** from the launcher or Recon. Build a dork and open it
in a tab, or tap an OSINT source pivot. The fingerprint tab reads exchanges you
already captured — it does not fetch anything new.

**Safety.** Queries open in the browser for you to review; nothing is automated
against a target. Fingerprinting is passive over existing captures only.

## Scanner (Burp-lite)

**What it does.** A web-testing toolset: sitemap/crawler (site tree from captured
history plus a bounded, scope-gated same-origin crawl), an opt-in active scanner
(reflected XSS, error/boolean/time-based SQLi, SSRF indicators per insertion
point), a header fuzzer, a CL.TE / TE.CL request-smuggling heuristic probe
(detection only), and a bring-your-own OOB collaborator client (no bundled
server).

**How to use.** Open **Scanner** from the launcher. Add an in-scope target host,
choose checks, and press Run. Send findings to the fleet export sink.

**Safety.** All intrusive actions are **off by default**, require an explicit
in-scope host and an explicit Run, and never run on browsing traffic. Pure
detection logic is unit-tested.

## Fuzz, Templates & Wordlists

**What it does.** An ffuf-style directory/endpoint brute-forcer (FUZZ-marker or
append mode, wordlist + extensions, concurrency cap, status/size/word/line
filters, live dedup'd results with pause/stop); a Nuclei-**compatible-subset**
template scanner (hand-rolled minimal YAML; id/info/requests/matchers where
matchers are status|word|regex|size over body|header|status with and|or — NOT full
Nuclei: no DSL, extractors, workflows, payloads or variables); and a SecLists-style
wordlist library (import from file/paste/URL, store/list/preview/delete; small
curated starter lists bundled, full SecLists NOT bundled).

**How to use.** Open **Fuzz** from the launcher. Pick a wordlist (or import one),
set the target and markers/filters, and Run. Load or paste templates for the
template scanner.

**Safety.** Both runners are **scope-gated** on the shared `ScannerConfig`
allowlist: off by default, explicit in-scope host + explicit Run required, never on
browsing traffic, with default/max concurrency caps. Pure logic is unit-tested.

## Net ID / Spoof

**What it does.** Upstream proxy chaining (HTTP / SOCKS5), HTML5 geolocation
override, per-session UA presets, and optional fingerprint-surface shims
(platform, languages, hardwareConcurrency, screen, timezone offset, WebGL
vendor/renderer).

**How to use.** Open **Net ID / Spoof** from the launcher. Enable only the
overrides you need for a session.

**Safety.** All overrides are **off by default**. **MAC spoofing is intentionally
not implemented** — a normal Android app cannot change the Wi-Fi MAC; the UI shows
randomized-MAC status read-only and documents the limit. HTTPS-type upstream is
stored but mapped to HTTP CONNECT (see UI note).

## Fleet Export

**What it does.** Push captured endpoints / recon / scanner findings to other
fleet tools via a generic sink: an HTTP POST of normalized JSON to a
user-configured local/mesh endpoint, or a share-sheet / clipboard fallback.

**How to use.** Open **Export** from the launcher (or "Send to fleet" on Network /
Scanner screens). Configure the sink URL + optional bearer token, preview the
dry-run JSON, then send.

**Safety.** Secret stripping drops cookies, Authorization / Proxy-Authorization,
API keys and any password/token/secret/session/cert/key-like field before export —
**no secrets ever leave the device.** Off by default and empty until configured;
the receiving fleet-API contract is user-supplied, not bundled.

## Metasploit Console

**What it does.** A client for the Metasploit RPC API (MessagePack over HTTP, the
`msgrpc` interface): auth.login/logout, console.create/read/write/destroy,
session.list and a generic call. MessagePack is hand-rolled (no dependency added).

**How to use.** Open **Metasploit** from the launcher. Enter your own MSF host /
port / SSL / user / pass and press Connect, then use the interactive console and
session list.

**Safety.** Off / empty by default; nothing connects without your creds and an
explicit Connect. The permissive TLS trust is scoped to this client for your own
MSF host's self-signed cert only.

## Certs (Inspection CA)

**What it does.** Export the local inspection root CA (the BouncyCastle CA the
proxy uses) to Downloads as `.pem` and `.crt`/DER, show its SHA-256 fingerprint,
launch the guided OS install flow, and optionally regenerate the root.

**How to use.** Open **Certs** from the launcher. Export or view the fingerprint;
use the guided install for your own device; regenerate only when needed.

**Safety.** Only the **public** certificate is ever exported — never the private
key. Android cannot silently install a user CA, and modern apps do not trust
user-installed CAs; this browser trusts the local root for its OWN WebView only.
Regenerating invalidates any previously exported/installed CA.

## Evidence

**What it does.** Capture recon evidence into timestamped **cases** (one folder per
case, JSON index): WebView screenshots (the per-page "Shot" toolbar action),
secret-stripped request/response exchanges, scanner findings and notes. Export a
case as a ZIP (screenshots + Markdown & HTML report + index.json) to Downloads.

**How to use.** Open **Evidence** from the launcher, or tap **Shot** in the browser
chrome to capture the current page into the active case. Export the case when done.

**Safety.** Exchanges are secret-stripped (no cookies/auth/proxy-creds/tokens/
certs). Screenshots draw this app's own WebView to a bitmap — no MediaProjection,
no camera permission.

## Anti-Phish (Defensive)

**What it does.** In-app phishing-awareness training (red-flag cards + a
brand-neutral quiz) and a **passive** page analyzer that scores navigations for
lookalike/typosquat domains, punycode/IDN homoglyphs, mixed-script labels,
suspicious TLDs, structural host oddities, passwords on non-HTTPS, credential forms
on IP-literal hosts, cross-domain form actions, hidden credential fields, and
title/brand mismatches vs a user-editable known-good list.

**How to use.** Open **Anti-Phish** from the launcher. Work through the training and
quiz. Enable the passive analyzer to get an advisory banner at score ≥ 40 on
navigations; dismiss or report from the banner. Export the report log as JSON.

**Safety.** Detection is **off by default** and **non-blocking** (navigation is
never stopped). It is **not** a credential harvester, page-clone wizard, capture
server, or browser-hooking tool. The report log stores host + score + reasons only
— no query strings, secrets or credentials.

## Fleet Bridge v1 / Remote Control

**What it does.** A bound Messenger service lets the owner's own controller app
(`ai.droidcommand.app`) drive this browser as an `EXTERNAL_NODE`. Wire is
newline-delimited JSON; protocol major 1, minor 1.

**Security model.**
- Caller identity via `Message.sendingUid`, cert-pinned to the controller package
  (`ControllerAllowlist`), DEBUG + RELEASE pins.
- External control switch is **OFF by default** (Fleet Control screen). Actions
  (`remote.command`, `macro.*`, `config.*`) and all legacy ops are rejected with
  `not_paired` when off; reads (`capabilities.get`, `node.status.get`) may proceed.
- v1 replay/expiry: reject when `now > timestampMs + ttlMs`; reject reused nonces;
  ttl clamped to 300000 ms.

**Frozen v1 ops.**

| op | kind | remoteAllowed | requiresConfirm |
|---|---|---|---|
| capabilities.get | read | true | false |
| remote.command | action | false | false |
| macro.step | action | false | false |
| macro.run | action | false | false |
| node.status.get | read | true | false |
| config.export | action | false | false |
| config.import | action | false | true |

**How to use.** Open **Control** from the launcher to toggle external control,
approve/reject a pending `config.import`, and read the node id + pinned controller
info.

**Safety.** `config.export` is preferences-only and refuses cookies / passwords /
proxy creds / certs; `config.import` needs on-device approval and **never**
auto-enables external control. Error categories: `invalid_parameters`,
`not_paired`, `not_found`, `proxy_error`, `internal`, `requires_confirm`.

## Fleet Panels & Mesh

**What it does.** The launcher lists the owner's own locally-hosted / fleet web
dashboards and opens them in the WebView. Mesh-flagged panels resolve and probe
over Victorious Mesh before opening (this app holds no VpnService).

**How to use.** Add / edit / delete panels from the launcher. Tap a panel to open
it; mesh panels run a reachability check first and show an actionable dialog
(Retry / Open Victorious Mesh) if the tunnel is down. Tap **Pin** to pin a panel as
a chrome-less Lite App.

**Mesh settings.** Tap **Mesh settings** on the Fleet Panels screen to edit:
mesh IP ranges (CIDR, defaults `100.64.0.0/10` and `fd7a:115c:a1e0::/48`), your
Headscale `base_domain` for MagicDNS names (blank = unset; `*.ts.net` is not
used), and **Use Victorious Mesh bridge** (off by default). The mesh app is
`com.victorious.mesh`. The status line shows the bridge state, e.g.
"mesh bridge: not authorized yet — using passive detection" until Victorious Mesh
adds this browser to its allowlist; mesh panels keep working via passive VPN
detection either way. The bridge is read-only (`mesh.status`, `mesh.ip`) and your
mesh IP is never logged, saved, or included in config exports.

**Safety.** Reachability probing only — routing is provided by the Victorious Mesh
app when its tunnel is up. No credentials or private IPs are seeded; defaults use
placeholder `127.0.0.1` addresses.


## Extensions (Userscript Engine)

Victorious ships a **WebView-native userscript engine**, not a Chrome/Firefox
extension host. A system WebView has **no WebExtension API**, so `.crx` / `.xpi`
packages cannot run here. What we do support is Greasemonkey / Tampermonkey-style
**userscript logic** injected into pages.

### What it can do
- Parse `==UserScript==` metadata (`@name`, `@namespace`, `@version`,
  `@description`, `@match` / `@include` / `@exclude`, `@run-at`, `@grant`,
  `@require`, `@icon`).
- Match pages with Chrome-style `@match` patterns and `@include`/`@exclude` globs
  (or `/regex/` forms).
- Inject matching scripts at `document-start` (via `onPageStarted`, best-effort —
  not guaranteed before the page's own scripts), `document-end`, or
  `document-idle` (via `onPageFinished`; idle is deferred with `setTimeout`).
- Minimal GM shim: `GM_getValue` / `GM_setValue` / `GM_deleteValue` /
  `GM_listValues` (backed by app storage through a scoped JS bridge),
  `GM_addStyle`, `GM_log`, `GM_info`, `unsafeWindow` (= `window`).

### What is stubbed / not supported
- **No `.crx` / `.xpi` / real WebExtensions.**
- `@require` is recorded but **never fetched** (no remote code loading).
- `GM_xmlhttpRequest` is stubbed (logs + `onerror`) — no cross-origin XHR.
- `GM_openInTab`, `GM_setClipboard`, `GM_registerMenuCommand`, `GM_notification`
  are best-effort or no-ops.
- There is **no userscript sandbox** beyond ordinary page JS; `unsafeWindow` is
  just `window`.

### Manager UI
Open **Extensions** from the Fleet panel (Browse group) or the browser **Exts**
button. Import by pasting a `.user.js` or picking a local file (≤ 2 MB).
Enable / disable / delete; parsed metadata is shown. Three tiny self-authored
starter scripts ship under `assets/userscripts/` (dark mode, storage viewer,
show-all-links). Per-page: the **Exts on/off** chip pauses all extensions for
the active tab's host without uninstalling them.

The older **Scripts** button (host-pattern-only, no metadata block) remains for
simple one-off snippets; prefer **Extensions** for real userscripts.


## Privacy protections & Fire

Privacy features inspired by privacy-first mobile browsers, in Victorious neon. **Every toggle is OFF by default** because for recon, trackers, raw URLs, and referrers are often the signal. Open **⋮ → Privacy protections…** or tap the **shield** next to the address bar.

**What it does**
- **Fire** (flame button / ⋮ → Fire…): a confirm sheet with *Burn this tab* (that site's cookies, storage, and history, then the tab closes) or *Close all tabs & burn everywhere* (all tabs, including private ones, cookies, site storage, cache, HTTP auth, history, geolocation grants, saved tab session). Optional *Keep history* / *Keep cookies & site data*. It **never** touches bookmarks, downloads, proxy history/HAR/CaptureStore, extensions, or fireproofed sites' first-party cookies and storage.
- **Fireproof sites**: ⋮ → *Fireproof this site*, the shield sheet, or the manager in Privacy protections. Subdomains of a fireproofed site are covered too.
- **Tracker & ad loading protection**: blocks *third-party* requests to a bundled, hand-curated host list (advertising, analytics, social, session replay, cryptomining). There are per-site exceptions ("Protections on this site" in the shield sheet). It works with the proxy on or off. Blocked requests are **still recorded** in Proxy history, marked blocked.
- **Privacy Dashboard** (shield): per page, it shows trackers blocked or seen, categories, domains, HTTPS upgrades, tracking params stripped, and cookie pop-ups handled, plus a session total.
- **Force HTTPS**: upgrades http→https for typed and clicked top-level navigations. It skips IPs, localhost, single-label/LAN names (.local/.lan/.internal/.home/.test/.onion/.ts.net), and custom ports, and falls back to http if the upgraded load fails.
- **Global Privacy Control**: sends `Sec-GPC: 1` and sets `navigator.globalPrivacyControl = true`.
- **Strip link tracking**: removes utm_*, fbclid, gclid, msclkid, mc_eid, and similar parameters from top-level navigations.
- **Trim referrer**: same-site navigations keep the full referrer, cross-site ones send origin only, and https→http sends none. A strict-origin-when-cross-origin policy is also injected for subresources.
- **Cookie pop-up protection (lite)**: `userscripts/cookie-popups.user.js` clicks "Reject" on common consent managers or hides the banner.
- **Clear on exit**: runs Fire (everywhere, fireproof respected) when the app goes to the background or closes.
- **Search**: DuckDuckGo is the default engine. Bangs work in the address bar: `!g`, `!w`, `!b`, `!ddg`, `!gh`, `!yt` (e.g. `!w kotlin` or `kotlin !w`).

**Honest limits**
- The tracker list is small (~120 hosts), not a full blocklist. First-party and CNAME-cloaked trackers are not caught. The site comparison uses an approximate base-domain rule (no full Public Suffix List).
- The GPC header, Referer, param stripping, and HTTPS upgrade apply only to top-level GET navigations the browser issues or re-issues. They don't apply to POST forms, redirects, or subresources (WebView limits). The JS GPC flag applies to every page.
- Cookie pop-up handling is heuristic. It won't reach cross-origin iframes and doesn't speak the TCF API. Some banners will remain, and some sites may break. Turn it off per need.
- Fireproofed cookies are preserved by re-writing them. HttpOnly and original expiry attributes are lost (they are re-set as Path=/, 1-year, Secure on https). Cache clearing is always global.
- Clear on exit can't run if Android kills the process outright. Tabs close the next time the browser screen is shown.
- Not included: email protection, AI chat, app-level tracking protection VPN, sync.

## UI / Navigation (Neon Design System)

The launcher (`FleetPanelScreen`) is a grouped dashboard with a brand-ring hero:
Browse, Recon, Offense / Testing, Net ID, Fleet, Defense, plus a **Help / Manual**
tile that opens this manual. A branded Home surface offers the ring, a
search-or-URL quick-open bar and quick actions. Every tool screen shares
`NeonToolHeader`/`ToolScaffold` (ring accent + gradient title + back + actions).
This Help screen itself uses the same neon system and the cyan→magenta BrandRing.
