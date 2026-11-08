# SmartTube Now Playing — Feasibility Spike

**Date:** 2026-11-07  
**Device:** VOX UHD 43A11U314B (`ro.hardware=rtd2851a`, product `SmartTV` / M043)  
**OS:** Android 11 (API 30), leanback-only, `android.software.picture_in_picture` present  
**Scope:** Investigate only. Home Monitor code was **not** modified.

Legend used below:

| Tag | Meaning |
|-----|---------|
| **TESTED** | Observed via ADB on this TV during the spike |
| **SOURCE** | Read from installed APK and/or upstream SmartTube source (not runtime-proven on Controllers) |
| **EXTERNAL** | Third-party reports (e.g. GitHub issues); not reproduced here |
| **NOT TESTED** | Requires phone UI, user settings, or an instrumented HM build |

---

## Follow-up validation (2026-11-07)

See **`smarttube-background-mediasession-validation.md`**: Only-audio background + MediaSession survival **confirmed**; HM can read **state / duration / position** after Notification access; **title / artist / artwork empty** on the tested path; phone pairing still untested. Animated Now Playing **not** justified yet.

## Verdict

**Conditionally feasible — not shippable from this spike alone.**

Desired UX (phone → SmartTube → background audio → Home Monitor visible → Now Playing with art/title/artist/progress → compact dashboard) is **plausible without replacing SmartTube**, but several blockers were confirmed on this TV:

1. **Default leave behavior stops playback** (Home or switch to Home Monitor). Background audio must be explicitly enabled in SmartTube.
2. **MediaSession exists while SmartTube is foreground**, but dumpsys shows empty MediaDescription and unreliable position; Controller-level metadata was not proven from an app.
3. **Notification extras do not carry track metadata** — only a generic FGS notification.
4. **PiP is supported by SmartTube but would overlay the dashboard** — wrong fit for the desired layout.
5. **RAM headroom is tight** (~1.5 GB device; SmartTube ~200 MB PSS while playing).

A follow-up prototype (NotificationListener + MediaSession read, with SmartTube set to audio-only / play-behind) is required before committing to the animated compact layout.

---

## 1. SmartTube identity (TESTED)

| Field | Value |
|-------|-------|
| Package | `org.smarttube.stable` |
| versionName | `32.71` |
| versionCode | `2461` |
| minSdk / targetSdk | 17 / 34 |
| Install path | `/data/app/~~…/org.smarttube.stable-…/base.apk` |
| uid | `10059` |

Launcher: `…tv.ui.main.SplashActivity` (LEANBACK_LAUNCHER).  
Player: `…tv.ui.playback.PlaybackActivity` (`supportsPictureInPicture=true`, `resizeableActivity=true`).

---

## 2. Background playback & PiP (TESTED + SOURCE)

### Manifest / features (TESTED)

- Device feature: `android.software.picture_in_picture`
- `PlaybackActivity`: `supportsPictureInPicture=true` (task dump confirmed while playing)
- Services declared:
  - `RemoteControlService` — **observed running as foreground** during playback
  - `BackgroundPlaybackService` — declared with media-playback FGS type

### Upstream source note (SOURCE)

`BackgroundPlaybackService` upstream comment: *“This service isn't used at all!”* — keep-alive/PiP/audio-behind are driven elsewhere (`PlayerData` background mode + player lifecycle), not this stub service.

Background modes in APK/source:

- `option_background_playback_off`
- `option_background_playback_pip` → `BACKGROUND_MODE_PIP`
- `option_background_playback_behind` → `BACKGROUND_MODE_PLAY_BEHIND`
- `option_background_playback_only_audio` → `BACKGROUND_MODE_SOUND`

Shortcuts: HOME / BACK / HOME+BACK (`BACKGROUND_PLAYBACK_SHORTCUT_*`). Default shortcut parsing favors HOME+BACK; **default mode resolves via `BACKGROUND_MODE_DEFAULT`** (effectively off for leave-to-other-app in our tests).

### Runtime leave tests (TESTED)

