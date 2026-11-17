# VictorLinux PC integration plan

**Status: planning only.** This document describes how Victorious Browser should talk to a VictorLinux PC. It is not a promise that the feature exists, and it is not permission to write feature code.

Implementation waits for Don's explicit approval in the browser agent's own chat. This pull request must not be merged as a substitute for that approval, and no Add-PC form, status card, chat client, TLS pin, or Fleet-bridge hook should be added until that yes is given.

## 1. Standalone in both directions

The browser is a complete app without VictorLinux. Browsing, Fleet tools, proxy, recon, and everything else keep working if no PC is added, if the PC is offline, or if VictorLinux is never installed.

VictorLinux is a complete PC image without the browser. Its Control Center, local inference, and storage do not depend on this app.

Until the owner adds a PC, the feature stays out of the way:

- The only visible hook is a **VictorLinux PC** entry in the Fleet tools sheet (Tools → *VictorLinux PC*), which opens an **Add PC** form.
- Chat, the live status card, and any "Check PC" chrome stay hidden until a PC has been saved.
- Nothing auto-discovers a PC, nothing phones home, and nothing is sent to a VictorLinux address that the owner did not save.

`127.0.0.1` and `localhost` on the phone are the phone, not the PC. The saved address must be the PC's LAN, VPN, or mesh address, or an explicit tunnel the owner runs (`adb reverse`, SSH port forward) and marks as a tunnel in the form. The Control Center at `127.0.0.1:8787` and its per-session token are never used by this app, never requested, and never stored.

## 2. What is already true in this repo

Reviewed against `origin/main` at `3781a9afcaeff7861acb190c32aee11576902333` (2026-11-10, America/New_York).

[Certain] There is no VictorLinux companion feature on browser `main`. A search of that tree for `victorlinux`, `VictorLinux`, `victor-local`, `/victor/companion`, and `chat/completions` returned no hits. The word "companion" appears only as Kotlin `companion object`. Mentions of port `8080` are ordinary URL examples and Fleet-panel placeholders (`http://127.0.0.1:8080/`), not a PC gateway client.

Existing pieces this design should reuse rather than reinvent:

- Fleet tools sheet and `ToolsCatalog` (add a *VictorLinux PC* item; wire it through `toolDestination`).
- `SecretBox` (Deep Research already wraps a user-pasted API key with an Android Keystore AES-256-GCM key and stores only ciphertext in app-private prefs).
- `config.export` allow-listing in `BridgeDispatcher` (preferences only, never Keystore material) plus `SecretStripper` / `ConfigBackup` denylists. The bearer key must stay off that allow list and would also be caught by existing `secret` / `token` / `apikey` fragments — still call this out as a hard requirement, not an accident of the denylist.
- Dark neon theme (`NeonCyan` `#00E5FF`, `NeonMagenta` `#FF2BD6`, semantic `StatusOk` / `StatusWarn` / `StatusDanger`).

## 3. Sources (nothing invented)

