# Sigverage

> **Record cellular technology and signal strength at your location, then paint a coverage map from your own readings.**
> 2G / 3G / HSPA / LTE / 5G NR / 5G NSA, on OpenStreetMap, fully on-device.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-26--34-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-Private-lightgrey)](#license)

---

## Table of contents

1. [Summary](#summary)
2. [Features](#features)
3. [Tech stack](#tech-stack)
4. [Architecture](#architecture)
5. [Project layout](#project-layout)
6. [How the coverage map encodes two dimensions](#how-the-coverage-map-encodes-two-dimensions)
7. [Building](#building)
8. [Permissions rationale](#permissions-rationale)
9. [Settings tour](#settings-tour)
10. [OpenStreetMap tile policy](#openstreetmap-tile-policy)
11. [Cellular-reading caveats](#cellular-reading-caveats)
12. [License](#license)

---

## Summary

Sigverage runs on your phone, records a `SignalReading` (network type, signal dBm, GPS fix, timestamp, MCC/MNC/cell ID, operator name where available) and aggregates your readings into a coverage overlay on top of OpenStreetMap. **Everything stays on the device.** There is no account, no backend, no telemetry, no remote sync. You can export to CSV from *Settings → Data* and the retention policy is configurable from *Settings → Data → Auto-expire*.

The map paints a small filled **box per Mercator tile** at a fixed storage zoom of Z=20 (~38 m tiles at the equator; ~50 m at mid-latitudes - labelled as "50 m cells" in the UI). Each box's **hue** indicates which network dominates, and its **alpha** indicates mean strength. A fixed 2×4 **corner slot grid** in the bottom-right of every box lets readers instantly see which *other* networks were also present at that location. Filter chips in a bottom sheet hide / reveal each network family. **Operator filter** chips let you show only readings from a specific carrier.

**Activity-based sampling** uses the Activity Recognition Transition API to record only when you are moving - the service pauses when you are still, saving battery. A **Battery Usage** setting (Automatic / Power Saver / Balanced / High Accuracy) further trades GPS fix frequency against power. **Smart sampling** skips redundant recordings: while you stay inside one ~50 m cell only a single reading is taken, but leaving and returning to that cell records again. Repeat visits accumulate and their strengths are averaged together on the map.

**Recording schedules** let you define time windows (e.g. Mon-Fri 09:00-17:00) when sampling should run automatically, using AlarmManager with exact alarms that survive reboots.

---

## Features

### 📡 Recording
- **Foreground sampling service** (`SamplingService`) with `foregroundServiceType = location` - promoted on the *first line* of `onStartCommand` so Android 14's 5-second rule is always satisfied.
- **Activity-based sampling** via the Activity Recognition Transition API - records only when the device detects movement (walking, running, cycling, vehicle). The service pauses when you are still, saving battery.
- **Adaptive battery usage** (*Settings → Recording → Battery Usage*): choose how aggressively location is sampled - **Automatic** (adapts to power state; drops to Power Saver when Battery Saver is on or the battery is ≤ 20 %), **Power Saver** (longest interval, coarsest distance filter), **Balanced**, or **High Accuracy** (shortest interval, finest detail). Backed by `SamplingMode`; `LocationTracker` derives the matching interval / distance filter / quality hint.
- **Auto-record on launch** (opt-in, *Settings → Recording*): when enabled, sampling starts automatically the moment you open the app. If location permissions are missing, a one-shot snackbar surfaces explaining how to fix it.
- **Recording schedules** (*Settings → Recording → Schedules*): define time windows (day-of-week + start/end time, including overnight windows) when sampling should run automatically. Uses AlarmManager with exact alarms that survive reboots via `BootReceiver`.
- **Background reliability controls** (*Settings → Permissions & Access → Background Access*): deep-links to the OS **battery-optimisation exemption** ("Run Reliably in Background") and, on Android 12+, the **exact-alarm** grant ("Exact Schedule Timing"), each showing its live status so long recordings and schedules aren't paused by the system.
- **Smart sampling**: while the device stays inside one ~50 m coverage cell (zoom 20) the background service records a single reading; leaving and returning to a cell records again, so repeat visits accumulate and are averaged on the map.
- **Recording control**: sampling is started and stopped from *Settings → Recording*, where a **Recording** switch shows the live running/stopped status. There are no recording controls on the map or in the top app bar.
- **Network operator** (carrier name) is recorded and displayed prominently in both the list view and details sheet.
- **CellularScanner** picks the strongest registered cell and classifies it:
  - 5G NSA detection via `TelephonyDisplayInfo.overrideNetworkType` (API 30+); we don't trust `dataNetworkType` alone because NSA anchors to an LTE control plane.
  - EDGE vs GSM via `dataNetworkType`. `CellInfoTdscdma` is gated on API 29.
- **Location via `callbackFlow`** on `LocationManager` - no Google Play Services dependency.

### 🗺️ Coverage map
- **HSL-hybrid encoding**: hue = dominant network, alpha = mean dBm bucket (Strong / OK / Weak).
- **2×4 corner slot grid**: a fixed-layout indicator grid in the bottom-right of every tile, with one slot per `NetworkType` (5G, NR_NSA, LTE, HSPA, GSM, EDGE, CDMA, Other). Filled = network present; alpha = its bucket strength. Filter-aware: disabled chips remove their slot everywhere. Slot positions are fixed so the legend is stable across the whole map.
- **Web-Mercator slippy-tile aggregation** at zoom 20 (~38 m tiles at the equator; ~50 m at mid-latitudes). The storage zoom is **fixed at 20**; there is no slider.
- **Decoupled zoom axes**: visible map zoom (pinch gestures) is independent of the fixed coverage storage zoom - you can pinch to street level while the coverage grid keeps building at 50 m cells.
- **Operator filter**: filter the coverage grid by network operator (carrier). Operator chips live in the "Filters" modal sheet below the network type chips, derived dynamically from the readings' `operatorName` field. Empty filter shows all operators.
- **Floating filter bar**: a translucent, horizontally-scrollable chip bar floats at the top of the map. It leads with a **Filters** pill (opens a Material 3 `ModalBottomSheet` with the full network + operator chip set) followed by colour-dotted quick toggles for each network type. Because the quick toggles sit directly on the map with no scrim, toggling one previews the coverage grid live - the property the previous non-modal bottom sheet was built to preserve.
- **Customisable palette**: each network's colour comes from `rememberNetworkColors()`, which resolves the built-in defaults against any per-network overrides the user sets in *Settings → Appearance → Network Colours*. Edits flow through the `LocalNetworkColors` CompositionLocal, so the map, legend, filter chips and reading badges all repaint at once.
- **Allocation-free rendering**: pre-allocated `Paint`, `Rect`, `GeoPoint`s, `Point`s; viewport culling + screen-rect culling in `CoverageGridOverlay.draw()`.

### 💾 Persistence
- **Room 2.8.4** with `signal_readings` and `recording_schedules` tables + KSP-generated DAOs.
- **TTL retention** in *Settings → Data → Auto-expire*: Forever / 30 days / 90 days / 6 months / 1 year. Persisted in `SharedPreferences`, swept on app start (silent) and immediately on manual change (with Snackbar).
- **Delete all** with confirmation dialog in the same Data section.
- **CSV export** via the Storage Access Framework - write directly to Drive, email, or any provider.

### 🎨 Theme & accessibility
- **Theme picker**: *Follow system* / *Light* / *Dark* (in *Settings → Appearance*).
- **Dynamic colour toggle**: opt out of Material You on Android 12+; falls back to the slate/sky palette on Android < 12.
- **Stable identity mapping** between `NetworkType` slots and `ColorScheme` roles:
  - 5G → `primary`, NR_NSA → `primaryContainer`, LTE → `tertiary`, HSPA → `tertiaryContainer`, GSM → `secondary`, EDGE → `secondaryContainer`, CDMA → `error`, Unknown → `outline`. So the legend is meaningful no matter which palette is active.

### 🧭 UI scaffold
- **Bottom navigation** between Map, List, and Settings views.
- **Immersive map tab**: the map runs edge-to-edge with no app bar; every control floats on the map itself - a top filter bar and a bottom-right control stack of **recenter → zoom-in → zoom-out** small FABs. osmdroid's built-in zoom buttons are disabled so these are the only zoom controls. Sampling is started and stopped from the Settings page.
- **Tap a coverage square** to open a details sheet: the tapped cell is highlighted and the sheet shows its centre coordinate, the dominant network, a per-network breakdown of reading counts, mean signal and best/worst readings seen, and the operators seen there.
- **Top app bar** (List / Settings root only, never on the immersive Map): app title only. No other clutter.
- **Floating filter bar** on the map: quick network toggles plus a **Filters** pill that opens the full network + operator filter modal sheet.
- **Compose `SnackbarHost`** in the outer `Scaffold` - survives configuration changes via a one-shot `Channel<String>` from the ViewModel.
- **List tab** with per-row `NetworkBadge`, operator name, colour-coded dBm chip and coordinates. Each card has inline **Show on map** and **Delete** buttons; tapping the card opens a detail bottom sheet (dBm, RSRP / RSRQ / SNR for LTE, MCC / MNC / cell ID, operator, provider, accuracy, time, lat/lng) with its own **Show on map** / **Delete** / **Close** actions.
- **Delete with undo**: deleting a reading (from a card, the detail sheet, or elsewhere) shows an **Undo** snackbar that restores it; wired through the ViewModel's `undoDeleteEvents` channel.
- **Jump-to-reading**: **Show on map** switches to the Map tab and recenters on that reading via a `focusEvents` flow.
- **Settings tab** with drill-out pages for **Permissions & Access** (App Permissions + Background Access) and Schedules, plus in-place dialogs for theme, battery usage, and retention.
- **Onboarding flow** on first launch: a carousel that requests location, notifications (Android 13+), activity recognition (Android 10+) and background location (Android 10+) before dropping into the app. The activity-recognition and background-location steps are optional (a **Not now** action skips them); because Android 11+ forbids an in-app background-location dialog, that step deep-links to system Settings for "Allow all the time". Any skipped grant can still be completed later from *Settings → Permissions & Access*.

### 🔐 Privacy posture
- No analytics SDK.
- No network calls other than OSM tile downloads (configurable `User-Agent` in `SigverageApp.onCreate`).
- No shared-storage write unless the user explicitly taps **Export**.
- `SharedPreferences`, the SQLite database, and the tile cache all live under app-private storage.

---

## Tech stack

| Concern              | Choice                                                                                                |
| -------------------- | ----------------------------------------------------------------------------------------------------- |
| Language             | Kotlin 2.3.0                                                                                          |
| UI                   | Jetpack Compose + Material 3 (BOM 2026.06.00), Material 3 `FilterChip`, `AlertDialog`                  |
| Maps                 | [osmdroid 6.1.18](https://github.com/osmdroid/osmdroid) (OpenStreetMap tiles, no API key)               |
| Cellular info        | `android.telephony.TelephonyManager` + `CellInfo` + `TelephonyDisplayInfo` (5G NSA, API 30+)          |
| Location             | `android.location.LocationManager` via `callbackFlow` (no Google Play Services dependency); adaptive interval / distance filter per `SamplingMode` |
| Activity recognition | Google Play Services Location (`play-services-location` 21.5.0) - movement-based sampling            |
| Storage              | Room 2.9.0 + KSP (`signal_readings` + `recording_schedules` tables, observed via `Flow`)              |
| Async                | Kotlin Coroutines 1.10.1 + `StateFlow`                                                                |
| Scheduling           | `AlarmManager` with exact alarms + `BroadcastReceiver` (survives reboots via `BOOT_COMPLETED`)         |
| Settings storage     | `SharedPreferences` (singleton in `PreferencesStore.kt`)                                               |
| minSdk / targetSdk   | 26 / 34                                                                                               |
| AGP / Gradle wrapper | 9.3.0 / 9.6.1                                                                                         |
| JDK                  | 21                                                                                                    |

---

## Architecture

```
MainActivity
   └── OnboardingScreen (first launch only) ── permission walkthrough
        └── SigverageTheme(dynamicColor, themeMode)        (Material 3 + dynamic palette)
             └── MainScreen  ── Scaffold
                  ├── TopAppBar     (List/Settings only; conditional stop icon while sampling)
                  ├── Tab content
                  │    ├── MapPanel   ── osmdroid MapView via AndroidView
                  │    │    ├── CoverageGridOverlay       ── Mercator tile aggregation + HSL+paint + operator filter
                  │    │    ├── MyLocationNewOverlay     ── modern custom location indicator
                  │    │    └── CoverageMapScreen       ── full-bleed map + floating filter bar + on-map control stack (recenter / zoom) + ModalBottomSheet filters + tile-details sheet
                  │    ├── ListPanel  ── LazyColumn with Card-based reading items (show-on-map + delete) + details bottom sheet
                  │    └── SettingsScreen ── Recording / Appearance / Map / Data / Permissions & Access / About
                  │         ├── SchedulesPage (drill-out: add/edit/delete/toggle schedules)
                  │         ├── ScheduleDialog (day chips + time pickers)
                  │         ├── PermissionsAccessPage (drill-out: App Permissions + Background Access reliability toggles)
                  │         ├── RetentionDialog, ThemeDialog, SamplingModeDialog
                  │         └── Export CSV launcher (Storage Access Framework)
                  └── NavigationBar  (Map ↔ List ↔ Settings)

MainViewModel  ── StateFlow ──► UI (collectAsState)
   ├── SignalRepository   ── Room DAOs     ── signals.db   (signal_readings + recording_schedules)
   ├── PreferencesStore   ── SharedPrefs   ── retentionDays, themeMode, dynamicColorEnabled, autoRecord, samplingMode, onboardingCompleted
   ├── events: Flow<String> for one-shot snackbars (e.g. "Removed 47 old readings")
   ├── undoDeleteEvents: Flow<SignalReading> for delete-with-undo
   └── focusEvents: Flow for jump-to-reading recenter on the map

SamplingService (foregroundServiceType=location)   ── ServiceCompat.startForeground on first line
   ├── Activity Recognition transitions   ── start/stop on movement detection
   ├── LocationTracker   ── LocationManager callbackFlow
   ├── CellularScanner   ── TelephonyManager.allCellInfo snapshot at every tick
   └── Smart sampling    ── one reading per ~50 m cell per visit; re-records on leave & return
                                 ↓
                        SignalRepository.add(reading)

ScheduleManager (AlarmManager)   ── registers exact alarms for each schedule
   ├── ScheduleReceiver   ── BroadcastReceiver: start/stop service on alarm
   └── BootReceiver       ── re-register all alarms after device reboot
```

Two deliberate separation-of-concerns choices worth noting:

- **Storage zoom ≠ visible map zoom.** Coverage readings are binned at a fixed storage zoom of Z=20 (~50 m cells at mid-latitudes). `MapController.setZoom()` is whatever the user pinches to. They are fully decoupled - zoom out to a city view and keep door-step granularity, or zoom in to building level and keep the same fine nationwide coverage.
- **Map palette is live, not static.** `rememberNetworkColors()` reads the resolved palette from the `LocalNetworkColors` CompositionLocal, provided at the activity root from `HomeUiState.networkColors` (built-in defaults + the user's per-network overrides). A `LaunchedEffect(networkColors)` injects it into `CoverageGridOverlay.setPalette(...)`, so recolouring a network in Settings repaints the grid immediately. Overrides are persisted per-network in `SharedPreferences`.

---

## Project layout

```
.
├── README.md
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml
├── gradle/wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/
        │   ├── values/      (strings, themes, colors)
        │   ├── xml/         (backup + data-extraction rules)
        │   ├── drawable/    (launcher bg/fg, notification icon, custom location indicator)
        │   └── mipmap-anydpi-v26/ (adaptive launcher icons)
        └── java/com/sigverage/app/
            ├── SigverageApp.kt           (Application + osmdroid config + notif channel)
            ├── MainActivity.kt               (Compose host)
            ├── model/
            │   ├── Models.kt                 (NetworkType + SignalReading + RecordingSchedule + ThemeMode)
            │   └── ThemeMode.kt              (System/Light/Dark enum)
            ├── data/
            │   ├── SignalDatabase.kt         (Room + TypeConverters + signalReadingDao + scheduleDao)
            │   ├── SignalRepository.kt       (singleton accessor + retention sweep + schedule CRUD)
            │   └── PreferencesStore.kt       (SharedPreferences wrapper)
            ├── cellular/CellularScanner.kt   (TelephonyManager snapshot, 5G NSA aware)
            ├── location/LocationTracker.kt   (LocationManager callbackFlow)
            ├── permissions/PermissionsInventory.kt   (single source of truth)
            ├── coverage/
            │   ├── CoverageModel.kt          (TileId, CellStats, NetworkAggregate, Mercator math, colorFor, aggregate)
            │   ├── CoverageGrid.kt           (osmdroid Overlay + slot grid + palette + operator filter)
            │   ├── CoverageFilterChips.kt    (Material 3 FilterChip strip, FlowRow-wrapped)
            │   └── CoverageMapScreen.kt      (full-bleed map + floating filter bar + on-map control stack: recenter / zoom + ModalBottomSheet filters + tile-details sheet)
            ├── service/
            │   ├── SamplingService.kt        (foreground service + activity recognition + smart sampling)
            │   ├── TransitionReceiver.kt     (BroadcastReceiver for activity transitions)
            │   ├── ScheduleManager.kt        (AlarmManager scheduling for recording schedules)
            │   ├── ScheduleReceiver.kt       (BroadcastReceiver for schedule alarms)
            │   └── BootReceiver.kt           (re-register alarms after device reboot)
            └── ui/
                ├── MainViewModel.kt          (StateFlow + Channel events: snackbars / undo-delete / focus + schedule CRUD)
                ├── MainScreen.kt             (Scaffold + tabs + permission flow + Snackbar / undo collector)
                ├── MapPanel.kt               (osmdroid + CoverageGridOverlay + chips + operator filter + focus events)
                ├── ListAndDetails.kt         (Card-based list w/ show-on-map + delete + detail bottom sheet)
                ├── OnboardingScreen.kt       (first-launch permission walkthrough)
                ├── SettingsScreen.kt         (Recording / Appearance / Map / Data / Permissions & Access / About + Background Access toggles)
                ├── SchedulesPage.kt          (drill-out: add/edit/delete/toggle recording schedules)
                ├── ScheduleDialog.kt         (day chips + time pickers)
                ├── TimePickerDialog.kt       (start/end time picker for schedules)
                ├── PermissionsSection.kt     (per-permission rows, generated from PermissionsInventory)
                ├── RetentionDialog.kt        (Forever / 30d / 90d / 6mo / 1y)
                ├── SamplingModeDialog.kt     (Automatic / Power Saver / Balanced / High Accuracy)
                ├── ThemeDialog.kt            (System / Light / Dark)
                └── theme/Theme.kt            (Material 3 + dynamic + rememberNetworkColors)
```

---

## How the coverage map encodes two dimensions

You have two categorical × continuous dimensions to show: **which network** (categorical, eight values) and **how strong** (continuous, dBm). At 12–20 dp tile sizes a marker can't carry either dimension legibly, so we use **one visual channel encoded cleverly**:

| Dimension  | Visual channel                                    | Why                                                                             |
| ---------- | ------------------------------------------------- | ------------------------------------------------------------------------------- |
| Network    | Box **fill colour hue**                           | Pre-attentive grouping - the eye chunks colour regions faster than shape or text. |
| Strength   | Box **fill alpha** (0.60 Strong → 0.22 Weak)      | Alpha survives being shrunk down; the hue identity stays usable everywhere. Kept translucent so the map underneath stays readable through the squares. |
| Secondary networks present | 2×4 **corner slot grid** in the bottom-right corner | Slot positions are fixed: row 0 has the modern nets, row 1 has fallbacks.       |
| Mean signal (exact) | Small **number in the top-left corner** | The dominant network's mean dBm - the precise value behind the opacity bucket, for when the band isn't specific enough. |

Each tile aggregates its readings into `CellStats.perNetwork: Map<NetworkType, NetworkAggregate>`. A single dominant network is then picked via `pickDominant()` (highest count; tiebreakers: more recent → stronger mean dBm). The corner grid still exposes the *non-dominant* networks that were present in the same tile - so you can read *"mostly LTE, with patches of 5G"* at a glance. The top-left number prints that dominant network's mean dBm (drawn allocation-free straight from the aggregate), so hue = network, opacity = strength band, number = mean signal.

A tile below ~22 dp either dimension suppresses the corner grid (rare at the default zoom 20 - kept as a safety net for when the user pans the map far enough); the mean-dBm number needs a little more room and is suppressed below ~30 dp.

The network filter chips (on the floating bar and in the "Filters" modal sheet) hide/reveal **both** encodings: turning 5G off removes 5G from the dominant pick *and* greys out its slot across every tile. Operator filter chips additionally hide tiles that have no readings from the selected carriers.

---

## Building

### Prerequisites

- **JDK 21** (`brew install --formulae openjdk@21`, `sdkman install java 21.0.x-tem`, or your distro package).
- **Android Studio Meerkat (2025.1) or newer.** Older Studio releases don't bundle AGP 9.x.
- **Android Platform 34** installed via SDK Manager (`compileSdk = 34`).
- **A real Android device** (USB debugging enabled) or an emulator running API 26+. *osmdroid tiles need an internet connection at runtime* - there's no offline mode yet.

### Steps

1. **Open in Android Studio** → *File → Open → select the project root*. Wait for the initial Gradle sync.
2. **Run** ▶︎ → choose your device (`Shift+F10`).
3. **First launch flow**:
   - Grant `Precise location` and `Coarse location` (one prompt).
   - Grant `Notifications` (Android 13+) so the foreground service can post its sticky notification.
   - Optionally grant `Background location` to keep sampling while the screen is off - Android bounces you to system settings for this one.

### Command-line

```bash
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

./gradlew installDebug
# Side-loads onto the first connected device.

./gradlew lint
# → app/build/reports/lint-results-debug.html
```

### Release builds

`app/build.gradle.kts` currently signs the **release** variant with the **`debug`** signing config for convenience. **Before publishing to the Play Store**, replace this with a real `signingConfigs { release { … } }` block. Recommended:

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("SIGNING_KEYSTORE") ?: "release.keystore")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

Add the four env vars to a **non-tracked** keystore.properties or your CI secrets store. The default debug build (`assembleDebug`) keeps working without any of this.

### Troubleshooting

- **`Could not find tools.jar`** → your Gradle daemon is running on an old Java 8 runtime instead of JDK 21. Check `JAVA_HOME` and Android Studio's Gradle JDK settings; `javac --version` should report 21.
- **`License for package … not accepted`** → open Android Studio, *SDK Manager → SDK Tools → accept licences*.
- **Empty map at first launch** → your device hasn't produced a GPS fix yet. Move outdoors or check that *Location* is turned on in system Settings.
- **Sampling stops after a few seconds** on Android 14 → make sure *POST_NOTIFICATIONS* and *Background location* are both granted. Without the notification the OS kills the foreground service.

---

## Permissions rationale

Every permission is justified by a concrete feature, and the manifest comments name the consumer.

| Permission | Required for | Manifest gate | Runtime |
| ---------- | ------------ | ------------- | ------- |
| `ACCESS_FINE_LOCATION` | GPS pin for every reading (lat/lng). | All API levels | Requested on first launch via `RequestMultiplePermissions`. |
| `ACCESS_COARSE_LOCATION` | Fallback when fine isn't granted (network-based fixes). | All API levels | Same prompt as fine. |
| `ACCESS_BACKGROUND_LOCATION` | Sampling keeps accumulating while the screen is off. | API 30+: Android routes this to system Settings - it can't be granted from an in-app prompt. | Surfaced as an optional onboarding step (Android 10+) and in Settings → Permissions & Access. The onboarding step raises an in-app **"Allow background location?"** confirmation with an explicit **Allow** button; confirming it then triggers the runtime dialog on Android 10 or deep-links to system Settings on Android 11+, where the user chooses "Allow all the time". |
| `POST_NOTIFICATIONS` | Sticky low-priority notification for `SamplingService`. | API 33+ only. | Asked on first launch alongside location. |
| `INTERNET` / `ACCESS_NETWORK_STATE` | osmdroid tile downloads. | All API levels. | Granted at install (normal permission). |
| `FOREGROUND_SERVICE` | The sampling service itself. | All API levels. | Granted at install. |
| `FOREGROUND_SERVICE_LOCATION` | Typed FGS for Android 14. | Required on API 34. | Granted at install. |
| `ACTIVITY_RECOGNITION` | Detect movement for activity-based sampling. | API 29+. | Requested during onboarding (Android 10+) and from Settings → Permissions & Access. |
| `RECEIVE_BOOT_COMPLETED` | Re-register schedule alarms after device reboot. | All API levels. | Granted at install. |
| `SCHEDULE_EXACT_ALARM` | Schedule exact alarms for recording schedules. | API 31+. | Granted at install; can be revoked by the user, and re-granted from *Settings → Permissions & Access → Background Access → Exact Schedule Timing*. |
| `READ_PHONE_STATE` (`maxSdkVersion=27`) | `allCellInfo` on API 24–27. Capped at 27 in the manifest because APIs 28+ no longer require it for cell info. | API ≤ 27 only. | Granted at install on those devices. |

**Permission flow**: toggling sampling triggers `RequestMultiplePermissions` when anything is missing. Background location is automatically routed to system Settings by Android - we surface a status banner in *Settings → Permissions & Access* if it's still missing, with an **Open settings** action deep-linking to `ACTION_APPLICATION_DETAILS_SETTINGS`.

**Beyond the manifest**: Two system grants that have no manifest declaration are surfaced live in the **Background Access** card of *Settings → Permissions & Access* — the **battery-optimisation exemption** and, on Android 12+, **exact-alarm scheduling**. Each row reads its current status (`PowerManager.isIgnoringBatteryOptimizations`, `AlarmManager.canScheduleExactAlarms`) on every resume.

**Single source of truth**: `app/src/main/java/com/sigverage/app/permissions/PermissionsInventory.kt`. UI rows in the *App Permissions* card of *Settings → Permissions & Access* are generated from this list, so adding a new permission is one entry.

---

## Settings tour

The bottom navigation has three tabs. The first two (Map / List) show your data; the third (Settings) is where every knob lives.

### 🗺️ Map | 📋 List
- **Map**: coverage boxes on OpenStreetMap. Floating filter chips at the top; on-map control stack (recenter / zoom) at the bottom-right. Tap a coverage square to open its details sheet (dominant network, per-network counts, mean signal and best/worst readings, operators). *Snackbar* sits in the outer Scaffold so messages reach you even when you drag the map around.
- **List**: every reading, most-recent first, each card showing network badge, operator, a colour-coded dBm chip and coordinates. Inline **Show on map** and **Delete** buttons per card; tap the card → bottom sheet with the full reading (signal dBm, LTE RSRP/RSRQ/SNR, MCC/MNC/cell ID, operator, provider, accuracy, timestamp, lat/lng) plus Show on map / Delete / Close. Deleting shows an **Undo** snackbar.

### ⚙️ Settings
| Section | Row | What it does |
| ------- | --- | ------------ |
| **Recording** | Recording | Switch. Starts/stops sampling on demand; the subtitle shows the live status (*Running* / *Stopped*). Turning it on checks location/notification permissions first and surfaces a snackbar if any are missing. This is the primary place to start and stop recording. |
| **Recording** | Auto-record on launch | Switch. When on, sampling begins automatically the moment the app opens (after onboarding). Service keeps running with a sticky notification until you turn recording off here. Default **off** - opt-in to avoid surprise battery drain. |
| **Recording** | Battery Usage | Opens a picker: *Automatic* / *Power Saver* / *Balanced* / *High Accuracy* - trades GPS fix frequency / accuracy against battery drain while recording. Automatic adapts to Battery Saver and low-battery state. |
| **Recording** | Schedules (drill-out) | Taps open a dedicated sub-page where you can add/edit/delete/toggle recording schedules. Each schedule has a name, day-of-week selection, and start/end time (overnight windows supported). Schedules use AlarmManager with exact alarms. |
| **Appearance** | Theme | *Follow system* / *Light* / *Dark*. |
| **Appearance** | Dynamic colour | Switch. On Android 12+ uses Material You; off falls back to the slate/sky palette. |
| **Map** | Default map filters (drill-out) | Pick which networks and operators the coverage map loads with (e.g. *5G only*, *Vodafone only*). Persisted in SharedPreferences. The map opens with these each time; on-map chip toggles are temporary and reset to the saved default on the next Map open. No operators selected = show all. |
| **Data** | Auto-expire readings | Forever / 30 d / 90 d / 6 mo / 1 y. Persisted in SharedPreferences. Silent sweep on app start. Snackbar feedback on manual change. |
| **Data** | Export CSV | Export readings to CSV via Storage Access Framework. Shows count (e.g., "Export 42 readings to CSV"). Snackbar feedback on completion. |
| **Data** | Delete all readings | Confirmation dialog → hard delete of the whole `signal_readings` table. |
| **Permissions & Access** | Permissions & Access (drill-out) | Dedicated sub-page with back button. **App Permissions** card: status banner (all granted / some missing) + per-permission rows with grant/open-settings actions. **Background Access** card: *Run Reliably in Background* (battery-optimisation exemption) and, on Android 12+, *Exact Schedule Timing* (exact-alarm grant) - each deep-links to the relevant system screen and reflects its live status. |
| **About** | App name, version, Android version, blurb | Static info. |

### Top app bar
The immersive **Map** tab has no app bar - all its controls float on the map. The **List** and **Settings** (root) tabs show a slim app bar with just the app title; recording is started and stopped from *Settings → Recording* (or a schedule / auto-record on launch), so the bar carries no pause or capture actions.

---

## OpenStreetMap tile policy

osmdroid fetches tiles from the OpenStreetMap public servers (or any tile source you wire in). Their [tile usage policy](https://operations.osmfoundation.org/policies/tiles/) asks apps to:

1. **Set a real `User-Agent`** that identifies the app and includes a contact channel. Configured in `SigverageApp.onCreate()` - change it to something like `Sigverage/1.0 (+https://your-website.example)`.
2. **Don't be a heavy site** - don't precompute large regions for offline use. Sigverage only zooms on demand, so this should be fine.
3. **Bulk downloads require your own tile server** or a paid provider (e.g. Mapbox / Stadia / Thunderforest). If you want true offline, bake tiles into the APK using [MOBAC](https://mobac.sourceforge.io/) + osmdroid's `MAPBOX`/`ZIP` archives.

If you change the tile source, do it in `MapPanel.MapView`'s `setTileSource(TileSourceFactory.MAPNIK)` call. Other `TileSourceFactory` options exist for OpenTopoMap, USGS, etc., and paid providers expose their own factories.

---

## Cellular-reading caveats

- **dBm is relative, not absolute.** A reading of *-99 dBm* on one phone can be *-105 dBm* on another. Use the **colour buckets** (green = strong, amber = OK, red = weak) comparatively, not as an absolute measurement.
- **5G NSA oscillates.** 5G NSA needs both an LTE master and an NR secondary. If only one is visible to the radio (e.g. in a basement), the classification can flip between `LTE` and `NR_NSA` between fixes. That's accurate to the device, not a bug.
- **CDMA-only devices** (older US carriers) return very little cell identity. The UI degrades gracefully to generic `dBm` and the *Other* colour.
- **First fix inside a building** can take a few seconds. The status banner says "Waiting for GPS fix…" until one arrives.
- **Sim cards without a data plan** still report cell identity but may report `"UNKNOWN"` `dataNetworkType`. We classify those as `Unknown`.

---

## License

Private / unreleased. Add a `LICENSE` file (MIT / Apache 2.0 / your choice) before you publish, and update the badge at the top of this README.
