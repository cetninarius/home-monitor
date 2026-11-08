# SmartTube → Home Monitor — Background Audio & MediaSession Validation

**Date:** 2026-11-07  
**Device:** VOX (`rtd2851a`), Android 11  
**SmartTube:** `org.smarttube.stable` 32.71  
**Home Monitor:** 1.2.2-diag (`MEDIA_SESSION_DIAG=true`)  
**Prior spike:** `smarttube-now-playing-feasibility.md`

---

## Executive answers

| # | Question | Result |
|---|----------|--------|
| 1 | Does audio survive switching to Home Monitor? | **YES** — after SmartTube **Only audio (HOME or BACK)** + press HOME |
| 2 | Does MediaSession survive? | **YES** — active `PLAYING` session while HM is foreground |
| 3 | Can HM read title, artist, duration? | **Duration YES**; **title/artist NO** (keys present, string values empty) |
| 4 | Can HM access artwork? | **NO** — `ALBUM_ART_URI` key present but empty |
| 5 | Can HM track playback progress? | **YES** — `MediaController` position advances ~1 s/s; duration 213000 ms observed |
| 6 | Can a phone send another video while HM visible? | **NOT TESTED** — pairing instructions below |
| 7 | Measured performance overhead? | HM ~57 MB PSS; SmartTube ~178 MB PSS; ST ~5.5% CPU (400% scale); no restarts in 20 s soak |
| 8 | Is animated Now Playing justified? | **Not yet for the full UX** — progress/state OK; title/art/artist blocked by empty SmartTube metadata on this path |

---

## Phase A — Background audio (TESTED)

### Settings found (via TV UI)

Path: **SmartTube → Settings (sidebar) → General → Background playback**

Options observed:

- Disabled  
- Picture in picture (HOME / HOME or BACK / BACK)  
- **Only audio (HOME / HOME or BACK / BACK)**

Selected: **Only audio (by pressing HOME or BACK)**  
Evidence: `st-only-audio-home-back.png`

### Trials (3/3)

Flow each trial: start `dQw4w9WgXcQ` → wait ~14 s → `KEYCODE_HOME` → start Home Monitor.

| Trial | After HOME | After HM | SmartTube AudioTrack `started` | MediaSession |
|-------|------------|----------|--------------------------------|--------------|
| 1 | Plume + audio | HM + audio | yes | 1 session PLAYING |
| 2 | same | same | yes | same |
| 3 | same | same | yes | same |

Logs: `phaseA-t{1,2,3}-session.txt`, `phaseA-t{1,2,3}-audio.txt`

**Note:** Leaving via `am start` HM **without** HOME still stops playback (shortcut is HOME/BACK). Product UX must use HOME (or BACK) after play, or document that path.

---

## Phase B — MediaSession diagnostic (TESTED)

### What was added (feature-flagged)

- `BuildConfig.MEDIA_SESSION_DIAG` (true in this build)  
- `MediaSessionDiagService` (`NotificationListenerService`)  
- Overlay + logcat dump every 1 s (`MediaSessionDiagController`)  
- Dashboard widgets unchanged underneath  

### Notification access (user-approved)

`ACTION_NOTIFICATION_LISTENER_SETTINGS` **does not resolve** on this VOX.

Working path:

1. Settings → **Apps**  
2. **Special app access**  
3. **Notification access**  
4. Enable **Home Monitor** (toggle)

Granted component:

`com.custom.vox.homemonitor/com.custom.vox.homemonitor.media.MediaSessionDiagService`

### MediaController readouts (while HM foreground, ST audio-only)

From `phaseB-metadata-keys.txt` / logcat:

```
metadataKeys=[ALBUM_ART_URI, ARTIST, DISPLAY_SUBTITLE, DISPLAY_TITLE, DURATION, MEDIA_ID, TITLE]
MEDIA_ID=dQw4w9WgXcQ
DURATION=213000
TITLE/ARTIST/DISPLAY_*/ALBUM_ART_URI → empty (getString null; logged as long=0)
state=PLAYING
position advances: 10328 → 11578 → 12576 → … (~1s/tick)
estimatedPos ≈ reportedPos (framework+reported)
```

