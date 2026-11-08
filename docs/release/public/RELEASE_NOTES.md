# Home Monitor `1.8.0-beta.3`

**versionCode:** 47  
**Package:** `com.custom.vox.homemonitor`

## Fixes in this build

- Now Playing no longer flickers every second while playing (preserve enriched metadata across polls; avoid rebinding unchanged artwork/text)

## Features

- Android TV Leanback dashboard (clock, calendar, to-do, weather, network strip)
- Adaptive layouts: HOME / MEDIA / WORKFLOW / WORKFLOW_MEDIA
- Local web admin (token auth, foreground-only) for calendar, to-do, appearance, services, WF1 config
- Optional Service Monitoring (UptimeRobot + Direct HTTP)
- Optional SmartTube Now Playing (notification listener)
- Optional experimental WF1 Live Queue (disabled by default)
- Opt-in crash reporting (Settings → About; off by default)

## Compatibility

- Min SDK 21 / Target SDK 30
- Leanback required
- Primary hardware class: low-RAM Android TV (VOX Ateko / rtd2851a)
- Other Android TV / Google TV devices: expected to install, not fully verified

## Known issues / limitations

- LAN web admin is cleartext HTTP; treat the home network as the trust boundary
- Some TVs cannot complete HTTP to the TV LAN IP; use another device on LAN or `adb forward`
- Weather location is fixed to Belgrade in this build
- WF1 needs a separate LAN proxy you operate
- Not registered as default HOME launcher
- The project's stable release signing identity is documented in [SIGNING.md](../SIGNING.md). **Verify the actual published APK certificate** against that identity before distribution; do not publish a debug-signed artifact.

## License

Official beta APKs are free for personal, non-commercial evaluation; the source is not licensed for redistribution or derivative works. See [LICENSE.md](../../../LICENSE.md).

## Installation / upgrade

See [INSTALL.md](../../../INSTALL.md). Use `install -r` / overwrite install to keep local data when the signing identity matches.

## Feedback

Open a GitHub Issue with the template. No tokens, calendar text, or private logs.
