# Services & Weather (Phase 3)

## Weather (Belgrade)

- Provider: [Open-Meteo](https://open-meteo.com/) — no API key
- Location: Belgrade (`44.8176, 20.4633`), timezone `Europe/Belgrade`
- Refresh: ~25 minutes while the dashboard is resumed
- Offline: keeps last snapshot and marks **Stale**

## Services (UptimeRobot)

- Read-only `getMonitors` with a user-supplied API key
- **Key is never in the APK / BuildConfig / git**
- Configure via web admin → **Services** tab (`POST /api/uptime/config`)
- Required: `apiKey`, `monitorIdA`, `monitorIdB`, optional labels
- Until configured, the TV card shows: **Configure UptimeRobot in web admin**
- Refresh: ~60 seconds while resumed; UP becomes STALE if refresh is too old

### Configure

```bash
adb forward tcp:8765 tcp:8765
# Open http://127.0.0.1:8765 → Services
# Paste API key + two monitor IDs (e.g. Sudoku Gym, Kliker CRM)
```
