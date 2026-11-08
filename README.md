# Home Monitor

Lightweight Android TV dashboard for the VOX living-room TV.

## What you get

1. Standalone Leanback app **Home Monitor** (`com.custom.vox.homemonitor`)
2. Baghdad fullscreen background + 24h clock/date
3. **Calendar**, **To-do**, **Weather**, optional **Services** / **Now Playing**
4. Optional experimental **WF1 Live Queue** (off by default on fresh installs)
5. Local **web admin** on the TV (`:8765`, foreground-only, token auth)
6. Mac-side read-only WF1 proxy (`proxy/`) when using WF1
7. Cursor-friendly CLI (`scripts/hm-cli.sh`)

## Requirements

- VOX TV on LAN with network ADB (`192.168.0.56:5555`, Ateko / `rtd2851a`)
- Mac host running WF1 monitor on `127.0.0.1:8791` (for WF1 widget)
- Android SDK + JDK 17
- Node 18+ for the WF1 proxy

## Configure

1. Copy `android/local.properties.example` → `android/local.properties`
2. Set `sdk.dir`. Optionally set `wf1.proxy.baseUrl` / `wf1.proxy.token` to seed owner upgrades
3. Start the WF1 proxy only if you use the experimental WF1 widget:

```bash
cd proxy
HM_TOKEN='your-strong-token' node server.mjs
```

## Build & install

```bash
cd android
./gradlew :app:assembleDebug

export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
S=192.168.0.56:5555
adb connect "$S"
adb -s "$S" shell getprop ro.hardware   # expect rtd2851a
adb -s "$S" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$S" shell am start -n com.custom.vox.homemonitor/.MainActivity
```

Use `install -r` so Room data is preserved. Do not set Home Monitor as HOME.

## Web admin (calendar & to-do)

While Home Monitor is open on the TV:

1. Note the banner URL and first-run admin token
2. On this VOX, open via ADB forward (direct LAN HTTP hangs after TCP connect):

```bash
adb -s 192.168.0.56:5555 forward tcp:8765 tcp:8765
# then browse http://127.0.0.1:8765
```

3. Sign in with the token (never embedded in page source)

The admin server **stops when the app leaves the foreground**.

## Cursor / CLI

```bash
adb -s 192.168.0.56:5555 forward tcp:8765 tcp:8765
export HM_TV=http://127.0.0.1:8765
export HM_TOKEN='…'   # from TV banner / logcat tag HomeMonitorAdmin

./scripts/hm-cli.sh event add --date 2026-11-12 --time 15:00 --title "Dentist"
./scripts/hm-cli.sh task add --priority high --title "Renew domain"
./scripts/hm-cli.sh task complete --match grocery
```

## Docs

- [Architecture](docs/architecture.md)
- [Beta release readiness](docs/release/BETA_RELEASE_READINESS.md)
- [Public distribution drafts](docs/release/public/)
- [BF3 private beta release](docs/bf3-release.md)
- [WF1 integration](docs/wf1-integration.md)
- [Calendar & To-do](docs/calendar-todo.md)
- [Services & Weather](docs/services-weather.md)
- [AGENTS.md](AGENTS.md)

## Known limitations

- Admin HTTP is cleartext + token; no TLS in this phase
- Admin only while the dashboard activity is resumed
- On VOX UHD 43A11U314B, direct `http://192.168.0.56:8765` TCP connects but HTTP does not complete; use `adb forward` → `http://127.0.0.1:8765` (verified)
- WF1 proxy must run on the Mac; TV never talks to `:8791`
- No recurring calendar events yet

## Non-goals

Weather, server monitoring, smart home, Google Calendar sync, widget drag-editor, WF1 control from the TV.
