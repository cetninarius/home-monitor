# Install Home Monitor (sideload)

This guide is for people who are not developers. You will install an APK file onto an Android TV / Google TV.

## Before you start

- You need a way to get the APK onto the TV (USB stick, file manager download, or a phone app that can install to the TV).
- Android will warn that the app is from an unknown source. That is normal for sideloaded apps.
- Do **not** disable your TV’s overall security features beyond allowing install of this one app when prompted.

## Typical path (file manager)

1. Download `home-monitor-v1.8.0-beta.3.apk` from the GitHub Release (link in the announcement).
2. Copy it to a USB drive, or download it on the TV browser / Downloader app if you prefer.
3. On the TV, open a file manager (or “Downloads”).
4. Open the APK.
5. If Android asks to allow installs from that app/source, allow it **only for this install**.
6. Confirm Install.
7. Open **Home Monitor** from the apps list.

## First launch

1. Allow “Display over other apps” if the TV prompts (needed for the persistent dashboard overlay on some devices).
2. Note the web admin banner (URL + token) if shown — you need the token to edit calendar/to-do from your phone.
3. Optional: Settings → Integrations for SmartTube / Services; Settings → About for crash reporting opt-in.

## Upgrade

Install the newer APK over the old one (same package id). Calendar, to-do, and settings should be kept. Prefer the same signing key for upgrades; mixing debug-signed and release-signed builds can require uninstall (data loss).

## ADB (optional, for advanced users)

```bash
adb connect TV_IP:5555
adb install -r home-monitor-v1.8.0-beta.3.apk
adb shell am start -n com.custom.vox.homemonitor/.MainActivity
```

ADB is **not** required for normal beta use.

## Troubleshooting

| Symptom | What to try |
|---|---|
| Install blocked | Use the TV’s “Install unknown apps” permission for your file manager |
| App missing | Look under Apps → See all apps (Leanback row) |
| Blank / stuck | Force-stop Home Monitor, reopen; if overlay permission was denied, grant it |
| Can’t open web admin | Dashboard must be open; use the URL shown on the TV; some TVs need `adb forward` |