| Action | Audio continues? | MediaSession after leave | PiP entered? |
|--------|------------------|--------------------------|--------------|
| `am start` Home Monitor while playing | **No** — SmartTube `AudioTrack` left `started`; audio focus cleared | **0 sessions** | **No** (`mLastReportedPictureInPictureMode=false`) |
| `KEYCODE_HOME` → Plume | **No** — same | **0 sessions** | **No** |

SmartTube process stayed alive (`pidof` non-empty) and `RemoteControlService` remained foreground, but **playback did not continue**.

**Conclusion:** Background Now Playing on the dashboard requires a **manual SmartTube setting change** (only-audio or play-behind, and appropriate HOME/BACK shortcut). That configuration was **not** successfully enabled in this spike (prefs not readable without root; `AddDevice`/settings activities not exported for ADB).

---

## 3. Phone → SmartTube pairing (NOT TESTED end-to-end)

| Item | Status |
|------|--------|
| `AddDeviceActivity` present | **TESTED** (manifest) |
| Launch via ADB | **TESTED** — `SecurityException` (activity not exported) |
| `SignInActivity` present | **TESTED** (manifest) |
| `RemoteControlService` + `RemoteControlReceiver` | **TESTED** — service runs during playback |
| YouTube phone “link / cast to TV” against this box | **NOT TESTED** (needs phone + in-app Add Device / Linked devices UI) |

**Manual pairing path (documented, not exercised):** open SmartTube on TV → settings / remote control / add device → complete link from YouTube on phone. Do not rely on ADB to open `AddDeviceActivity`.

Playback for this spike was started with:

```bash
adb -s 192.168.0.56:5555 shell am start -a android.intent.action.VIEW \
  -d 'https://www.youtube.com/watch?v=jNQXAC9IVRw' \
  -n org.smarttube.stable/com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity
```

That reached `PlaybackActivity` successfully (**TESTED**).

---

## 4–5. Start video, return to Home Monitor (TESTED)

1. Started “Me at the zoo” (`jNQXAC9IVRw`) → focus = `PlaybackActivity`.
2. uiautomator showed on-screen title **“Me at the zoo”**, author line **“jawed …”**, transport **0:06 / 0:19**.
3. Audio focus held by SmartTube; `AudioTrack` uid `10059` state `started`.
4. Brought Home Monitor to front → HM focused; **SmartTube audio stopped**; media session stack empty.
5. Screenshot after switch: `docs/spikes/hm-with-st-bg.png` (dashboard visible; no PiP overlay).

---

## 6–7. MediaSession contents (TESTED + SOURCE + EXTERNAL)

### While SmartTube foreground (TESTED)

```
Sessions Stack - have 1 sessions:
  package=org.smarttube.stable
  active=true
  state=PlaybackState {state=3, position=0 or -100, buffered position=…, speed=1.0, actions=2360191}
  metadata: size=7, description=null, null, null
```

`state=3` = `STATE_PLAYING`.  
`cmd media_session list-sessions` listed `tag=org.smarttube.stable`.  
`cmd media_session monitor` advertised commands: `play, pause, next, previous`.

| Field | Desired | Observed via dumpsys | Notes |
|-------|---------|----------------------|-------|
| Playback state | yes | **PLAYING** | TESTED |
| Title | yes | MediaDescription **null** | UI had title; see SOURCE |
| Artist/channel | yes | MediaDescription **null** | UI had “jawed”; see SOURCE |
| Thumbnail/artwork | yes | Description icon **null** | SOURCE publishes URI only |
| Duration | yes | Not printed by dumpsys description | SOURCE puts `METADATA_KEY_DURATION` |
| Position | yes | **0 or -100**, not advancing over 5s | UI transport advanced; session position unreliable in dumpsys |

### What SmartTube intends to publish (SOURCE)

From upstream `PlaybackFragment.createMediaSession()` (matches installed 7-key metadata size):

