# Privacy — Home Monitor beta

Last updated for `1.8.0-beta.3`.

## What stays on your TV

| Data | Storage | Leaves the device? |
|---|---|---|
| Calendar events | Local Room database | Only if **you** open web admin and use it on another device on your LAN |
| To-do tasks | Local Room database | Same as calendar |
| Appearance prefs / custom backgrounds | Local prefs + app files | Custom images may be uploaded via LAN web admin |
| Admin token / sessions | Local prefs (token); sessions in memory | Token shown on TV; you type it into the admin UI |
| UptimeRobot API key / WF1 token | EncryptedSharedPreferences when available | Sent only to UptimeRobot or your WF1 proxy when configured |
| Dashboard widget toggles | Local prefs | No |
| HTTP monitor URLs | Local prefs | TV performs outbound GET checks to those URLs |

## Network destinations (when features are used)

| Destination | Purpose | Payload |
|---|---|---|
| `api.open-meteo.com` | Weather | Fixed coordinates for Belgrade (this build) |
| UptimeRobot API | Service monitors | API key + monitor queries (only if configured) |
| User-defined HTTP URLs | Direct checks | GET only; no Home Monitor credentials attached |
| YouTube oEmbed (optional) | Now Playing artwork | Public video id metadata |
| Your WF1 proxy URL | Experimental queue | Bearer token you configured; sanitized queue JSON |
| Sentry (`ingest.de.sentry.io`) | Crash diagnostics | **Only if** you enable Crash reporting in Settings → About |

## Crash reporting (opt-in)

- **Default: off**
- When enabled, unexpected crashes / ANRs may be sent to the Home Monitor Sentry project (`environment=beta`)
- No user accounts, no session replay, no screenshots, no product analytics
- Client-side scrubbing removes private IPs, tokens, Authorization headers, and filters expected “optional integration missing” states
- Calendar titles, to-do text, WF1 payloads, and media metadata are not intentionally collected; scrubbing also strips sensitive-looking fields if they appear in error text

Turn off anytime: Settings → About → Crash reporting.

## What we do not do

- No accounts / cloud sync of calendar or to-do
- No advertising ID collection for ads
- No automatic updater phoning home for APKs
- Web admin does not run when the dashboard is not in the foreground

## Contact

Use the public releases repository Issues for privacy questions related to the beta.