The contract for the phone is [Victorious93/VictorLinux `docs/companion-api.md`](https://github.com/Victorious93/VictorLinux/blob/main/docs/companion-api.md) on VictorLinux `main` (repo last pushed 2026-11-15 12:46 AM America/New_York). That page is short prose. It names one JSON field, `transport.tls_fingerprint_sha256`, and lists Check PC states in words. It does not include a formal JSON schema.

Related VictorLinux docs that do not replace `companion-api.md`, but bound the same gateway:

- [`docs/remote-access.md`](https://github.com/Victorious93/VictorLinux/blob/main/docs/remote-access.md) — gateway on port 8080, Bearer key, model name `victor-local`, OpenAI-compatible `POST /v1/chat/completions` with SSE streaming, `GET /victor/health` and `GET /victor/capabilities`.
- [`docs/security.md`](https://github.com/Victorious93/VictorLinux/blob/main/docs/security.md) — the companion API has no storage, file, or volume route; a self-signed certificate only helps if the client pins it.
- [`docs/fleet-integration.md`](https://github.com/Victorious93/VictorLinux/blob/main/docs/fleet-integration.md) — use the real PC address over LAN/VPN; do not put the Control Center token in a URL or a JavaScript store; Keystore-backed credentials; send an inference key only to its saved origin; no global certificate-error bypass.

To list actual JSON keys without inventing them, this plan also read the implementation those docs describe, on the same VictorLinux `main`:

- [`airootfs/usr/local/lib/victorlinux/companion.py`](https://github.com/Victorious93/VictorLinux/blob/main/airootfs/usr/local/lib/victorlinux/companion.py) (`SCHEMA = 1`, `report()`)
- [`airootfs/usr/local/lib/victorlinux/gateway.py`](https://github.com/Victorious93/VictorLinux/blob/main/airootfs/usr/local/lib/victorlinux/gateway.py)
- [`tests/test_control_api.py`](https://github.com/Victorious93/VictorLinux/blob/main/tests/test_control_api.py) (`test_companion_report_is_authenticated_read_only_and_secret_free`)

If `companion-api.md` and that source ever disagree, the markdown is the published contract and the mismatch is an open question for VictorLinux, not something the browser should guess its way around.

The Fleet coordinator in `docs/MANUAL_FLEET.md` / `fleet/docs/FLEET.md` is a different system (phone workers, pairing secrets, job plans). It is out of scope here.

## 4. Gateway rules

| Item | Rule |
|---|---|
| Base URL | `https://<pc>:8080/v1` by default. The owner may change host or port; the app stores the exact origin (`scheme + host + port`). |
| Auth | `Authorization: Bearer <key>` on every companion and chat request. The key is created in the PC Control Center under *Remote access* and is shown once. |
| Model name for chat | `victor-local` (accepted by the gateway; llama.cpp ignores the name). |
| Status | `GET /victor/companion` only. Read-only. Non-GET must not be sent. |
| Chat | `POST /v1/chat/completions`, SSE streaming. |
| Admin UI | Control Center on `127.0.0.1:8787` is never contacted. |
| TLS | Self-signed is expected. The app pins the certificate SHA-256 and also checks that the certificate belongs to the saved address. It never ignores certificate errors. |

The bearer key is sent **only** to the saved origin. An exact match on scheme, host, and port is required. No redirect, IP fallback, or "same host, different port" may carry the key. The key must never appear in a URL or query string, a WebView, a JavaScript bridge, `localStorage`, app logs, HAR / proxy history, evidence packages, or fleet exports (`config.export` must exclude it; so must `SecretStripper` / staged Export payloads).

TLS is a **per-host pinned trust manager scoped to this client only**. It must not:

- install a global `TrustManager` or `HostnameVerifier`,
- call through certificate errors in WebView,
- change LittleProxy / MITM / WebView trust,
- add the PC certificate to the system or app-wide trust store,
- disable validation for any other HTTP client.

Fingerprint format observed in `companion.py` (`tls_fingerprint()`): SHA-256 of the certificate's DER encoding, lowercase hex, 64 characters, no colons. The same value is what `victor-tls-cert` and `/victor/companion` → `transport.tls_fingerprint_sha256` are supposed to show. The app should compute that hash itself from the certificate presented in the TLS handshake (for trust-on-first-use and for later checks), not by first skipping TLS in order to read the field from the API.

`GET /victor/health` is documented as unauthenticated (`200 {"status":"ok"}` or `503 model_not_running`). This plan does **not** use it for the status card. The published companion contract says everything the phone can learn about the PC, besides inference itself, is in the authenticated companion report. Whether health is used as a later, key-free reachability hint is an open question.

## 5. Work item 1 — Add PC form (High priority, Medium size)

Entry: Fleet tools → **VictorLinux PC** → **Add**.

Fields:

1. **Address** — host or full URL. Default scheme `https`, default port `8080`. Show the normalized origin before save. Reject empty input. Warn clearly if the host is `127.0.0.1`, `localhost`, or `::1`, unless the owner ticks **This is a tunnel on this phone**.
2. **Bearer key** — password-style field (masked, paste allowed). Stored only in Keystore-wrapped storage: an AES-256-GCM key in the Android Keystore (a dedicated alias, not the Deep Research HIBP alias) and ciphertext in a private prefs file that is **not** on the `config.export` allow list. The plaintext key is held in memory only for the request, then discarded. Removing the PC deletes the ciphertext and should invalidate or delete that Keystore alias.
3. **Pinned fingerprint** — either:
   - **Trust on first use:** connect, show the SHA-256 of the certificate that was actually presented, ask the owner to compare it with the PC (`victor-tls-cert <pc-address>` or the Control Center), and save the pin only after an explicit confirm; or
   - **Paste a value** the owner copied from the PC, then only accept a certificate that matches that pin and the saved address.

A first successful **Check PC** also pins `pc.id` from the report. That id is not a secret; it is how later checks detect a different PC.

Optional on the form: a display name, and the tunnel checkbox above. v1 stores **one** PC. Remove / replace is explicit (and wipes the key, the pin, the pinned id, and local chat history for that PC).

Validation before the first request: origin is `https` (or `http` only if the owner confirmed a trusted private link / tunnel — default is to prefer refusing plain HTTP; see open questions), fingerprint is empty or a normalizable SHA-256, key is non-empty.

## 6. Work item 2 — Status card and Check PC (High priority, Medium size)

After a PC is saved, **VictorLinux PC** opens a status card instead of only the empty Add form. A **Check PC** button performs `GET /victor/companion` with the pinned TLS client and the Bearer key, against the saved origin only.

On a successful schema-1 report the card may show the published facts: host name, PC id (not as a secret), RAM and GPU name/VRAM, backend and memory pool, running model *file name*, context, GPU layers, generation-check result, top local models with fit, and TLS status/fingerprint. All of those are described in `companion-api.md`. Concrete JSON keys are in §8.

The first successful check pins `pc.id`. Later checks compare the new id, the TLS pin, and the running model file name against what was pinned.

Rotating or revoking the key on the PC must show **bad key** immediately. `companion-api.md` says there is no grace period.

### 6.1 State map

States below are exactly the later-check states named in `companion-api.md` ("offline / tunnel down", "bad or rotated key", "wrong host / certificate problem", "a different PC answered (id changed)", "model changed", "unsupported report"), plus the success case the card also needs. Colours use the existing semantic status tokens (and the neon palette). UI text is plain language for Don.

| State | How it is detected | UI text | Colour | User action |
|---|---|---|---|---|
| Ready (Check PC succeeded) | `GET /victor/companion` returns 200, JSON parses, `schema` is `1`, required fields are present, `pc.id` matches the pinned id (or this is the first successful check, which pins it), and the TLS pin + address both match. | **Connected** — show host name and the running model file name (or "No model running" if `runtime.running` is false). | Green (`StatusOk`) | Chat may be used. Offer Check PC again, Edit, or Remove. |
| Offline / unreachable | Transport failure: no route, timeout, connection refused, DNS failure. If the saved PC is **not** marked as a tunnel. | **Offline** — can't reach the PC at the saved address. | Amber (`StatusWarn`) | Retry. Confirm the PC is on, Remote access is enabled, and the address is still right. |
| Tunnel down | Same transport failure, but the owner marked this address as a tunnel. Named separately in `companion-api.md`. | **Tunnel down** — the tunnel on this phone is not reaching the PC. | Amber (`StatusWarn`) | Retry after bringing the tunnel back (`adb reverse`, SSH forward, or mesh). |
| Bad or rotated key | HTTP **401** or **403** from the saved origin (after TLS already succeeded). Current VictorLinux `gateway.py` returns **401** with `authentication_error` for a missing or wrong Bearer token; it does not use 403 for this. Treat 403 the same way if it ever appears. | **Bad key** — this key was rejected. It may have been rotated or revoked on the PC. | Red (`StatusDanger`) | Paste a new key from Control Center → Remote access. No grace period. |
| Wrong host / certificate problem | TLS failed: pin mismatch, hostname/IP does not match the saved origin, an untrusted cert, a fingerprint the owner declined, or a cert that does not match both the pin **and** the address (`companion-api.md`). | **Wrong host / certificate problem** — the certificate or address does not match what was pinned. | Red (`StatusDanger`) | Confirm the address. If the PC's certificate was intentionally replaced, compare the new SHA-256 and re-pin only after the owner confirms. |
| Different PC (identity changed) | Report is otherwise valid, but `pc.id` is present and **differs** from the id pinned on first success. `companion.py` says a different id at the same address means a different PC or re-provisioned storage. | **A different PC answered** — this is not the PC that was pinned. | Red (`StatusDanger`) | Do not silently adopt the new id. Offer "This is a new PC" (re-pin id, clear chat history) or "Wrong address" (edit / remove). |
| Model changed | See the open question in §11. Proposed default: a valid report whose `runtime.model` (the running file name, which is what `companion-api.md` says the report contains) differs from the file name stored at the last successful check. Chat requests still send `model: "victor-local"`. If a chat or `/v1/models` response names something other than `victor-local`, show this state as well. | **Model changed** — the PC is running a different model than last time. | Amber (`StatusWarn`) | Tell the owner the previous and current file names. Offer "Use this model" (update the stored name and continue) or Check PC later. Do not silently keep chatting as if nothing changed. |
| Unsupported report | HTTP 200 but the body is not JSON, `schema` is missing or not `1`, or fields `companion-api.md` depends on (stable PC id, TLS fingerprint when TLS is on) cannot be read. | **Unsupported report** — this PC's reply is not a format this app can use. | Amber (`StatusWarn`) | Check PC again after updating VictorLinux or the app. Do not pin a new id or fingerprint from a report that could not be parsed. |

Chat errors use this same table (§7).

Informational only (not a named pairing state): a valid report with `runtime.running` false can still be **Connected** with the line "No model running." Starting a model is done on the PC, not in this app.

## 7. Work item 3 — Chat (Medium priority, Medium size)

Hidden until a PC is added and a Check PC (or an equivalent successful companion fetch at chat start) has produced a usable pin.

- `POST https://<saved-origin>/v1/chat/completions` with `Authorization: Bearer <key>`, `model: "victor-local"`, and `stream: true` (SSE, as `remote-access.md` describes).
- Same pinned TLS client and same origin rule as Check PC. No other protocol (Ollama `/api/chat`, llama.cpp `/completion`) in v1.
- Do not send tools or ask the PC to execute anything. The gateway advertises `tool_execution_on_pc: false` and drops tools.
- Map failures onto §6.1: timeouts and refused connections → offline / tunnel down; 401/403 → bad key; TLS/pin failures → wrong host / certificate problem; a different `pc.id` if a status refresh is done at chat start → different PC; a model name other than `victor-local`, or a mid-session companion check that shows a new file name → model changed; 503 with the gateway's "no model is loaded" error → show that the model is not running (informational), not "bad key."
- Conversation history stays on the phone only (app-private storage). It is not written to HAR, logs, evidence, Export, or `config.export`. Removing the PC, or accepting "This is a new PC," deletes it. It is never sent back except as the `messages` array of the next chat request to the saved origin.

## 8. Companion report shape (schema 1)

`companion-api.md` says the report (schema 1) contains: a stable PC id and host name; RAM and GPU names/VRAM; backend and memory pool for this boot; the running model's file name, context, GPU layers, and the result of the generation check; the top local models with their fit; and TLS status/fingerprint. It contains **no keys, paths, or chat content**. The endpoint rejects every method except GET.

The markdown does not define the rest of the JSON keys. The keys below are **copied from** `companion.report()` and the unit test on VictorLinux `main`, not invented for this plan. Units for RAM / VRAM / `size` / `generated_at` format are not specified in `companion-api.md` (open question).

```json
{
  "schema": 1,
  "pc": { "id": "<uuid-36 or m- + 32 hex, length 36 or 34>", "name": "<hostname>" },
  "generated_at": "<timestamp from the PC>",
  "hardware": {
    "ram_total": "<number>",
    "ram_available": "<number>",
    "gpus": [{ "vendor": "...", "name": "...", "vram_total": "<number>" }],
    "backend": "...",
    "backend_reason": "...",
    "memory_pool": "...",
    "memory_budget": "<number>",
    "threads_default": "<number>"
  },
  "runtime": {
    "running": true,
    "model": "<basename only, or null>",
    "backend": "...",
    "context": "<number>",
    "gpu_layers": "<number>",
    "generation_check": { "state": "verified" }
  },
  "models": [
    { "name": "...", "fit": "...", "ctx": "<number>", "verified": "...", "size": "<number>" }
  ],
  "transport": { "tls": false, "tls_fingerprint_sha256": "<64 lowercase hex or null>" },
  "privacy": "Inference stays on this PC. No telemetry, no cloud fallback. This report is read-only and contains no keys, paths or chats."
}
```

Notes taken from that same source, called out so they are not mistaken for extra contract:

- `runtime.model` is the basename of the file (the test asserts `/secret/place/model.gguf` becomes `model.gguf`). Paths must not be stored or shown if they ever slipped through.
- `pc.id` is a UUID stored in the PC's data volume, or `m-` plus a hash of `/etc/machine-id` when no data store is selected, or the string `unknown` if both fail.
- `transport.tls_fingerprint_sha256` may be `null` when the PC has no cert file.
- `generation_check` in the test is the object `{"state": "verified"}`. Other `state` values are not listed in `companion-api.md`.
- `models[].fit` is not enumerated in `companion-api.md`. A different VictorLinux page (`docs/hardware-and-models.md`) describes estimates `fits | partial | too large | unusable`; treat that as helpful context, not as a companion-API guarantee.
- The gateway test shows unauthenticated companion access returns 401, a wrong Bearer token returns 401, and `POST /victor/companion` returns 405.

## 9. Fleet bridge (open question, needs Fleet Orchestrator sign-off)

Proposal: if anything is exposed to droidcommand-AI at all, it is **read-only status** — for example "no PC added", or one of the named states in §6.1, plus non-secret display fields (host name, running model file name, TLS yes/no). No chat send, no Add/Remove, no fingerprint re-pin, and **never** the bearer key, ciphertext, or Keystore alias.

`config.export` / `config.import` must not include the key or any prefs file that holds its ciphertext. A non-secret subset (origin, display name, "a PC is saved") may be exported only if we explicitly decide that later.

This is a proposal only. It needs Fleet Orchestrator sign-off before any new `capabilities.get` / `node.status.get` field or operation is added. Default until that sign-off: **no new bridge surface**.

## 10. UI and theme

Dark neon, consistent with the rest of the app:

- Background `#0B0F1A` (already used by Deep Research and the embed viewer; charcoal in the core theme is nearby `#0C0D10` — use `#0B0F1A` on these screens as specified).
- Cyan `#00E5FF`, magenta `#FF2BD6`.
- Status colours as in the table (§6.1).

The Add form and status card are native Compose (not a WebView loaded with the gateway URL). The key must never be injected into a WebView request.

## 11. Open questions

1. **What "model changed" means.** `companion-api.md` lists it as a Check PC state and says the report includes the running model's *file name*. The integration request also mentions "model changed (not `victor-local`)." Those are different strings: chat uses the constant `victor-local`; the companion report uses a GGUF basename. This plan treats a changed file name, or a chat/`/v1/models` response that is not `victor-local`, as the same owner-facing state. Confirm with VictorLinux / Don before coding.
2. **Formal schema vs prose.** `companion-api.md` does not define field names besides `transport.tls_fingerprint_sha256`, types, units, or a list of `generation_check` / `fit` / `verified` enums. Browser code should tolerate missing optional fields and fail into **unsupported report** when `schema` or `pc.id` cannot be read. A short official schema block in `companion-api.md` would remove this gap.
3. **Plain HTTP.** The docs want TLS for anything beyond a trusted private link. Should v1 refuse `http://` outright, or allow it only when the owner confirms a trusted private link or tunnel?
4. **One PC or several?** This plan assumes one saved PC in v1.
5. **`pc.id` is `unknown`.** Should that be **unsupported report**, or pinable with a warning?
6. **Fleet bridge read-only status** — needs Fleet Orchestrator sign-off (§9).
7. **`GET /victor/health`** — unused in v1 status; confirm that is desired.
8. **Pasted fingerprint accepted forms** — recommend accepting colon-separated, spaced, or `SHA256:`-prefixed values and normalizing to 64 lowercase hex. Confirm.
9. **Chat history retention** — stay until the PC is removed, or add a clear-history control and a size cap? Recommend both a clear control and a modest cap.
10. **Gateway 401 vs 403.** Current PC software uses 401 for a bad key. The app should still treat 403 as bad key.

## 12. Test plan (unit tests; no device claims)

Implementation, when approved, should ship unit tests for at least:

- Origin matching: key is attached only when scheme, host, and port match the saved origin; rejected for a different port, host, scheme, or a redirect target.
- Pinning: a presented certificate whose SHA-256 (DER, lowercase hex) matches the pin is accepted; any other certificate is rejected; a TrustManager used here cannot be mistaken for the proxy/WebView trust store.
- Address binding: a cert that matches the pin but not the saved host/IP is rejected (the "both the pin and the address" rule).
- State mapping: fixtures for each row in §6.1 (401, 403, timeouts, pin mismatch, changed `pc.id`, changed `runtime.model`, bad/missing `schema`, 200 + valid schema 1).
- Secret hygiene: the key is absent from `config.export` fixtures, `SecretStripper` output, and any log/HAR serialization helpers; prefs used for ciphertext are not on the export allow list.
- Chat: requests use `model: "victor-local"` and the saved origin; streamed and non-streamed error bodies map to the same states.
- Tools catalog: the new id maps through `toolDestination`; the catalog tests stay green.

This plan does not claim any emulator or device run. When tests are later executed, report only what actually ran.

## 13. Phased rollout (after Don approves code)

1. **Add PC + storage + pinned TLS client** (High/M) — form, Keystore wrap, origin check, TOFU / pasted pin. Feature still hidden behind the Fleet tools entry. No chat yet.
2. **Status card + Check PC** (High/M) — companion GET, pin `pc.id`, every state in §6.1.
3. **Chat** (Med/M) — streaming completions, local history, the same error states.
4. **Fleet bridge read-only status** — only if the Fleet Orchestrator signs off.

Each phase is its own PR, tests green, and a separate merge approval from Don.

## 14. Approval

Do not implement this plan, and do not merge this documentation PR as if it were implementation approval, until Don says so in the Victorious Browser agent's own chat.