Overlay evidence: `phaseB-diag-success.png` (`listenerEnabled=true`, `sessionFound=true`, `PLAYING`).

### Progress estimation

When reported position is sane (≥0 and advancing), no extrapolation needed.  
Extrapolator remains for the `position=-90` / `0` dumpsys cases; **Controller API was reliable** during only-audio + HM.

### Artwork

URI empty → reachability probe not applicable. No bitmap in session.

**Optional future fallback (NOT implemented / NOT validated):** resolve `MEDIA_ID` via YouTube oEmbed / thumbnail URL. That is outside pure MediaSession.

---

## Phase C — Performance (TESTED)

While HM visible + SmartTube only-audio playing (`phaseC-perf.txt`):

| Process | TOTAL PSS | Notes |
|---------|-----------|--------|
| Home Monitor | **~56.6 MB** | widgets alive |
| SmartTube | **~178 MB** | audio-only background |

CPU (`top` 400% scale): SmartTube ~**5.5%**; HM not in top hot list.  
`MemAvailable` tight on 1.5 GB device.  

Stability (+20 s): both PIDs unchanged; session still PLAYING; AudioTrack still started; no HM/ST restart observed (`phaseC-stability.txt`).

Widgets (Calendar / To-do / Weather / WF1 / Network) remained on screen in screenshots — no functional break observed during soak.

---

## Phase D — Phone pairing (NOT TESTED end-to-end)

### Supported surfaces (TESTED via manifest / settings UI)

- Settings tile: **Remote control**  
- Activities: `AddDeviceActivity`, `SignInActivity` (not exported for ADB)  
- Services: `RemoteControlService` (runs as FGS during playback)

### Exact manual instructions (for the user — not executed here)

1. On TV: open **SmartTube → Settings → Remote control**.  
2. Enable remote / linking as prompted (keep **RemoteControl** service allowed).  
3. Open **Add device** / link screen inside SmartTube (QR or code).  
4. On phone: YouTube app → account avatar → **Settings → Watch on TV** / **Link with TV code** (wording varies by YouTube version) → enter code from TV.  
5. Alternatively: cast/link from YouTube’s device picker if SmartTube appears as a linked TV.  
6. Start a video on the phone; confirm TV SmartTube takes playback.  
7. On TV: with **Only audio (HOME or BACK)** enabled, press **HOME**, open **Home Monitor**, confirm audio continues.  
8. Change video on the phone; confirm session `MEDIA_ID` / playback updates while HM stays visible.

**Do not claim phone→TV queue switching works until step 8 is observed.**

---

## Implications for animated Now Playing

**Green lights**

- Background audio + surviving MediaSession (with correct SmartTube setting)  
- Notification-listener gate works on VOX (via Special app access)  
- Playback state, duration, position readable from HM  
- Acceptable CPU; memory heavy but stable in short soak  

**Red / yellow lights**

- Title, artist, artwork **empty** on MediaSession for intent-started playback (keys exist, values blank)  
- HOME/BACK required to enter only-audio (direct app switch insufficient)  
- 1.5 GB RAM + ~178 MB SmartTube → compact animation must stay light  
- Phone remote queue change **unproven**  

**Verdict:** Do **not** build the full animated compact dashboard yet. Next step options:

1. Retest metadata after playing a video from SmartTube’s own browse UI (not VIEW intent) — see if TITLE/ART fill in.  
2. If still empty, decide whether `MEDIA_ID` → network metadata fallback is acceptable.  
3. Complete Phase D phone pairing test.  
4. Only then implement Now Playing UI.

---

## Evidence index

| File | Phase |
|------|-------|
| `st-only-audio-home-back.png` | A setting |
| `phaseA-t*-*.txt` | A trials |
| `phaseB-diag-*.png` / `phaseB-diag-log.txt` | B overlay |
| `phaseB-metadata-keys.txt` | B key dump |
| `phaseC-perf.txt` / `phaseC-stability.txt` | C |
| `grant2-s3.png` | Notification access UI |

Diagnostic code is behind `MEDIA_SESSION_DIAG`; set to `false` before shipping a non-spike build.
