# VictorLinux companion

Work attribution: **Victorious / Victorious Framework**.

Victorious Browser is an Android Kotlin/Compose/WebView app. It is **not** a Linux desktop browser (the PC keeps Firefox), and `Victorious93/browser` is a different, headless Lightpanda fork. The **VictorLinux PC** tool (Tools sheet → *VictorLinux PC*, also on the fleet launcher) is the phone-side companion for a PC booted into VictorLinux.

## What it does

* Saves PC profiles (name, address, optional pinned certificate fingerprint). Profiles contain **no key**.
* *Check PC* reads the PC gateway's read-only `GET /victor/companion` report (schema 1) and shows: RAM and GPUs, backend and memory pool for this boot, the running model, context and GPU layers, whether the model has actually generated a token on that PC, and the PC's local models with estimated fit (`fits`/`partial`/`too large`) and *verified here* vs *estimate only*.
* *Chat* sends messages to the PC's local model (`/v1/chat/completions`, non-streaming). The conversation is held in memory only; nothing is stored or uploaded, and there is no cloud fallback.

## Boundaries (enforced in code and tests)

* **`127.0.0.1` on a phone is the phone.** Loopback addresses are refused unless you state the address is a tunnel you run (adb reverse / SSH forward); use the PC's LAN or VPN/mesh address otherwise. Plain `http` is only accepted for private-network IP literals and always warns.
* The PC's administrative Control Center stays on the PC's `127.0.0.1:8787` with a local token. The phone only talks to the inference gateway; the report has no keys, paths or chat content and is GET-only.
* **Keys** are stored with the existing Android Keystore AES-GCM `SecretBox`, together with the origin they were saved for, and are returned only for that exact origin. Editing a profile's address makes the old key unusable (and drops the pinned PC identity). Keys never appear in exports (`ProfileExport` omits them), URLs, history or logs, and nothing injects them into WebViews or userscripts: the companion uses its own HTTP client, not a WebView.
* **TLS**: no trust-all and no ignored SSL errors anywhere in this feature. Without a pin the platform's normal CA and host-name validation apply. For a self-signed PC you paste the certificate's SHA-256 (printed by `victor-tls-cert`, or shown in the report); the app then accepts *that exact certificate*, within its dates, **and still requires the certificate to be valid for the address**. A different certificate, a mismatching host name, or a redirect is refused and the key is not sent elsewhere (redirects are never followed).
* The first successful check pins the PC's identity. A different PC at the same address shows *A different PC answered* and nothing is sent until you explicitly re-pair.

## States you will see

Online · Offline (tunnel down / refused / timeout / unknown name) · Bad key (rotated or revoked) · Wrong host or certificate problem · Different PC (changed id) · Model changed (note) · Unsupported report.

## Setup

1. PC: Control Center → Remote access → enable, create a key (and `victor-tls-cert <pc-address>` + TLS if not on a trusted private link).
2. Phone: Tools → VictorLinux PC → Add a PC → address, key, (self-signed) certificate fingerprint → Save → *Check PC*.

## Verification status

Unit tests (JVM) cover the origin policy, key binding, exports, report parsing, and the client against a local HTTP server and a local HTTPS server with generated certificates (self-signed rejected, correct pin accepted, wrong pin rejected, another PC's certificate rejected, host-name mismatch rejected even with a matching pin, redirects not followed, bad key, tunnel down, changed PC, changed model, port change). See the VictorLinux repo's `docs/2026-11-14-victorlinux-session-results.md` for the observed counts. **Not done:** emulator or device run of the Compose screen, rotation/process-death lifecycle checks, streaming chat, and a run against a real VictorLinux PC.

Pre-existing note: `msf/MsfRpcClient.kt` contains its own permissive TLS socket factory for the Metasploit console feature. It is unrelated to this feature, was left unchanged, and should be reviewed separately.
