# Privacy — Home Monitor beta

**Updated:** November 2026. This is a description of the current implementation, not a promise about unverified third-party apps or Android TV firmware.

Home Monitor has no accounts, subscriptions, advertising analytics or built-in cloud calendar sync.

## Data stored locally

Calendar events and to-do items are stored in a local Room database on the TV. Appearance settings, backgrounds, widget preferences and service endpoints are also stored locally. Optional integration credentials are stored in app-private storage, using Android encrypted preferences where available.

The local admin web interface allows a device on the same network to edit information **only when Home Monitor is in the foreground** and the client is authorized. Requests use HTTP on port `8765`, **not HTTPS**. Do not expose this port to the internet or use it on an untrusted network. The admin token must be kept secret.

## External services (only when applicable)

| Service | Data involved | When |
|---|---|---|
| Open-Meteo | Weather lookup for configured/default location (currently Belgrade in this build) | Weather enabled |
| UptimeRobot | API key and monitor query | Only if configured |
| User-entered HTTP endpoints | HTTP GET requests to chosen URLs | Only if configured |
| YouTube oEmbed | Video identifier for public artwork/metadata | When enrichment is used |
| Local WF1 proxy | Configured bearer token and read-only queue request | Only if configured |
| Sentry | Diagnostic crash/ANR details | **Only when enabled** in app settings |

## Crash diagnostics

Crash reporting is **off by default** and can be toggled in Settings → About.

When enabled, Sentry may receive exception and ANR diagnostics. The app is configured without screen replay, screenshots, analytics, default personally identifying user data or profiling. The app also attempts to redact tokens, private IPs and sensitive fields in error reports. **Automatic redaction is not a guarantee**: avoid placing secrets in diagnostic text and report suspected privacy issues privately rather than in a public Issue.

## Permissions and capabilities

Depending on Android version and enabled features, Home Monitor may request overlay/display-over-other-apps permission, notification listener access (for compatible media sessions), internet access and boot-related capabilities. These do not grant access to your Netflix account.

## Data deletion

Uninstalling the app normally removes its app-private local database and settings. Android/TV backups or external service data, if any, are governed separately by the platform/provider.

For questions about this beta, use the repository Issues **without including sensitive data**.
