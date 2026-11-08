# Release signing (owner-controlled)

No keystore is committed to git. Passwords live only on this machine.

## Active identity (created 2026-11-08)

| | |
|---|---|
| Keystore | `~/Keys/home-monitor-release.p12` (PKCS12) |
| Alias | `home-monitor` |
| Validity | ~10000 days (~2054) |
| Cert SHA-256 | `6B:76:AD:03:E6:27:06:26:1C:EE:BB:1E:D2:09:EB:89:25:C9:B9:52:77:25:20:56:6A:D1:A1:8F:32:3C:A8:1A` |
| Credentials file | `~/Keys/home-monitor-release.credentials` (mode 600) |

**Back up** `home-monitor-release.p12` + `home-monitor-release.credentials` offline. Losing them means you cannot update the same package without forcing uninstall.

Gradle also reads the same values from gitignored `android/local.properties` / `android/keystore.properties` on this Mac.

## Build a signed release

```bash
export JAVA_HOME="/path/to/jdk-17"
set -a && source ~/Keys/home-monitor-release.credentials && set +a
cd android
./gradlew :app:assembleRelease -Pwf1.proxy.baseUrl= -Pwf1.proxy.token=
# → app/build/outputs/apk/release/app-release.apk
```

## Warning

Future public updates **must** use this same signing identity. Do not generate a second “temporary” release key for distribution.

## History

- Earlier interim APKs used the Android **debug** keystore. VOX was wiped once (uninstall) to migrate to this release identity for `1.8.0-beta.2`.
