# Install Home Monitor on Android TV

Home Monitor is an Android TV **app**, not a replacement firmware or default system launcher. It is an early beta; compatibility with most TV models has not been confirmed.

## 1. Get the APK

Open the [official GitHub Releases page](https://github.com/cetninarius/home-monitor/releases) and download the APK attached to the newest published release.

If no release with an APK is published, **installation files are not yet available here**. Do not download a similarly named APK from an unrelated site.

If a `SHA256SUMS.txt` file is attached, verify the APK against it. On macOS run `shasum -a 256 path/to/home-monitor.apk`; on Windows PowerShell run `Get-FileHash .\home-monitor.apk -Algorithm SHA256`. Compare the complete hash, not only its prefix.

## 2. Install without a computer

1. Put the downloaded APK on a USB drive or download it with a TV-compatible browser/file manager.
2. On the **Android TV itself**, open your file manager and select the APK.
3. If Android asks to allow installing apps from this source, enable it only for the file manager you are using. Revoke the permission afterwards if desired.
4. Confirm installation and launch **Home Monitor** from Apps.
5. If prompted for display-over-other-apps permission, grant it only if you want Home Monitor's dashboard overlay behavior.

Menus vary by TV manufacturer and Android version. Root access is not required.

## 3. Set up the dashboard

- Use your remote to open **Settings** and choose a background, widgets and appearance.
- Calendar and to-do are stored **locally on the TV**.
- For editing via browser, open Home Monitor's **web admin** link shown on the TV while the app is open, and authenticate with the admin token.
- The admin server listens on TCP port `8765` while Home Monitor is in the foreground. It uses **unencrypted HTTP**: trusted home networks only. Never forward it to the internet.
- Optional **SmartTube**, service monitoring and **WF1** are not required. Now Playing needs compatible SmartTube/MediaSession support and notification access.
- WF1 requires your own compatible local read-only proxy. The developer's private WF1 installation and tokens are not included.

### If the web admin cannot be reached

Some TVs reject direct HTTP requests to the TV IP even when networking otherwise works. This does **not** necessarily mean the application is broken.

Advanced users who have separately enabled ADB can forward the admin port to their computer:

```bash
adb devices -l
adb -s TV_SERIAL_OR_IP:PORT forward tcp:8765 tcp:8765
```

Open `http://127.0.0.1:8765` **on the computer**. Use the exact TV identifier returned by `adb devices`. Do not enable ADB on an untrusted network; no ADB connection is required for normal use.

## 4. Upgrade and uninstall

Install a newer officially signed APK over the installed version to retain local data and settings, **provided the package name and signing certificate match**. Do not uninstall first unless you accept local data loss.

When changing from a debug-signed or different-key build, Android may refuse an upgrade. Uninstalling clears app data: back up anything important first.

To uninstall: TV **Settings → Apps → Home Monitor → Uninstall** (exact menu varies by TV).

## Troubleshooting

| Problem | What to check |
|---|---|
| APK fails to install | TV is actually Android TV / Google TV; supported Android version; enough free space; permitted sideload source; matching signature for upgrade |
| App not in launcher | Check **See all apps**; this is not a default HOME replacement |
| D-pad or layout behaves strangely | Note device model, Android version and screen size; report in Issues |
| Blank screen or overlay problems | Force stop/reopen; check display-over-other-apps permission |
| Admin unreachable | Home Monitor must be active; check TV network; some TVs require ADB forwarding |
| SmartTube information missing | Integration is optional; verify SmartTube is installed and notification access is granted |
| WF1 unavailable | Expected unless you separately configure a compatible running proxy |

## Report a problem

[GitHub Issues](https://github.com/cetninarius/home-monitor/issues) — include device model, Android version, app version, steps and non-sensitive symptoms. **Never publish API keys, tokens, private logs or personal event titles.**
