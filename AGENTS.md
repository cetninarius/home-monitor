# Home Monitor — agent notes

Standalone Android TV dashboard for the **VOX UHD 43A11U314B** (`Ateko` / `rtd2851a`).

## Hard stops

- Do **not** modify Plume, TitanOS, Netflix, YouTube/SmartTube, EON HDMI, vendor keylayouts, or system partitions.
- Do **not** set Home Monitor as the default HOME activity.
- Do **not** rebind the stock WF1 monitor (`:8791`) to the LAN or expose `/api/control/*`.
- Confirm ADB target identity (`product:Ateko`, `ro.hardware=rtd2851a`) before any install.
- Ignore EON (`SDSTB02`) and the ChipTrip projector.

## Safe install path

Network ADB sideload only:

```bash
S=192.168.0.56:5555
adb connect "$S"
adb -s "$S" shell getprop ro.hardware   # expect rtd2851a
adb -s "$S" install -r android/app/build/outputs/apk/debug/app-debug.apk
```

## WF1 data

TV talks only to `home-monitor/proxy` (read-only, bearer token). Proxy polls localhost WF1 monitor. Never ship Cursor/GitHub/deploy credentials to the TV.
