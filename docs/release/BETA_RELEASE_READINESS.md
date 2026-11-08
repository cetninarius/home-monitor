# Home Monitor — Beta Release Readiness

**Date:** 2026-11-08  
**Candidate version:** `1.8.0-beta.2` (`versionCode` 46)  
**Proposed `0.1.0-beta.1`:** rejected — would regress below existing `1.8.0-beta1` / 45 lineage and confuse upgrades.

**Final recommendation:** `CONDITIONAL GO` → owner smoke + stable signing completed 2026-11-08; remaining gate is public publish approval only.

---

## 1. Architecture summary

Home Monitor is a **native Kotlin Android TV (Leanback) app** — **no WebView**, no Compose. UI is ViewBinding + XML layouts with a persistent overlay dashboard (`DashboardReclaimer` / shield) launched from `MainActivity`.

| Area | Implementation | Key paths |
|---|---|---|
| App ID / SDK | `com.custom.vox.homemonitor`, min 21, target 30, compile 34 | `android/app/build.gradle` |
| Layouts | HOME / MEDIA / WORKFLOW / WORKFLOW_MEDIA | `DashboardLayoutMode.kt`, `DashboardController.kt` |
| Local data | Room (`home_monitor.db`) calendar + to-do | `data/local/*`, repositories |
| Secrets | EncryptedSharedPreferences (+ private fallback) | `SecretStore.kt`, `PendingSecretGate.kt` |
| Web admin | NanoHTTPD on `0.0.0.0:8765`, foreground-only | `LocalAdminServer.kt`, `AdminAuth.kt`, `assets/admin/*` |
| Weather | Open-Meteo, fixed Belgrade coords | `WeatherRepository.kt` |
| Services | UptimeRobot + Direct HTTP | `data/services/*` |
| Now Playing | NotificationListener + MediaSession / SmartTube | `data/media/*`, `media/*` |
| WF1 | HTTP client → LAN proxy (optional, off by default) | `data/wf1/*`, `proxy/server.mjs` |
| Diagnostics | Opt-in Sentry Android SDK | `diagnostics/CrashReporting.kt` |
| CI | None in-repo | — |

**Clean-device suitability:** Yes for core dashboard + local calendar/to-do + weather, without the developer LAN. Optional integrations degrade to empty/setup states. WF1 and developer proxy are **not** required.

---

## 2. Security findings

### CRITICAL

_None remaining after hardening._

### HIGH

| ID | Finding | Evidence | Impact | Remediation status |
|---|---|---|---|---|
| H1 | Owner WF1 URL/token could bake into APK via `local.properties` | Prior debug `BuildConfig` contained LAN URL; release now forces `""` | External beta APK could seed attacker-visible proxy endpoint / shared token | **Fixed** — `buildTypes.release` overrides WF1 fields empty; verified release `BuildConfig` empty |
| H2 | No stable release signing identity in repo | `signingConfigs` optional; artifact debug-signed | Upgrade continuity / trust for public beta | **Owner action** — see §8 / `SIGNING.md` |

### MEDIUM

| ID | Finding | Evidence | Impact | Status |
|---|---|---|---|---|
| M1 | Cleartext HTTP for LAN admin + optional WF1 | `usesCleartextTraffic` + `network_security_config` | LAN attackers can sniff tokens if on-path | Accepted for beta; documented; CSRF/Origin checks + token auth remain |
| M2 | Admin binds `0.0.0.0` while dashboard resumed | `LocalAdminServer.create` | Any LAN client can reach login UI | Mitigated by token + rate limit + cookie CSRF; secrets need TV approve |
| M3 | Exported boot receiver previously accepted spoofable `QUICKBOOT_POWERON` | Manifest / `SmartTubeWarmupReceiver` | Other apps could trigger SmartTube warmup | **Fixed** — quickboot action removed |
| M4 | `SYSTEM_ALERT_WINDOW` + overlay shield | Manifest / reclaimer | Powerful permission; expected for product | Accepted; prompted once |
| M5 | Public Sentry DSN embedded | `BuildConfig.SENTRY_DSN` | Anyone can send junk events if they extract DSN | Accepted (client DSN); reporting still opt-in + scrubbed |

### LOW

| ID | Finding | Status |
|---|---|---|
| L1 | Unauthenticated `/api/health` | Accepted (minimal metadata) |
| L2 | Weather hardcodes Belgrade | Product default; document in privacy |
| L3 | Placeholder-token detector string present in DEX | Not a live credential; comparison constant only |
| L4 | `targetSdk 30` fails Play “ExpiredTargetSdkVersion” lint | Disabled for sideload beta; intentional for VOX |

### Admin trust model (verified in code)

- Auth: Bearer or session cookie after `/api/login`
- Cookie mutations require Origin/Referer host match (CSRF)
- No CORS `Access-Control-Allow-Origin` (same-origin browser use)
- Security headers added: `nosniff`, `DENY` frame, `Referrer-Policy`, `CORP same-origin`
- UptimeRobot/WF1 secrets staged via TV remote confirmation
- Server stops on activity pause

---

## 3. Sentry

