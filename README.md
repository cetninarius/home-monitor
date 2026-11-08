# Home Monitor

**A customizable home dashboard for Android TV and Google TV.**

Home Monitor is an independent, lightweight Android TV app that turns your TV into a glanceable screen with time, weather, calendar, tasks, and optional integrations. It is designed for D-pad remote controls and low-resource devices.

> **Early public beta.** The app has been developed and tested primarily on a VOX UHD 43A11U314B (Ateko / rtd2851a). Compatibility and behavior on other Android TV / Google TV devices are not yet verified. This app is **not** a replacement for your TV firmware or the default system launcher.

## Features

- Fullscreen time, date, weather and interchangeable scenic backgrounds
- Local calendar and to-do list, with a browser-based admin interface
- Optional service health monitoring (UptimeRobot or direct HTTP checks)
- Optional SmartTube *Now Playing* integration (requires notification access and compatible media sessions)
- Optional **WF1 Live Queue**: a preview of tasks from the developer's separate AI workflow system, or your own compatible self-hosted proxy; **not needed** to use the app
- TV remote (D-pad) navigation and adaptive layouts
- Optional opt-in crash diagnostics; **off by default**

No account, subscriptions, ads or telemetry-based product analytics are required.

## Download and install

**[GitHub Releases](https://github.com/cetninarius/home-monitor/releases)** — look for an APK attachment in the newest published release.

**Important:** At the time this README was updated, no GitHub Release with an APK was visible in this repository. If the Releases page is empty, there is **nothing to download yet**. Do not install APKs from unrelated third-party sites claiming to represent this project.

Follow the [installation and first-run guide](INSTALL.md) for USB/file-manager sideloading, optional ADB, upgrades and troubleshooting. **Root and ADB are not required for ordinary installation.**

If an official release includes `SHA256SUMS.txt`, use it to verify the downloaded APK before installing.

## First-time setup

1. Open **Home Monitor** from your TV's Apps screen. It does not register as your default HOME app.
2. Use the remote to open **Settings** and choose a background and visible widgets.
3. To edit local calendar or to-do items, open the on-TV **web admin** URL from a browser on your local network and enter the token displayed on the TV.
4. Some TVs may not allow direct LAN web-admin access; advanced users can use `adb forward` as described in [INSTALL.md](INSTALL.md).
5. Configure optional services only if you use them. Missing SmartTube, WF1 or monitoring integrations should not prevent using the core dashboard.

**Security:** The web admin runs on your local network over plain HTTP while the app is in the foreground. It uses a token, but traffic is **not encrypted**. Use it only on a trusted home network; never expose port `8765` to the public internet or forward it on your router. Keep the admin token private.

## Device support

| Property | Status |
|---|---|
| Platform | Android TV / Google TV with Leanback support |
| Minimum Android version | Android 5.0 (API 21) |
| Primary tested device | VOX UHD 43A11U314B, Ateko / rtd2851a |
| Other TVs and remotes | Experimental / tester feedback welcome |
| Default launcher replacement | **No** |
| Root / ADB for normal use | **No** |
| SmartTube / WF1 | Optional |

## Feedback and bug reports

Found an issue? [Open a GitHub Issue](https://github.com/cetninarius/home-monitor/issues). Include your TV make/model, Android version, app version, steps to reproduce, expected behavior and what happened.

**Do not include** API keys, local admin tokens, personal calendar/to-do content, account details, private IP addresses, or raw logs with sensitive information.

For data handling, see [PRIVACY.md](PRIVACY.md).

## Known limitations

- Some Android TV vendors restrict notification access, overlays, background processes or third-party apps.
- The local browser admin may be unreachable over a TV's LAN IP despite the TV having network access. ADB port forwarding is an advanced workaround.
- Weather currently defaults to Belgrade; location selection is not yet generally available.
- No recurring calendar events or cloud calendar sync.
- WF1 requires a separately hosted, authorized read-only LAN proxy; it does not ship with the APK.
- Direct LAN admin uses HTTP, not HTTPS.
- Not a stock launcher replacement and not a Netflix/streaming player.

## Developers

Native Kotlin / XML + Android Leanback project. The app source is in [`android/`](android/), with optional WF1 proxy in [`proxy/`](proxy/).

```bash
cd android
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Requires a suitable Android SDK and JDK 17. The debug APK is for development, **not** the signed beta release. Keep keystores, local properties and credentials outside version control. See [signing notes](docs/release/SIGNING.md).

This repository is publicly readable. **No open-source license has been selected yet**; public availability of source code does not grant unrestricted redistribution or modification rights. That is an open maintainer decision.

## Credits and independence

Independent project, not affiliated with VOX, Google, YouTube, SmartTube, or the developers of optional third-party services.
