# VictorLinux companion integration

Work attribution: **Victorious / Victorious Framework**. Inspected repository: `Victorious93/victorious-browser`, base `3781a9afcaeff7861acb190c32aee11576902333`.

This is an Android Kotlin/Compose/WebView browser. It has editable fleet panels (`panels/PanelStore.kt`), panel types (`Panel.kt`), mesh reachability checks, and a fleet bridge. It is **not** a native Linux desktop browser. `Victorious93/browser` is a separate headless Lightpanda fork, not this app.

## Recommended role

Add an explicit **VictorLinux workspace** companion entry. Show the PC identity/address, whether it is reachable, its available inference backend, imported models and estimated model fits, then let the owner open the authenticated chat/API. Save a device-specific endpoint profile; do not mistake a phone's `127.0.0.1` for the PC's loopback address.

The PC must continue using Firefox for the local desktop Control Center unless a real desktop browser package is independently implemented. Do not try to install this APK as the PC's Linux browser.

## Connection boundaries

- The administrative Control Center stays on PC loopback (`127.0.0.1:8787`) with a local per-session token. Do not publish that listener or put its token in a remote browser panel URL.
- Use the existing separately authenticated inference listener for remote inference, over the owner's configured private LAN/VPN. Read-only status/model-fit data requires an authenticated endpoint or an explicit trusted tunnel/bridge.
- Store native connection credentials with Keystore-backed protection, scoped to their saved origin. Do not put them in URL queries, exported panel JSON, history, logs, arbitrary userscripts or shared WebView JavaScript.
- Never solve TLS problems by ignoring all certificate errors. Verify the saved host/certificate or use a properly configured trusted private tunnel.
- Existing network inspection/recon features must not automatically inspect, export or replay inference credentials or chat traffic. Capture for this profile must be explicit and scoped.
- A disconnected PC should produce an actionable offline/tunnel-down state. Never silently switch the chat to a cloud provider.

## Tests required for implementation

Check Android unit tests, lint and APK build, then real/emulated flows for profile creation, bad key, wrong origin, self-signed/untrusted certificates, tunnel down, PC address changes, model changes, local/cloud routing and lifecycle restoration. The existing browser app was inspected; this kit supplies integration documentation, not a tested Android companion implementation.
