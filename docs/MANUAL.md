# Victorious Browser — Bridge v1 Manual

Last updated: 2026-10-20. Confidence: **[Certain]** for wire fields ratified in this tree.

## Overview

Victorious Browser is an `EXTERNAL_NODE` in the droidcommand-AI (DCA) fleet.
The bound Messenger bridge (`com.victorious.browser.action.BRIDGE`) accepts
newline-delimited JSON. Protocol major = 1, minor = 1.

## Identity and pairing

- Caller identified via `Message.sendingUid` (not `Binder.getCallingUid()`).
- Allowlist package: `ai.droidcommand.app` only (no `.debug` package pin).
- Cert pins (source of truth: `ControllerAllowlist.kt`):
  - DEBUG SHA-256: `88:00:51:A6:…:C8:4D`
  - RELEASE SHA-256: `9E:A7:…:2D:8D`
- External control switch is **OFF by default** (Fleet control screen).

## Frozen remote-control envelope v1

```json
{
  "op": "capabilities.get",
  "requestId": "…",
  "targetNodeRef": "browser-<32 hex>",
  "actionRef": "browser.open_url",
  "params": {},
  "nonce": "<32 hex chars = 128-bit>",
  "timestampMs": 1700000000000,
  "ttlMs": 120000,
  "destructive": false,
  "trigger": {}
}
```

| Field | Notes |
|---|---|
| op | One of the frozen ops below |
| requestId | Echoed as response `id` |
| targetNodeRef | Optional; if set must match this node's id |
| actionRef | Required for `remote.command` / `macro.step` |
| params | Object; default `{}` |
| nonce | 128-bit hex; replay rejected |
| timestampMs | Required |
| ttlMs | Default 120000; clamped to max 300000 |
| destructive | Bool; default false |
| trigger | Informational only |

### macro.step extras

`macroId`, `stepIndex` (≥0), `onError`: `abort` \| `continue`

### macro.run extras

`macroId`, `steps:[{actionRef, params?}]` (max 64), `dryRun` (bool), `onError`

### config.import extras

`dryRun`, `conflictPolicy`: `skip` (default) \| `overwrite` \| `merge-by-id`,
`params.preferences: [{name, entries}]`

## Ops

### capabilities.get

Returns `{ protocol_major, protocol_minor, package, node_id, operations:[{op,kind,remoteAllowed,requiresConfirm}], action_refs, proxy_supported }`.
`remoteAllowed:true` — works with external control OFF.

### remote.command

Runs one `actionRef` from the legacy `browser.*` set. Requires external control ON.

### macro.step / macro.run

Sequence control over the same `actionRef` targets. `dryRun` validates without
executing. `onError=abort` stops the run; `continue` records the error and goes on.

### node.status.get

Returns `NodeStatusSnapshot`:
`{ nodeId, packageName, connectionClass:"EXTERNAL_NODE", updatedAtMs,
domains:[{name, state, detail?}] }` with at least `browser` and `node_health`.
Detail ≤200 chars; no URLs, cookies, or personal data.
`remoteAllowed:true`.

### config.export

Preferences-only backup for `com.victorious.browser`. Refuses cookies, passwords,
proxy credentials, certs → `invalid_parameters`. External-control flag is forced
off in the export payload.

### config.import

Requires on-device confirmation (Fleet control → Approve), then resend the same
`requestId`. Supports `dryRun` and conflict policies. **Never** auto-enables
external control / remote.command via import.

## Check order

1. `sendingUid` package + cert pin (`CallerVerifier` / `ControllerAllowlist`)
2. Envelope parse (legacy `{id,op,params}` or v1)
3. v1: ttl expiry + nonce replay cache
4. External control for `remoteAllowed:false` (and all legacy ops)
5. Dispatch

## Legacy envelope

Still accepted when the line has `id` (and no `requestId`). All legacy ops
require external control ON. See README for the `browser.*` table.

## Errors

`invalid_parameters`, `not_paired`, `not_found`, `proxy_error`, `internal`,
`requires_confirm`.


## Anti-Phish (defensive)

Module path: `com.victorious.browser.antiphish`.

**Purpose.** Protective awareness + passive detection on pages the user
navigates to. Replaces any offensive SET/BeEF-style ask. Does **not** harvest
credentials, clone login pages, run a capture server, or hook browsers.

**Training UI.** Fleet neon Compose screen with red-flag cards (lookalike
domains, punycode/homoglyphs, mismatched form actions, insecure forms, urgency
cues, title/brand mismatch) and a static brand-neutral quiz (`PhishQuiz` —
fictional AcmePay / NorthBank / CloudBox only).

**Passive analyzer.** On each WebView `onPageFinished`, when detection is
enabled (`AntiPhishSettings`, **off by default**), `PageProbe` runs a JS
snapshot that returns title, HTTPS flag, form metadata (action host, password/
username presence, hidden-cred flags — **never field values**), and alt/aria
brand hints. `PhishingScorer` combines:

| Heuristic | Source |
|---|---|
| Punycode / IDN / mixed-script labels | `DomainAnalyzer` + `java.net.IDN` |
| Typosquat / lookalike vs known-good | Levenshtein + confusable skeleton |
| Suspicious TLD / structural host | Built-in TLD set + hyphen/depth heuristics |
| Password on non-HTTPS | `FormAnalyzer` |
| Cred form on IP-literal host | `FormAnalyzer` |
| Cross-domain form `action` | Registrable-domain compare |
| Hidden / off-screen credential fields | DOM style heuristics in probe |
| Title / brand-keyword mismatch | `PageBrandAnalyzer` vs editable list |

Score 0–100; advisory banner at ≥40 (`RISK_THRESHOLD`). User can dismiss or
report. Navigation is never blocked.

**Reporting.** `AntiPhishReportLog` stores `{timestamp, host, score, level,
reasons[]}` — host only, no query/path secrets, no credentials. Export writes
JSON under Downloads/`VictoriousBrowser/` via `MediaStoreDownloads`.

**Wiring.** Fleet Panels → **Anti-Phish**; MainActivity destination; hooked from
`VictoriousWebViewClient.onPageFinished` into TabbedBrowserScreen / BrowserScreen
banners.

**Tests.** `DomainAnalyzerTest`, `FormAnalyzerTest`, `PageBrandAnalyzerTest`,
`PhishingScorerTest`, `PageProbeTest` (pure JVM unit tests).

## UI / Navigation (neon design system)

The launcher (`FleetPanelScreen`) is a grouped dashboard with a brand-ring hero.
Tool tiles are grouped into **Browse** (Browser, Sandboxes), **Recon** (Recon,
OSINT), **Offense / Testing** (Scanner, Fuzz, Metasploit), **Net ID**
(Net ID / Spoof), **Fleet** (Control, Export, Network) and **Defense** (Certs,
Evidence, Anti-Phish). Every destination that existed before remains reachable;
only the labels/layout changed (the Metasploit RPC console tile reads
"Metasploit"). The hero also exposes a **Home** quick action.

A branded **Home / new-tab** surface (`AppDestination.Home`) provides the brand
ring, a search-or-URL quick-open bar (opens into the browser) and quick actions
into Fleet panels / Browser / Recon / OSINT / Scanner.

All tool screens now share `NeonToolHeader` (ring accent + gradient title + back +
optional actions). This is a visual/structural change only — no op, feature or
navigation wiring was removed.
