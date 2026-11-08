# Home Monitor BF3 private beta

**Version:** `1.8.0-beta.2` (`versionCode` 46) — see also `docs/release/BETA_RELEASE_READINESS.md`  
**Application ID:** `com.custom.vox.homemonitor`  
**Minimum Android:** API 21 (Android 5.0). Target API 30. **API 28 (Android 9) is in range** but not verified on a Sony Bravia.

## Artifact

Build from the repo (do not publish automatically):

```bash
cd android
export JAVA_HOME="/path/to/jdk-17"   # Gradle 8.2 needs JDK 17

# Private beta for external testers — force empty WF1 BuildConfig (no owner LAN URL/token):
./gradlew :app:assembleDebug -Pwf1.proxy.baseUrl= -Pwf1.proxy.token=
# APK: app/build/outputs/apk/debug/app-debug.apk

# Owner upgrade build that may seed WF1 from local.properties:
# ./gradlew :app:assembleDebug
```

Release unsigned (no signingConfigs configured — do not fabricate keystores):

```bash
./gradlew :app:assembleRelease -Pwf1.proxy.baseUrl= -Pwf1.proxy.token=
# APK: app/build/outputs/apk/release/app-release-unsigned.apk
```

Private beta may use the **debug** APK for sideload. Signing for upgrade-compatible release builds must use the owner’s existing keystore when available — not committed to git.

## Installation (beta tester)

1. Enable unknown sources / ADB sideload on the Android TV.
2. Install the APK (USB, network ADB, or file manager).
3. Launch **Home Monitor** from the apps list (do **not** set as default HOME).
4. Complete first-run: note the web admin URL + token on the TV.
5. On a phone/computer on the same LAN, open the admin URL, sign in with the token, manage Calendar / To-do / appearance.
6. Optionally configure Service Monitoring, SmartTube, or experimental WF1 Engine.

No ADB is required for normal setup after the APK is installed.

## Clean-install defaults

- WF1 **disabled**; no proxy URL/token bundled
- Services + Now Playing widgets enabled (configure integrations as needed)
- Admin token generated at first launch (not embedded in the APK)
- SmartTube Settings → Open SmartTube triggers a **one-time** easter egg on fresh installs only

## Upgrade safety

Existing installs migrate once:

- Owner WF1 BuildConfig URL/token (when present in the upgrade APK build) seeds runtime config and keeps WF1 enabled
- SmartTube easter egg is marked consumed so established Open SmartTube behavior is unchanged
- Calendar, To-do, appearance, Services, and admin data are preserved

**Do not deploy to the owner VOX TV without explicit approval.**

## Security notes

- LAN admin is **cleartext HTTP** on the home network. Treat LAN devices as the trust boundary.
- Writable `/api/*` requires Bearer or session cookie. Fresh installs are not open write APIs.
- UptimeRobot and WF1 tokens are staged via TV remote confirmation (`PendingSecretGate`).
- Secrets are not returned by GET endpoints.
- Appearance custom IDs are sanitized against path traversal.

## Known limitations

- No TLS for LAN admin
- WF1 requires a separate Home Monitor proxy on the LAN
- SmartTube video URL intents vary by SmartTube version; egg falls back to normal launch
- Android 9 / Sony Bravia not physically verified in this release
- Release signing not configured in-repo
- Low-end VOX (~1.5 GB RAM) remains the primary tested hardware class

## Visual QA

Capture screenshots under `docs/bf3-qa/` for:

- Core dashboard (WF1 off)
- Services on/off
- Now Playing active
- WF1 enabled connected / disconnected
- Settings Integrations (WF1 Experimental)