- `METADATA_KEY_MEDIA_ID` (video id)
- `METADATA_KEY_TITLE` / `DISPLAY_TITLE`
- `METADATA_KEY_ARTIST`
- `METADATA_KEY_DISPLAY_SUBTITLE`
- `METADATA_KEY_ALBUM_ART_URI` (card image URL — **not** a bitmap)
- `METADATA_KEY_DURATION`

Session can be deactivated if “playback notifications disabled” in Player Tweaks (`setActive(!disableNotifications)`).

### Artwork (EXTERNAL + SOURCE)

GitHub issue [SmartTube #5666](https://github.com/yuliskov/SmartTube/issues/5666) (Cast / Home Assistant): integrations receive title, artist, duration, position, but **not** artwork/thumbnail bitmap. Aligns with SOURCE using `ALBUM_ART_URI` only.

**HM implication:** if URI is present via `MediaController`, fetch thumbnail over HTTPS; do not expect session bitmap.

### Notifications (TESTED)

Active notification title: **“Remote control: Background service started”**.  
Extras: no track title/text; `largeIcon=null`; progress 0.  

**NotificationListener alone cannot build Now Playing from notification content.** It is still the user-facing gate for `MediaSessionManager.getActiveSessions()`.

---

## 8. Access path for Home Monitor (SOURCE / platform rules; NLS not enabled on device)

| API | Usable by sideloaded HM? | Notes |
|-----|--------------------------|-------|
| `MediaSessionManager.getActiveSessions(ComponentName)` | Yes, **if** a `NotificationListenerService` is user-enabled for HM | Standard third-party path |
| `MEDIA_CONTENT_CONTROL` | No | Signature/privileged |
| Reading notification extras only | Insufficient | No title/art in SmartTube FGS notification |
| Binding SmartTube services / patching | Out of scope / disallowed | |

**TESTED on device:** `settings get secure enabled_notification_listeners` → `null` (no third-party listeners). Enabling notification access on Android TV is awkward (Settings UI); must be designed into onboarding.

**NOT TESTED:** actual `MediaController.getMetadata()` / `getPlaybackState()` from an HM NotificationListener (would require a temporary diagnostic build + user grant).

---

## 9. Playback survival SmartTube ↔ Home Monitor (TESTED)

Under **current** SmartTube settings: **does not survive**. Session disappears; audio focus released; no PiP.

With `BACKGROUND_MODE_SOUND` or `PLAY_BEHIND` (**NOT TESTED** on this TV): theoretically audio can continue while another activity is resumed — that is the required configuration for the desired UX.

---

## 10. PiP vs dashboard (TESTED + design)

- PiP capability: **yes** (feature + `PlaybackActivity` flag).
- Auto-PiP on leave: **not observed**.
- If user enables `BACKGROUND_MODE_PIP`, a pinned window would sit on top of Home Monitor — **conflicts** with “dashboard remains the composition + Now Playing strip”.

**Recommendation:** target SmartTube **only audio** (or play-behind without visible PiP), and render Now Playing **inside** Home Monitor — do not rely on system PiP for the UX.

---

## 11. CPU / RAM / rendering (TESTED)

Device: **1572864 kB** RAM (~1.5 GB). During FG SmartTube play, `MemAvailable` dropped to ~**295 MB**.

| Condition | SmartTube PSS | Home Monitor PSS | Notes |
|-----------|---------------|------------------|-------|
| HM only (baseline) | (not running) | ~62 MB | `perf-baseline.txt` |
| SmartTube FG playing | ~**218 MB** | HM cached ~47 MB | SmartTube ~**23%** CPU in `top` (400% scale) |
| After switch to HM | ~**207 MB** (still resident) | ~**55 MB** | ST CPU ~0% when stopped |

HM `gfxinfo` (alone): **1.61%** janky frames over sample — fine for current dashboard.

**Risks if both audio decode + HM animation run together (NOT TESTED in that config):**

- Memory pressure / reclaim on 1.5 GB
- SurfaceFlinger + media codec contention
- Animated compact layout should avoid heavy continuous blur/re-layout; prefer simple transitions

---

## Confirmed capabilities

- SmartTube install identified; VIEW intents open `PlaybackActivity`.
- Active `MediaSession` while player is foreground (`STATE_PLAYING`).
- Transport actions exposed (`play/pause/next/previous`).
- Remote-control foreground service + generic notification.
- Device supports PiP; SmartTube player declares PiP.
- Home Monitor can be resumed over SmartTube without crashing.
- On-screen player UI exposes title, channel, and progress (uiautomator).

## Unsupported / blocked (on this TV as tested)

- Background audio after leaving SmartTube **without** changing SmartTube settings.
- Track metadata via notification extras.
- System PiP as the Now Playing surface for the dashboard (undesirable even if enabled).
- ADB launch of pairing UI (`AddDeviceActivity` not exported).
- Proven Controller access to title/artist/art/position from HM (no NLS app granted yet).
- Reliable MediaSession position from dumpsys (stuck at `0` / `-100` while UI progressed).

## Required permissions / user steps (for a future HM feature)

1. SmartTube: enable **background playback** → **only audio** (preferred) or play-behind; configure HOME/BACK shortcut as needed.
2. Home Monitor: declare `NotificationListenerService`; user enables **Notification access** for Home Monitor.
3. Optional: `INTERNET` already present — needed to fetch `ALBUM_ART_URI`.
4. Do **not** set HM as default HOME; do not patch SmartTube; do not use PiP as the primary UX.

## Recommended architecture (if pursued)

```
YouTube phone ──(manual link)──► SmartTube (audio-only background)
                                      │
                                      │ MediaSession (state + metadata)
                                      ▼
                         HM NotificationListenerService
                                      │
                                      ▼
                         NowPlayingRepository (MediaController)
                                      │
                    ┌─────────────────┴─────────────────┐
                    ▼                                   ▼
            NowPlayingWidget                    DashboardLayoutMode
         (art via URI fetch,               (normal ↔ compact animate)
          title, artist, progress*)
```

\*Progress: prefer `PlaybackState` position when sane; if SmartTube keeps publishing `0`/`UNKNOWN`, fall back to local monotonic estimate from last non-zero sample + duration, or hide scrubber initially.

Gate the widget on: session package == `org.smarttube.stable` && state playing/paused && (optional) background mode confirmed by session surviving HM resume.

## Performance risks

- Concurrent SmartTube + HM on 1.5 GB RAM.
- Thumbnail decode + dashboard transition jank on Realtek GPU.
- Keeping SmartTube process warm (~200 MB) while HM is “idle dashboard”.
- Avoid PiP (extra composition layer).

## Is the animated Now Playing widget achievable without replacing SmartTube?

**Yes, as a direction — with prerequisites.** SmartTube already owns playback and publishes a MediaSession; HM should observe, not replace, the player.  

**Not yet proven end-to-end on this VOX.** Next spike should be a throwaway diagnostic APK (or a feature-flagged HM build) that:

1. Asks for notification listener permission.
2. Logs `MediaController` metadata/position every second while SmartTube plays.
3. Asks the user to set SmartTube background mode to **only audio**, then switches to the diagnostic UI and verifies session + audio survival.
4. Measures PSS/CPU with both active.

Only then implement the compact animated dashboard.

---

## Evidence index

All under `docs/spikes/`:

| File | Content |
|------|---------|
| `playback-foreground.txt` | Focus, media_session, audio, services while ST playing |
| `playback-after-hm-switch.txt` | Session/audio gone after HM |
| `playback-after-home.txt` | Same after HOME → Plume |
| `media-session-me-at-zoo.txt` | Session dump during known title |
| `st-ui-player.xml` | uiautomator: “Me at the zoo”, jawed, 0:06/0:19 |
| `notification-during-play.txt` | FGS notification without track fields |
| `perf-baseline.txt` / `perf-st-fg.txt` / `perf-hm-after-st.txt` | Memory/CPU/gfx |
| `hm-with-st-bg.png` | HM after leaving player |

Home Monitor application code: **unchanged**.