| Item | Detail |
|---|---|
| Integrated? | **Yes** (minimal) |
| SDK | `io.sentry:sentry-android:8.63.0` only (no JS/WebView SDK; no Gradle plugin auto-init) |
| Project | Sentry org `radomir-perisic`, project `home-monitor` |
| Environment | `beta` |
| Release tag | `home-monitor@1.8.0-beta.2+46` |
| Consent | Settings → About → Crash reporting (**default off**) |
| Captured | Uncaught exceptions / ANRs after opt-in init |
| Excluded | Session replay, screenshots, view hierarchy, tracing/profiling sample rates, default PII, user identity; client `beforeSend` scrub + expected-noise drop |
| Test events | **Scrubbing unit tests passed** (3). Live ingest from a TV/emulator **not** run (no install without owner approval) |
| Owner config | Project already created via Sentry MCP. Optional: confirm first opt-in event in Sentry UI after a controlled debug capture |

---

## 4. Compatibility

### Supported (by design)

- Android TV / Google TV with Leanback
- API 21–30+ runtime (target 30)
- Devices without SmartTube / WF1 / UptimeRobot / root / ADB

### Tested in this audit

- JVM unit tests on macOS (JDK 17)
- Release APK package inspection (`aapt`, dex string checks, signing verify)
- **Not** re-tested on VOX hardware in this pass (install forbidden without approval)
- **Not** tested on Sony / Shield / Chromecast Google TV

### Unverified

- Alternate remotes / DPI / 4K UI scale
- Android 12+ BAL / notification listener UX differences
- Upgrade path from `1.8.0-beta1` (45) → `1.8.0-beta.2` (46) on device
- Live LAN admin from phone on non-VOX TVs

### Launcher behavior (documented)

- Exported `MAIN` + `LAUNCHER` + `LEANBACK_LAUNCHER` only — **not** `HOME`
- Exit via Back → reclaimer exit path; stock launcher remains default
- Uninstall removes app; Room data removed with app

---

## 5. Regression results

| Test | Result |
|---|---|
| Unit tests (`:app:testDebugUnitTest`) | **38 passed**, 0 failed (includes layout mode, WF1 prefs, admin auth, scrubber, Now Playing gate, calendar/todo logic, HTTP/uptime stores) |
| On-device HOME/MEDIA/WORKFLOW/WORKFLOW_MEDIA | **Not executed** this pass |
| On-device settings persistence / D-pad | **Not executed** this pass |
| Clean install / upgrade on TV | **Not executed** (needs owner approval) |

Static review: missing optional integrations already have dedicated empty/error UI paths (`Wf1QueueWidget`, services unconfigured strings, Now Playing gate tests).

---

## 6. Release artifact

| Field | Value |
|---|---|
| Version | `1.8.0-beta.2` (46) |
| Build | `./gradlew :app:assembleRelease` — **SUCCESS** |
| `debuggable` | `false` (release) |
| WF1 BuildConfig | empty URL/token in release |
| Installable APK | `docs/release/artifacts/home-monitor-v1.8.0-beta.2.apk` |
| SHA-256 (installable) | `cd78f321df39ad80b378a75d5d7f993f4b9b22a44b62dcb32b78519d0928648b` |
| Unsigned twin | `…-unsigned.apk` SHA-256 `9fbaa42c83218bc829b5535a25a04e8ed2670a5087c95f20953eae19f0460c3b` |
| Signing status | **Stable release PKCS12** (`~/Keys/home-monitor-release.p12`, alias `home-monitor`) |
| Cert SHA-256 | `6B76AD03E62706261CEEBB1ED209EB8925C9B952772520566AD1A18F323CA81A` |
| Checksums file | `docs/release/artifacts/SHA256SUMS.txt` |
| VOX smoke | Clean install on Ateko/`rtd2851a` — HOME dashboard up (screenshot `artifacts/smoke-vox-1.8.0-beta.2.png`); not debuggable; admin bound `:8765`; no FATAL |

---

## 7. Known limitations

### Accepted

- Cleartext LAN admin
- Belgrade weather default
- WF1 optional + external proxy
- targetSdk 30 (sideload)
- No automatic updater / accounts / analytics
- Debug-signed interim APK for sideload convenience

### Release blockers (for unrestricted public GO)

- Live Sentry verification after opt-in not performed on device
- Public GitHub Release / Reddit not published (by design — needs owner OK)

### Cleared 2026-11-08

- Stable PKCS12 signing identity created and used for APK
- VOX clean install smoke of `1.8.0-beta.2` (HOME layout, weather, empty calendar/todo/services)

---

## 8. Owner actions

1. **Back up** `~/Keys/home-monitor-release.p12` + `~/Keys/home-monitor-release.credentials` offline.  
2. Optionally enable crash reporting and confirm one event in Sentry `home-monitor`.  
3. Create a **public releases-only** GitHub repo; copy `docs/release/public/**`; attach APK + SHA256 — **do not** make the private source repo public.  
4. Approve Reddit / Release publish when ready (drafts unpublished).  
5. Decide whether Belgrade weather default is acceptable for external testers.  
6. Re-enter calendar/todo / WF1 / UptimeRobot on VOX if needed (clean install wiped local data).

---

## 9. Final recommendation

### `CONDITIONAL GO`

Signing + VOX smoke are done. Remaining conditions are **publish/consent** choices (Sentry live check optional; public distribution explicit approval).

Not full `GO` only because nothing has been published externally yet and Sentry end-to-end on-device was not verified.
