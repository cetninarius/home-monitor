# Publish Home Monitor v1.8.0-beta.3

**Status:** release preparation only. This document does not indicate an APK has been uploaded or published.

## Release metadata

- **Tag:** `v1.8.0-beta.3` (create on publication; tag the commit that matches the actual APK source)
- **Title:** `Home Monitor v1.8.0-beta.3 — Public Beta`
- **Type:** GitHub **Pre-release** (not Latest stable)
- **Android package:** `com.custom.vox.homemonitor`
- **versionName:** `1.8.0-beta.3`
- **versionCode:** `47`
- **Recommended files:** `home-monitor-v1.8.0-beta.3.apk` and `SHA256SUMS.txt`
- **Official downloads:** https://github.com/cetninarius/home-monitor/releases

## Do this on your Mac before publishing

1. Use the **signed release** APK, not `app-debug.apk` or `*-unsigned.apk`. The current build's release signing configuration is described in [SIGNING.md](release/SIGNING.md). The signing keys/credentials must stay on your Mac and must not be uploaded.
2. Check `versionName=1.8.0-beta.3`, `versionCode=47`, `applicationId=com.custom.vox.homemonitor` using Android SDK `aapt dump badging` or `apkanalyzer`.
3. Run Android SDK `apksigner verify --verbose --print-certs APK_FILE` and confirm:
   - APK verifies (signed);
   - certificate SHA-256 equals `6B76AD03E62706261CEEBB1ED209EB8925C9B952772520566AD1A18F323CA81A` (documented release identity);
   - not a debug certificate / debug build;
   - it can upgrade the previous public release signed with the same identity.
4. Generate `SHA256SUMS.txt` for the **exact file you're uploading**, not an earlier build.
5. Inspect APK `BuildConfig` / decompiled constants for accidentally embedded owner-only WF1 URLs/tokens; release configuration should force them empty. A Sentry DSN is a public client identifier, not a secret token.
6. Smoke-test the **exact APK you're uploading** on your own TV, including clean start, D-pad, appearance, missing optional WF1, settings and exit. Never flash system partitions or alter HOME registration.
7. Make sure the source commit being tagged is compatible with the compiled artifact. If you built the APK before subsequent code changes, don't imply it was built from the newer commit; record actual source revision.

### Quick verification commands (macOS)

Run in the directory containing the final APK and Android SDK tools (replace `<path-to-build-tools>` as appropriate):

```bash
APK="home-monitor-v1.8.0-beta.3.apk"
test -s "$APK" || exit 1

# Use an Android SDK Build Tools version installed on your Mac:
SDK_TOOLS="$HOME/Library/Android/sdk/build-tools/35.0.0"
"$SDK_TOOLS/apksigner" verify --verbose --print-certs "$APK"
"$SDK_TOOLS/aapt" dump badging "$APK" | head -n 8

# Check the certificate fingerprint against SIGNING.md.
shasum -a 256 "$APK" | tee SHA256SUMS.txt
cat SHA256SUMS.txt
```

Change `35.0.0` to the installed Build Tools version if necessary. **Do not paste keystore passwords or credential file contents into a GitHub issue, release description or chat.**

## Publish through GitHub UI

1. Visit https://github.com/cetninarius/home-monitor/releases/new
2. Choose **Create new tag** `v1.8.0-beta.3` on the correct source revision.
3. Set the title to `Home Monitor v1.8.0-beta.3 — Public Beta`.
4. Paste the release description below.
5. Attach the signed `.apk` and `SHA256SUMS.txt`. Do not attach `.p12`, `.credentials`, keystores, unsigned files, or internal diagnostic archives.
6. Check **Set as a pre-release**.
7. Publish the release.
8. Open the public release link in a private/incognito window and confirm both attachments download successfully. Verify the public APK hash against the uploaded checksum.
9. Share the **release URL** on Reddit, not a random mirror: https://github.com/cetninarius/home-monitor/releases/tag/v1.8.0-beta.3

## Release description — copy/paste

### Home Monitor v1.8.0-beta.3 — Public Beta

A lightweight, customizable Android TV dashboard with a clock, weather, local calendar and to-do list, and optional integrations.

**Features**
- Scenic backgrounds and adaptive TV layouts
- Calendar and to-do with local web admin
- Optional service status monitoring
- Optional SmartTube Now Playing
- Optional experimental WF1 queue view (requires your own compatible proxy)
- D-pad controls; opt-in crash diagnostics (off by default)

**What's improved**
- Fixed recurring Now Playing flicker while media is playing.

**Compatibility**
- Android TV / Google TV with Leanback; Android 5.0+ (API 21)
- Tested primarily on VOX UHD 43A11U314B (Ateko / rtd2851a)
- Other TVs are **experimental**, and may have remote, overlay or layout differences
- Not a replacement for the system launcher; no root or ADB required for regular installation

**Install**
Download the signed `home-monitor-v1.8.0-beta.3.apk` attached below, follow the [installation guide](https://github.com/cetninarius/home-monitor/blob/main/INSTALL.md), and optionally verify its SHA-256 using `SHA256SUMS.txt`.

**Known limitations**
- Weather defaults to Belgrade; location selection isn't yet available
- LAN web admin uses cleartext HTTP on a trusted home network; never expose it to the public internet
- Some TVs require ADB port forwarding to reach the local admin page
- No recurring calendar events or cloud sync
- SmartTube, services and WF1 are entirely optional

**Usage terms**
Free for personal, non-commercial beta evaluation. The source is publicly viewable but is **not open-source**; redistributing or modifying the author's code or repackaging the official APK is not authorized by the [Free Beta License](https://github.com/cetninarius/home-monitor/blob/main/LICENSE.md). Third-party software retains its own licenses.

**Feedback**
Please report device model, Android version, reproducible steps and relevant non-sensitive screenshots through [GitHub Issues](https://github.com/cetninarius/home-monitor/issues). Do not share admin tokens, keys, private IPs or personal data.

---

## Outstanding release-risk checks

- The public source repository contains historical internal screenshots and diagnostic logs. They may contain network/device metadata or personal content; a full history review and possible cleanup are **not completed**. Deleting a current file alone does not erase older commits. Review before broad promotion, and rotate any credential actually found.
- Third-party code/asset attribution and distribution obligations should be checked before calling the legal review complete.
- It is not verified here that the local APK exists or that its hash/certificate matches the intended release.
- This document grants **no new permission** to ship another person's protected content as background art. Verify asset rights separately.
