# Calendar & To-do (Phase 2)

## Source of truth

Room database `home_monitor.db` on the TV:

- `calendar_events`
- `todo_tasks`

TV widgets and the web admin share the same repositories. Mutations from the admin API emit through Room `Flow` and refresh the dashboard automatically while the app is open.

## Web admin

- Intended URL: `http://192.168.0.56:8765` (only while Home Monitor is in the foreground)
- Auth: admin token (generated once) → session cookie for browsers, or `Authorization: Bearer <token>` for CLI
- Token is shown on the first-run TV banner and logged once as `HomeMonitorAdmin`
- No shell/ADB/filesystem/WF1 credential endpoints

### Verified access on VOX (2026-11-07)

Direct LAN HTTP from the Mac **times out** after TCP connect on this VOX firmware. Verified working path for Cursor/CLI/browser-from-Mac:

```bash
adb -s 192.168.0.56:5555 forward tcp:8765 tcp:8765
open http://127.0.0.1:8765
```

Use the token from the TV banner.

## Cursor / CLI

```bash
adb -s 192.168.0.56:5555 forward tcp:8765 tcp:8765
export HM_TV=http://127.0.0.1:8765
export HM_TOKEN='…'

./scripts/hm-cli.sh event add --date 2026-11-12 --time 15:00 --title "Dentist"
./scripts/hm-cli.sh task add --priority high --title "Renew domain"
./scripts/hm-cli.sh task complete --match grocery
./scripts/hm-cli.sh tasks
./scripts/hm-cli.sh events --from today
```

Do not edit the SQLite file while the app is running.

## TV widgets

- **Calendar** — today’s events (≤3) + optional next-up line
- **To-do** — ≤5 pending tasks; OK/center completes the focused task

## Adding data without rebuild

Use the web admin or `hm-cli.sh`. An APK rebuild is only needed for code/UI changes.
