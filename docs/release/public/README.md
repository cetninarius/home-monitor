# Home Monitor (public beta)

Home Monitor is an independently developed Android TV dashboard. It shows a living-room home screen with clock, weather, calendar, to-do, optional service checks, optional SmartTube “Now Playing”, and an experimental local WF1 queue widget.

It is **not** affiliated with VOX, TitanOS, Google, YouTube, or SmartTube.

## Supported devices and limitations

| | |
|---|---|
| **Intended for** | Android TV / Google TV with Leanback launcher support |
| **Min Android** | API 21 (Android 5.0) |
| **Primary tested** | VOX UHD 43A11U314B (Ateko / `rtd2851a`) |
| **Not a stock HOME replacement** | Install from the apps list. Do **not** set as default Home unless you know how to recover |
| **Optional** | SmartTube, WF1 proxy, UptimeRobot, Direct HTTP checks |

Unverified on most third-party brands (Sony Bravia, Chromecast with Google TV, Nvidia Shield, etc.). Expect layout and focus quirks on untested remotes/resolutions.

## Main features

- Fullscreen dashboard with four adaptive layouts: HOME, MEDIA, WORKFLOW, WORKFLOW_MEDIA
- Local calendar and to-do (edited via on-TV web admin)
- Weather (Open-Meteo; default location Belgrade in this build)
- Service Monitoring (UptimeRobot and/or Direct HTTP)
- Now Playing (SmartTube MediaSession when notification access is granted)
- Experimental WF1 Live Queue (off by default; needs a LAN proxy you run yourself)
- Settings on the TV remote (D-pad)

## Screenshots

Place screenshots or a short video here when publishing the public releases repo:

- `screenshots/home.png`
- `screenshots/media.png`
- `screenshots/settings.png`
- `video/demo.mp4` (optional)

Development captures also exist in the private source tree under `docs/bf3-qa/`.

## Installation

See **[INSTALL.md](INSTALL.md)** for sideload steps (no developer tools required for most TVs).

APK (when published): `home-monitor-v1.8.0-beta.3.apk`

## Optional integrations

1. Open Home Monitor → Settings → Integrations / About as needed.
2. While the dashboard is open, note the web admin URL and token on the TV.
3. On a phone/computer on the same Wi‑Fi, open that URL and sign in with the token.
4. Configure calendar, to-do, appearance, UptimeRobot, HTTP checks, or WF1 URL/token.
5. Secrets (UptimeRobot / WF1 tokens) require **Approve** on the TV remote.

WF1 also needs the read-only Home Monitor proxy on a computer on your LAN (not shipped inside the APK).

## Return to the stock launcher

- Press **Back** in Home Monitor and confirm exit when prompted, **or**
- Use the TV’s Apps / Inputs / Home button to open the original launcher.

Home Monitor does **not** register itself as the system HOME activity in this beta.

## Uninstall

Android TV Settings → Apps → Home Monitor → Uninstall  
(or long-press the app icon in the apps row → Uninstall, depending on device).

## Report bugs

Use the GitHub Issue template in this releases repository. Include TV brand/model, Android version, app version, and steps to reproduce. Do **not** paste admin tokens, API keys, calendar/to-do text, or private logs.

## Privacy and diagnostics

See **[PRIVACY.md](PRIVACY.md)**. Crash reporting is **off by default** and can be enabled under Settings → About.
