# Architecture

Home Monitor is a lightweight Android TV dashboard shell with independent widgets.

## Layers

| Layer | Role |
|-------|------|
| `MainActivity` | Immersive landscape entry; starts/stops widgets with resume/pause |
| `DashboardController` | Wires widgets into the fixed layout |
| `WidgetRegistry` | Code-configured factories for Phase 1 widgets |
| `DashboardWidget` | Contract: attach, onStart/onStop, optional focus target |
| Data providers | Per-widget (clock = local time; WF1 = HTTP proxy) |

## Layout

- Fullscreen Baghdad background (`centerCrop`)
- Left ~66%: brand + clock/date; mid row Calendar | To-do; bottom row Services | Weather
- Right ~34%: WF1 Live Queue (full height)

Positions are hardcoded in `activity_dashboard.xml`. No drag-and-drop editor.

Local data uses Room (`home_monitor.db`). Weather uses Open-Meteo (no key). Services uses UptimeRobot with an on-device API key via web admin. The foreground NanoHTTPD admin (`:8765`) mutates calendar/todo/uptime config.

## Adding a future widget

1. Create `widgets/<name>/` with UI + optional data provider implementing `DashboardWidget`.
2. Register a factory in `WidgetRegistry`.
3. Add a layout slot (or reuse left column) and attach from `DashboardController`.
4. Ensure refresh stops in `onStop()` / activity `onPause()`.

Keep widgets self-contained: loading, error, and empty states live inside the widget.

## Performance constraints (VOX)

- No WebView, no Compose, no realtime blur
- Static JPEG background
- Poll only while the activity is resumed
- Prefer solid translucent panels over expensive effects
