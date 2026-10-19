# Victorious Browser

Fleet security / recon browser for the Victorious fleet.

This repository is early architecture-neutral groundwork: a Kotlin + Jetpack Compose
Android app shell with a fixed debug keystore, dark brand theme, and CI that builds
`assembleDebug`.

## Status

- **Shell engine** (WebView / Chromium / custom) — *pending approval*
- **Proxy / MITM library** — *pending approval*

Once those picks land, they will be documented here with full third-party license
attributions (see `NOTICE`).


## Browser shell

The browsing surface is a single system **WebView** hosted in Jetpack Compose via
`AndroidView` (no embedded Chromium / Gecko binary).

- Address bar + Go, Back, Forward, Reload, and a load-progress indicator
- Custom `WebViewClient` / `WebChromeClient` keep http(s) navigation in-app
- Recon-oriented defaults: JavaScript + DOM storage on, cookies via `CookieManager`,
  mixed content set to `MIXED_CONTENT_COMPATIBILITY_MODE` (noted in code)
- Optional Desktop / Mobile UA toggle
- `androidx.webkit` is on the classpath for upcoming `ProxyController` use; a
  clearly marked `TODO(proxy)` hook lives in `BrowserSettings.kt` — **proxy is not
  wired in this layer yet**

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
