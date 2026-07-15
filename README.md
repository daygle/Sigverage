# Signal Spotter

A native Android app that records the **quality and type of cellular network
(2G / 3G / LTE / 5G NR / 5G NSA)** at your current location and visualises
every reading on an **OpenStreetMap** background.

* Fully **on-device**: no account, no backend, no telemetry.
* Markers are colour-coded by cellular technology and **sized by signal
  strength** (strong / OK / weak buckets).
* Optional **background sampling** via a typed foreground service so readings
  keep accumulating while the phone screen is off.

This is the build of the source tree in this directory. To produce an APK you
need a real machine with the Android SDK.

## Stack

| Concern        | Choice                                                   |
| -------------- | -------------------------------------------------------- |
| Language       | Kotlin 1.9.24                                            |
| UI             | Jetpack Compose + Material 3 (BOM 2024.06.00)            |
| Maps           | [osmdroid 6.1.18](https://github.com/osmdroid/osmdroid)  (OpenStreetMap tiles, no API key) |
| Cellular info  | `android.telephony.TelephonyManager` + `CellInfo`        |
| Location       | `android.location.LocationManager` (no Google Play Services dep) |
| Storage        | Room 2.6.1 (SQLite), KSP-generated DAOs                  |
| Async          | Kotlin Coroutines 1.8.1                                  |
| Min SDK / Target SDK | 26 / 34                                            |
| AGP / Gradle   | 8.5.2 / 8.7                                              |

## Architecture

```
MainActivity
   └── SignalSpotterTheme (Material 3)
        └── MainScreen  ── Scaffold
             ├── TopAppBar     (start/stop, interval, export, delete-all)
             ├── MapPanel      (osmdroid MapView via AndroidView + cached Marker bitmaps)
             │   or
             ├── ListPanel     (LazyColumn + per-row NetworkBadge)
             └── NavigationBar (Map ↔ List)

MainViewModel ── StateFlow ──► SignalRepository ──► Room (signal_readings table)
                          ▲
                          └── SamplingService (foregroundServiceType=location)
                                 ├── LocationTracker  (callbackFlow)
                                 └── CellularScanner (TelephonyManager.allCellInfo)
```

* `CellularScanner` picks the **registered cell with the strongest dBm**, then
  classifies it. 5G NSA is detected via `TelephonyDisplayInfo.overrideNetworkType
  == OVERRIDE_NETWORK_TYPE_NR_NSA` (API 30+) — we never trust `dataNetworkType`
  alone because NSA anchors to an LTE control plane. EDGE vs GSM is decided by
  `dataNetworkType`. `CellInfoTdscdma` is gated on API 29.
* `SamplingService` is promoted to foreground on the **first line** of
  `onStartCommand` via `ServiceCompat.startForeground(...,
  FOREGROUND_SERVICE_TYPE_LOCATION)` so Android 14's 5-second rule is satisfied
  regardless of what happens downstream.
* `Marker` icons are generated as `BitmapDrawable`s and **cached in a
  `Map<String, Drawable>` keyed by `(networkType, signalBucket)`** so
  recomposition doesn't keep allocating bitmaps.

## Building

### Prerequisites

* JDK 17
* Android Studio Hedgehog (2023.1.1) or newer
* Android Platform 34 (compileSdk) installed via SDK Manager
* A real Android device (USB debugging enabled) or an emulator running API 26+

### Steps

1. **Copy this folder** to a machine with the Android SDK installed. The
   sandbox this README lives in cannot compile APKs.
2. **Open in Android Studio**: `File → Open → select the project root`.
3. Wait for Gradle sync. Studio downloads AGP, Compose BOM, Room, osmdroid and
   friends.
4. **Run**: plug in a device and press ▶︎ (or `Shift+F10`).

### Command-line build

```bash
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

./gradlew assembleRelease
# ⚠️ Uses the debug signing config for convenience.
# Wire a real `signingConfigs { release { … } }` block in app/build.gradle.kts
# before publishing to the Play Store.
```

The Gradle wrapper is pinned to **8.7** in `gradle/wrapper/gradle-wrapper.properties`.

## Permissions

| Permission | Required for | Notes |
|---|---|---|
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | GPS pins for every reading | Requested on first launch |
| `ACCESS_BACKGROUND_LOCATION` | Sampling while screen is off | API 30+ bounces to settings |
| `POST_NOTIFICATIONS` | Foreground-service notification | API 33+ only |
| `INTERNET` / `ACCESS_NETWORK_STATE` | osmdroid tile downloads | Standard |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_LOCATION` | The sampling service itself | Required on API 34 |
| `READ_PHONE_STATE` (`maxSdkVersion=27`) | `allCellInfo` on API 24-27 | Removed on API 28+ |

The permission flow lives in `MainScreen.toggleSampling`. When anything is
missing it triggers `RequestMultiplePermissions`; for background location the
user is bounced to system settings automatically by the OS.

## Using the app

* **Tap "Record here"** to drop a marker at your current fix.
* **Tap any marker** (or any list row) for the full reading — RSRP / RSRQ / SNR
  for LTE, MCC / MNC / cell ID, operator, accuracy, time, lat/lng.
* **Tap the play icon in the AppBar** to start background sampling. It posts a
  persistent low-priority notification so Android keeps the service alive.
* **The clock icon in the AppBar** opens the sampling-interval picker (3 s / 5 s
  / 15 s / 60 s).
* **The share icon** writes every reading as CSV via the Storage Access
  Framework — drop the file into Google Drive, your email attachment tray, or
  any provider you have installed.
* **The sweep icon** deletes the entire database after a confirmation dialog.

## OpenStreetMap tile policy

osmdroid fetches tiles from the OpenStreetMap public servers. Their tile usage
policy asks apps to:

1. Set a **real `User-Agent` string** that identifies the app and includes a
   contact channel. Set in `SignalSpotterApp.onCreate()` — change to something
   like `SignalSpotter/1.0 (+https://your-website.example)`.
2. **Don't be a heavy site** (don't precompute large regions for offline use).
   Signal Spotter only zooms on demand, so this should be fine.
3. **Bulk downloads require your own tile server** or a paid provider. If you
   need offline tiles, bake them into the APK using MOBAC + osmdroid's
   `MAPBOX`/`ZIP` archives.

## Caveats about cellular readings

* Reported dBm varies significantly between devices and modems. A reading of
  *-99 dBm* on one phone may read *-105 dBm* on another. Use the **relative**
  colour buckets (green = strong, amber = OK, red = weak) rather than as an
  absolute measurement.
* 5G NSA needs both an LTE master and an NR secondary. If only one is visible
  to the radio (e.g. inside a basement) the classification can oscillate
  between `LTE` and `NR_NSA` between fixes. That's accurate to the device, not
  a bug.
* CDMA-only devices (older US carriers) return very little cell identity; the
  UI degrades gracefully to generic `dBm` and the *Other* colour.
* The first fix inside a building can take a few seconds; the status banner
  says "Waiting for GPS fix…" until one arrives.

## Project layout

```
.
├── README.md
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/
        │   ├── values/      (strings, themes, colors)
        │   ├── xml/         (backup + data-extraction rules)
        │   ├── drawable/    (launcher bg/fg, monocolor notification icon)
        │   └── mipmap-anydpi-v26/ (adaptive launcher icons)
        └── java/com/signalspotter/app/
            ├── SignalSpotterApp.kt           (Application + osmdroid config + notif channel)
            ├── MainActivity.kt               (Compose host)
            ├── model/Models.kt               (NetworkType + SignalReading entity)
            ├── data/SignalDatabase.kt        (Room + TypeConverters + DAO)
            ├── data/SignalRepository.kt      (singleton accessor)
            ├── cellular/CellularScanner.kt   (TelephonyManager snapshot)
            ├── location/LocationTracker.kt   (LocationManager callbackFlow)
            ├── service/SamplingService.kt    (typed foreground service)
            └── ui/
                ├── MainViewModel.kt
                ├── MainScreen.kt             (Scaffold + tabs + permission flow)
                ├── MapPanel.kt               (osmdroid + cached marker bitmaps)
                ├── ListAndDetails.kt         (list + bottom sheet)
                └── theme/Theme.kt            (Material 3 + dynamic color)
```

## Roadmap (not in this build)

* Heatmap / cluster overlay for hundreds of markers.
* Wi-Fi scanning alongside cellular (Android 9+ has `WifiManager.startScan`
  with throttling — needs workarounds).
* GPX / KML export alongside CSV.
* Route replay: draw a polyline through every reading in an animated tour.
* Live signal vs. speed chart while you're moving.
* Migration framework for Room v2+ once you add columns.
