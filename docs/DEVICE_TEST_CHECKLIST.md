# Device test checklist

Nothing in this repository has been run on a physical device by CI. Unit tests (JVM) and
`assembleDebug` pass; the items below need a real phone (Android 8.0+, minSdk 26) and are the
ones the framework guide marks as unverified. Tick each one and note the device + Android version.

Build: `./gradlew assembleDebug` -> `app/build/outputs/apk/debug/app-debug.apk`, then `adb install -r`.

## Smoke
- [ ] App starts; the Home surface, ring logo and fleet menu open (confirms the new `VictoriousApp` class loads).
- [ ] Open a page over https and one over http; tabs, back/forward, bookmarks and downloads work.

## Privacy / lists
- [ ] Privacy protections -> turn on tracker blocking; load a page with known trackers; the dashboard counts them.
- [ ] `a.github.io` and `b.github.io` are treated as different sites (a tracker on one is third-party on the other).
- [ ] Tracker list update: with a valid https URL, *Update now* reports N hosts; kill and restart the app; the update is still applied. With an `http://` URL or a bad file it fails with a message and the bundled list still works. *Remove update* resets.
- [ ] Backups: `adb shell dumpsys backup` / `adb backup com.victorious.browser` yields no app data (allowBackup is false).

## Proxy and CA
- [ ] Start the intercepting proxy; HTTPS history shows decoded requests for this app's WebView only.
- [ ] Modify-and-replay, cookies, JWT decode, console, storage and HAR export work.

## Never run on a device before (guide: "unverified")
- [ ] Sandboxes (`ProfileStore` / `WebViewFeature.MULTI_PROFILE`): create, switch, delete; cookies are isolated.
- [ ] WireGuard: import a profile, VPN consent prompt, connect/disconnect. Confirm what happens to the Victorious Mesh VPN when this one connects.
- [ ] Headscale: list nodes and create a pre-auth key against a real server. With a public `http://` URL it must refuse; with `https://` or a LAN/ts.net address it must work.
- [ ] URL scan with a valid VirusTotal key and a valid URLhaus Auth-Key (success paths).
- [ ] Metasploit console against a real msfrpcd on the LAN (SSL and non-SSL); non-SSL to a public host must refuse.
- [ ] Fleet export: against a real endpoint on LAN (works), and `http://` to a public host (refused). The request contract with HackerAI-Commander / Pentest-Swarm-AI is still unconfirmed; the sink is labelled experimental.

## Release gate
- [ ] All boxes above ticked or explicitly waived, then tag a pre-release.
