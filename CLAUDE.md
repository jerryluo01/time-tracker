# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Debug build (Windows)
gradlew.bat assembleDebug

# Debug build (Unix/WSL)
./gradlew assembleDebug

# Install to connected device
adb install app/build/outputs/apk/debug/app-debug.apk

# Clean build
./gradlew clean assembleDebug
```

There are no automated tests in this project. Validation is done manually on a Pixel 7 device.

## Architecture

Native Android (Kotlin, API 26+, `compileSdk 35`), no third-party dependencies beyond AndroidX.

### Data flow

```
Screen ON/OFF ──────────────────┐
                                ▼
UsageStatsManager ──► TimerService  (polls every 1500ms)
                           │
                           ├──► ContinuityEngine    session state + gap/resume logic
                           ├──► AllowlistManager    which packages the app timer tracks
                           ├──► SettingsManager     interval, colors, anchor, opacity
                           ├──► OverlayManager      floating pill (WindowManager)
                           ├──► PulseOverlay ×2     screen-edge glow (app + phone)
                           └──► NotificationManager live timer text in notification shade
```

### Key design decisions

**ContinuityEngine** owns all session logic. Gap < threshold → resume; gap ≥ threshold → reset. Both timers use **independent gap clocks**: `KEY_LAST_ACTIVE_TIME` (phone) and `KEY_LAST_APP_ACTIVE_TIME` (app). A detour through an untracked app can reset the app timer without touching the phone timer. Keep `ContinuityEngine` free of Android UI dependencies.

Key methods:
- `onResume(pkg, isAppTracked)` — screen-on / service start; always resumes phone timer, conditionally resumes app timer
- `pauseAppTimer()` — on switch to untracked app; phone timer unaffected
- `resumeAppTimer(pkg)` — on return to tracked app; uses `KEY_LAST_APP_ACTIVE_TIME` for gap check

**AllowlistManager** stores `Set<String>` of package names ("allowlist"). `isTracked(pkg)` returns `true` when the set is empty (track-everything default) or when `pkg` is in the set.

**SettingsManager** (`"settings"` SharedPreferences) holds all user preferences:
- `milestoneIntervalMin` — nudge interval, read every poll cycle so changes apply immediately
- `pulseColorApp` / `pulseColorPhone` — ARGB ints, read via lambda at pulse trigger time
- `isAnchored` — toggled from `SettingsActivity`; `TimerService` listens via `OnSharedPreferenceChangeListener` and calls `overlayManager.setAnchored()` instantly
- `pillOpacityPercent` — applied when the overlay is built; requires service restart to change

**TimerService** is the single coordinator:
- Dynamic `BroadcastReceiver` for `ACTION_SCREEN_OFF`/`ON` (manifest receivers cannot receive these)
- `OnSharedPreferenceChangeListener` on `SettingsManager.prefs` for instant anchor changes
- `PulseOverlay` instances receive color as `() -> Int` lambdas, not fixed values — colors update on the next milestone without restarting

**Foreground app detection** — `UsageStatsManager.queryEvents(now-3000, now)` for latest `MOVE_TO_FOREGROUND` event, falls back to `queryUsageStats`. Do not use `queryUsageStats` alone — it is stale for live tracking.

**Milestone detection** — fire-once guards (`lastPhoneMinFired`, `lastAppMinFired`). Fires when `min % interval == 0 && min != lastFired`. Guards reset to `-1` when not on a boundary. `interval` is read from `SettingsManager` each poll. Do not use a time-window approach.

**PulseOverlay** — creates a fresh `GlowBorderView` on each `trigger()` call (so it picks up the latest color). Two independent instances in `TimerService`; both can fire in the same poll cycle.

**OverlayManager** — anchor mode adds `FLAG_NOT_TOUCHABLE` to `WindowManager.LayoutParams`, making the pill fully click-through. `FLAG_NOT_FOCUSABLE` stays on always. Position saved to `"overlay_prefs"` SharedPreferences, clamped to screen bounds on restore.

### Permissions flow (MainActivity)

Three permissions required before `TimerService` starts:
1. `SYSTEM_ALERT_WINDOW` — `Settings.canDrawOverlays()`
2. `PACKAGE_USAGE_STATS` — `AppOpsManager.OPSTR_GET_USAGE_STATS`
3. Battery optimization exemption — `PowerManager.isIgnoringBatteryOptimizations()`

`onResume()` re-checks all three. Once all granted: **Start Tracking**, **Choose apps**, and **Settings** buttons appear.

### Android version notes

- `foregroundServiceType="dataSync"` required on API 34+, harmless below
- `FOREGROUND_SERVICE_DATA_SYNC` permission declared for API 34+
- `START_STICKY` + `BootReceiver` ensure the service survives process death and device reboot
