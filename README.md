# Time Tracker

A native Android app (Kotlin, API 26+) that floats two live session timers over every app on your screen and pulses a glowing border around the screen edge at configurable time milestones — a subtle, ambient nudge to stay mindful of how long you've been on your phone.

---

## Features

### Two independent timers
| Timer | Tracks |
|---|---|
| **App session** | How long you've been in the current foreground app continuously |
| **Phone session** | Total continuous phone-on time, regardless of which app you're in |

Both timers run independently. The app timer resets when you switch apps; the phone timer only resets after the screen has been off for 15+ minutes (configurable).

### Continuity rule
- **Gap < threshold** → timer **resumes** from where it left off (idle time is not counted)
- **Gap ≥ threshold** → timer **resets** to zero
- Default threshold: 15 minutes. Configurable in Settings.

### Floating pill overlay
- Small semi-transparent pill showing `● app_time | ● phone_time` floating on top of all apps
- Draggable — position saved and restored across reboots
- **Anchor mode** — lock the position so it can't be moved accidentally; when anchored the pill is fully **click-through** (all taps pass straight to the app behind it)
- Opacity: Ghost (15%) / Light (28%) / Medium (45%) / Solid (65%)
- Dot colors match your chosen pulse colors and update live

### Milestone pulse
- At each milestone a glowing border pulses around the screen edges for ~2.2 seconds
- App milestone → customizable color (default **green `#639922`**)
- Phone milestone → customizable color (default **blue `#378ADD`**)
- Both timers fire independently and can pulse simultaneously
- The pill also briefly scales up on each milestone

### App allowlist
- By default every app is tracked (track-everything mode)
- Open **Choose apps** to pick a specific list (e.g. Instagram, TikTok, YouTube)
- When an allowlist is active the app timer only runs while one of those apps is in the foreground; the phone timer always runs regardless
- Switching to an untracked app freezes (but does not reset) the app timer
- **Search** — filter the app list live by name to find an app quickly
- **Refresh** — reload the installed app list without leaving the screen (picks up newly installed or uninstalled apps)

### Customizable settings
| Setting | Options | Default |
|---|---|---|
| Nudge interval | 5 / 10 / **15** / 20 / 30 / 45 / 60 min | 15 min |
| App timer pulse color | 8 swatches (green, blue, cyan, orange, purple, red, pink, white) | Green |
| Phone timer pulse color | same 8 swatches | Blue |
| Overlay opacity | Ghost / **Light** / Medium / Solid | Light (28%) |
| Lock overlay | On / Off | Off |

Changes to interval, colors, and anchor state take effect **immediately**. Opacity changes apply on next service restart.

### Persistent notification
The required foreground service notification doubles as a live timer display visible from the pull-down shade without needing the overlay: `App: 12:34  |  Phone: 1:05:02`

### Auto-restart on reboot
`BootReceiver` restarts the service automatically after device reboot.

---

## Setup

### Requirements
- Android Studio (Hedgehog 2023.1.1 or newer)
- Android SDK with `compileSdk 35`, `minSdk 26`
- A physical Android 8.0+ device — overlay permissions cannot be granted on emulators

### 1 — Clone and open
```bash
git clone <repo-url>
```
Open Android Studio → **Open** → select the `time-tracker` folder. Wait for Gradle sync to complete.

### 2 — Generate the Gradle wrapper (first time only)
If you see a `gradle-wrapper.jar` missing error, run once inside the project directory:
```bash
gradle wrapper --gradle-version 8.4
```
Or in Android Studio: **Tools → Gradle → Generate Gradle Wrapper**.

### 3 — Build
```bash
# Windows
gradlew.bat assembleDebug

# macOS / Linux / WSL
./gradlew assembleDebug
```

### 4 — Enable Developer Options on your phone
**Settings → About phone → tap Build number 7 times**, then enable **USB debugging** under **Settings → System → Developer options**.

### 5 — Install
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```
Or press **Run ▶** in Android Studio with your phone connected via USB.

### 6 — Grant permissions (one-time walkthrough)
The app guides you through three permissions on first launch:

| Permission | Why |
|---|---|
| Display over other apps | Draw the floating pill |
| Usage access | Detect which app is in the foreground |
| Ignore battery optimization | Prevent Android from killing the background service |

Tap **Grant** for each → toggle it on in the system page that opens → navigate back. Once all three are granted, **Start Tracking** and **Choose apps** / **Settings** buttons appear.

---

## How to use

1. **Launch** the app and grant all three permissions, then tap **Start Tracking**.
2. The floating pill appears. **Drag it** to wherever you want it on screen.
3. Use your phone normally — both timers count up live.
4. At each milestone (default 15 min) a colored glow pulses around the screen edges.
5. To customize: tap the notification → **Settings** button on the main screen.
6. To lock the pill in place: **Settings → Lock overlay**. All taps will pass through it.
7. To track only specific apps: tap **Choose apps** and check the ones you care about.

---

## Architecture

```
Screen ON/OFF ──────────────────┐
                                ▼
UsageStatsManager ──► TimerService  (polls every 1500ms)
                           │
                           ├──► ContinuityEngine    session state, gap/resume logic
                           ├──► AllowlistManager    which packages the app timer tracks
                           ├──► SettingsManager     interval, colors, anchor, opacity
                           ├──► OverlayManager      floating pill (WindowManager)
                           ├──► PulseOverlay ×2     screen-edge glow (app + phone)
                           └──► NotificationManager live timer text in shade
```

Key design points:
- **Phone timer** only reacts to screen on/off — app switches never affect it
- **App timer** uses its own gap clock (`KEY_LAST_APP_ACTIVE_TIME`) independent of the phone timer's, so a detour through an untracked app can reset the app timer without touching the phone timer
- **Anchor mode** adds `FLAG_NOT_TOUCHABLE` to the overlay window — applied instantly via `SharedPreferences.OnSharedPreferenceChangeListener` in `TimerService`
- **Pulse colors** are lambdas — the current color is read at the moment a milestone fires, so color changes in Settings take effect on the next pulse without restarting anything
- `START_STICKY` on the service + `BootReceiver` ensure the service survives both process death and device reboot

---

## File overview

```
app/src/main/kotlin/com/timetracker/app/
├── ContinuityEngine.kt     gap/resume/reset logic for both timer clocks
├── AllowlistManager.kt     user's tracked-app selection
├── SettingsManager.kt      nudge interval, pulse colors, anchor, opacity
├── TimerService.kt         foreground service — coordinates everything
├── OverlayManager.kt       draggable pill, anchor/click-through support
├── PulseOverlay.kt         animated screen-edge glow with dynamic color
├── MainActivity.kt         permission walkthrough + service launch
├── AppPickerActivity.kt    app allowlist picker
├── SettingsActivity.kt     nudge interval, colors, opacity, lock toggle
└── BootReceiver.kt         restart service on device reboot
```
