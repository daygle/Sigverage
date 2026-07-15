# Signal Spotter

> **Native Android app that records the cellular technology and signal strength at your location, then paints a coverage map from your own readings.**
> 2G / 3G / HSPA / LTE / 5G NR / 5G NSA, on OpenStreetMap, fully on-device.

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-26%E2E40–34-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-Private-lightgrey)](#license)

---

## Table of contents

1. [TL;DR](#tldr)
2. [Screenshots](#screenshots)
3. [Features](#features)
4. [Tech stack](#tech-stack)
5. [Architecture](#architecture)
6. [Project layout](#project-layout)
7. [How the coverage map encodes two dimensions](#how-the-coverage-map-encodes-two-dimensions)
8. [Building](#building)
9. [Permissions rationale](#permissions-rationale)
10. [Settings tour](#settings-tour)
11. [OpenStreetMap tile policy](#openstreetmap-tile-policy)
12. [Cellular-reading caveats](#cellular-reading-caveats)
13. [Roadmap](#roadmap)
14. [License](#license)

---

## TL;DR

Signal Spotter runs on your phone, records a `SignalReading` (network type, signal dBm, GPS fix, timestamp, MCC/MNC/cell ID where available) at a configurable interval, and aggregates your readings into a coverage overlay on top of OpenStreetMap. **Everything stays on the device.** There is no account, no backend, no telemetry, no remote sync. You can export to CSV at any time and the retention policy is configurable from *Settings → Data → Auto-expire*.

The map paints a small filled **box per Mercator tile** at a configurable storage zoom (default 18 ≈ 150 m per side). Each box's **hue** indicates which network dominates, and its **alpha** indicates mean strength. A fixed 2×4 **corner slot grid** in the bottom-right of every box lets readers instantly see which *other* networks were also present at that location. Filter chips at the top of the map hide / reveal each network family.

---

## Screenshots

Drop real device captures into `./docs/screenshots/` and uncomment the lines below — GitHub will render them on the repo page.

```text
docs/screenshots/
├── 01-map-coverage.png         ← map view: boxed coverage overlay on OSM
├── 02-filter-chips.png         ← filter chips, mid-toggle
├── 03-granularity-slider.png   ← AppBar cell-granularity slider dialog
├── 04-settings-appearance.png  ← Settings → Appearance: theme + dynamic colour
├── 05-data-retention.png       ← Settings → Data: auto-expire + delete-all
├── 06-list-and-detail.png      ← List tab with row tap → bottom sheet
└── 07-permissions.png          ← Settings → Permissions with all granted
```

Markdown placeholders (leave them commented until you push images):

```markdown
<!-- ![Coverage overlay](docs/screenshots/01-map-coverage.png) -->
<!-- ![Granularity slider](docs/screenshots/03-granularity-slider.png) -->
<!-- ![Settings → Appearance](docs/screenshots/04-settings-appearance.png) -->
```

---

## Features

### 📡 Recording
- **Foreground sampling service** (`SamplingService`) with `foregroundServiceType = location` — promoted on the *first line* of `onStartCommand` so Android 14's 5-second rule is always satisfied.
- **Configurable interval**: 3 s / 5 s / 15 s / 60 s, live in *Settings → Recording*.
- **"Record here" snapshot** for one-off readings without starting the service.
- **CellularScanner** picks the strongest registered cell and classifies it:
  - 5G NSA detection via `TelephonyDisplayInfo.overrideNetworkType` (API 30+); we don't trust `dataNetworkType` alone because NSA anchors to an LTE control plane.
  - EDGE vs GSM via `dataNetworkType`. `CellInfoTdscdma` is gated on API 29.
- **Location via `callbackFlow`** on `LocationManager` — no Google Play Services dependency.

### 🗺️ Coverage map
- **HSL-hybrid encoding**: hue = dominant network, alpha = mean dBm bucket (Strong / OK / Weak).
- **2×4 corner slot grid**: a fixed-layout indicator grid in the bottom-right of every tile, with one slot per `NetworkType` (5G, NR_NSA, LTE, HSPA, GSM, EDGE, CDMA, Other). Filled = network present; alpha = its bucket strength. Filter-aware: disabled chips remove their slot everywhere. Slot positions are fixed so the legend is stable across the whole map.
- **Web-Mercator slippy-tile aggregation** at zoom 12 (≈9.6 km) … zoom 19 (≈75 m). Default storage zoom is **18** (≈150 m, "a doorway / a city block").
- **Decoupled zoom axes**: visible map zoom (pinch gestures) is independent of coverage storage zoom (slider in *Settings → Coverage map*).
- **Filter chips** at the top of the map: multi-select toggle for each `NetworkType`, live re-render without re-aggregation.
- **Themed palette**: marker / box colours come from `rememberNetworkColors(MaterialTheme.colorScheme)`, so the legend stays consistent across Light / Dark / Material You.
- **Allocation-free rendering**: pre-allocated `Paint`, `Rect`, `GeoPoint`s, `Point`s; viewport culling + screen-rect culling in `CoverageGridOverlay.draw()`.

### 💾 Persistence
- **Room 2.6.1** with a single `signal_readings` table + KSP-generated DAO.
- **TTL retention** in *Settings → Data → Auto-expire*: Forever / 30 days / 90 days / 6 months / 1 year. Persisted in `SharedPreferences`, swept on app start (silent) and immediately on manual change (with Snackbar).
- **Delete all** with confirmation dialog in the same Data section.
- **CSV export** via the Storage Access Framework — write directly to Drive, email, or any provider.

### 🎨 Theme & accessibility
- **Theme picker**: *Follow system* / *Light* / *Dark* (in *Settings → Appearance*).
- **Dynamic colour toggle**: opt out of Material You on Android 12+; falls back to the slate/sky palette on Android < 12.
- **Stable identity mapping** between `NetworkType` slots and `ColorScheme` roles:
  - 5G → `primary`, NR_NSA → `primaryContainer`, LTE → `tertiary`, HSPA → `tertiaryContainer`, GSM → `secondary`, EDGE → `secondaryContainer`, CDMA → `error`, Unknown → `outline`. So the legend is meaningful no matter which palette is active.

### 🧭 UI scaffold
- **Bottom navigation** between Map and List views.
- **Single AppBar action group**: play / pause (start/stop sampling) + export. Every other control moved under the **Settings** tab to keep the AppBar calm.
- **Compose `SnackbarHost`** in the outer `Scaffold` — survives configuration changes via a one-shot `Channel<String>` from the ViewModel.
- **List tab** with per-row `NetworkBadge` and tap → detail bottom sheet (RSRP / RSRQ / SNR for LTE, MCC / MNC / cell ID, operator, accuracy, time, lat/lng).

### 🔐 Privacy posture
- No analytics SDK.
- No network calls other than OSM tile downloads (configurable `User-Agent` in `SignalSpotterApp.onCreate`).
- No shared-storage write unless the user explicitly taps **Export**.
- `SharedPreferences`, the SQLite database, and the tile cache all live under app-private storage.

---

## Tech stack

| Concern              | Choice                                                                                                |
| -------------------- | ----------------------------------------------------------------------------------------------------- |
| Language             | Kotlin 1.9.24                                                                                         |
| UI                   | Jetpack Compose + Material 3 (BOM 2024.06.00), Material 3 `FilterChip`, `Slider`, `AlertDialog`        |
| Maps                 | [osmdroid 6.1.18](https://github.com/osmdroid/osmdroid) (OpenStreetMap tiles, no API key)               |
| Cellular info        | `android.telephony.TelephonyManager` + `CellInfo` + `TelephonyDisplayInfo` (5G NSA, API 30+)          |
| Location             | `android.location.LocationManager` via `callbackFlow` (no Google Play Services dependency)            |
| Storage              | Room 2.6.1 + KSP (`signal_readings` table, observed via `Flow`)                                        |
| Async                | Kotlin Coroutines 1.8.1 + `StateFlow`                                                                 |
| Settings storage     | `SharedPreferences` (singleton in `PreferencesStore.kt`)                                               |
| Min SDK / Target SDK | 26 / 34                                                                                               |
| AGP / Gradle wrapper | 8.5.2 / 8.7                                                                                           |
| JDK                  | 17                                                                                                    |

---

## Architecture

```
MainActivity
   └── SignalSpotterTheme(dynamicColor, themeMode)        (Material 3 + dynamic palette)
        └── MainScreen  ── Scaffold
             ├── TopAppBar     (start/stop + export only)
             ├── Tab content
             │    ├── MapPanel   ── osmdroid MapView via AndroidView
             │    │    ├── CoverageGridOverlay    ── Mercator tile aggregation + HSL+paint
             │    │    └── CoverageFilterChips   ── Multi-select Material 3 chips
             │    └── ListPanel  ── LazyColumn + per-row NetworkBadge + bottom sheet
             └── NavigationBar  (Map ↔ List ↔ Settings)

MainViewModel  ── StateFlow ──► UI (collectAsState)
   ├── SignalRepository   ── Room DAO      ── signals.db   (single source of truth)
   ├── PreferencesStore   ── SharedPrefs   ── retentionDays, themeMode, dynamicColorEnabled
   └── events: Flow<String> for one-shot snackbars (e.g. "Removed 47 old readings")

SamplingService (foregroundServiceType=location)   ── ServiceCompat.startForeground on first line
   ├── LocationTracker   ── LocationManager callbackFlow
   └── CellularScanner   ── TelephonyManager.allCellInfo snapshot at every tick
                                 ↓
                        SignalRepository.add(reading)
```

Two deliberate separation-of-concerns choices worth noting:

- **Storage zoom ≠ visible map zoom.** The AppBar slider (`coverageZoom: Int`, default 18) drives how readings are binned. `MapController.setZoom()` is whatever the user pinches to. They are fully decoupled — zoom out to a city view and keep door-step granularity, or zoom in to building level and keep a coarse nationwide view.
- **Map palette is live, not static.** `rememberNetworkColors(MaterialTheme.colorScheme)` recomputes on every theme / dynamic-colour change and a `LaunchedEffect(networkColors)` injects it into `CoverageGridOverlay.setPalette(...)`. The legend is meaningful across both Material You and the static slate/sky fallback.

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
        │   ├── drawable/    (launcher bg/fg, notification icon)
        │   └── mipmap-anydpi-v26/ (adaptive launcher icons)
        └── java/com/signalspotter/app/
            ├── SignalSpotterApp.kt           (Application + osmdroid config + notif channel)
            ├── MainActivity.kt               (Compose host)
            ├── model/
            │   ├── Models.kt                 (NetworkType + SignalReading entity + ThemeMode)
            │   └── ThemeMode.kt              (System/Light/Dark enum)
            ├── data/
            │   ├── SignalDatabase.kt         (Room + TypeConverters + DAO)
            │   ├── SignalRepository.kt       (singleton accessor + retention sweep)
            │   └── PreferencesStore.kt       (SharedPreferences wrapper)
            ├── cellular/CellularScanner.kt   (TelephonyManager snapshot, 5G NSA aware)
            ├── location/LocationTracker.kt   (LocationManager callbackFlow)
            ├── permissions/PermissionsInventory.kt   (single source of truth)
            ├── coverage/
            │   ├── CoverageModel.kt          (TileId, CellStats, NetworkAggregate, Mercator math, colorFor)
            │   ├── CoverageGrid.kt           (osmdroid Overlay + slot grid + palette)
            │   └── CoverageFilterChips.kt    (Material 3 FilterChip strip)
            ├── service/SamplingService.kt    (typed foreground service)
            └── ui/
                ├── MainViewModel.kt          (StateFlow + Channel<String> events)
                ├── MainScreen.kt             (Scaffold + tabs + permission flow + Snackbar collector)
                ├── MapPanel.kt               (osmdroid + CoverageGridOverlay + chips)
                ├── ListAndDetails.kt         (list + bottom sheet)
                ├── GranularityDialog.kt      (slider 12-19 + live "X m per cell" label)
                ├── RetentionDialog.kt        (Forever / 30d / 90d / 6mo / 1y)
                ├── ThemeDialog.kt            (System / Light / Dark)
                ├── SettingsScreen.kt         (Recording / Coverage / Data / Permissions / Appearance / About)
                └── theme/Theme.kt            (Material 3 + dynamic + rememberNetworkColors)
```

---

## How the coverage map encodes two dimensions

You have two categorical × continuous dimensions to show: **which network** (categorical, eight values) and **how strong** (continuous, dBm). At 12–20 dp tile sizes a marker can't carry either dimension legibly, so we use **one visual channel encoded cleverly**:

| Dimension  | Visual channel                                    | Why                                                                             |
| ---------- | ------------------------------------------------- | ------------------------------------------------------------------------------- |
| Network    | Box **fill colour hue**                           | Pre-attentive grouping — the eye chunks colour regions faster than shape or text. |
| Strength   | Box **fill alpha** (0.95 Strong → 0.34 Weak)      | Alpha survives being shrunk down; the hue identity stays usable everywhere.    |
| Secondary networks present | 2×4 **corner slot grid** in the bottom-right corner | Slot positions are fixed: row 0 has the modern nets, row 1 has fallbacks.       |

Each tile aggregates its readings into `CellStats.perNetwork: Map<NetworkType, NetworkAggregate>`. A single dominant network is then picked via `pickDominant()` (highest count; tiebreakers: more recent → stronger mean dBm). The corner grid still exposes the *non-dominant* networks that were present in the same tile — so you can read *"mostly LTE, with patches of 5G"* at a glance.

A tile below ~22 dp either dimension suppresses the corner grid (rare at the default zoom 18 — kept as a safety net for when the user dials the slider down).

Filter chips above the map hide/reveal **both** encodings: turning 5G off removes 5G from the dominant pick *and* greys out its slot across every tile.

---

## Building

### Prerequisites

- **JDK 17** (`brew install --formulae openjdk@17`, `sdkman install java 17.0.10-tem`, or your distro package).
- **Android Studio Hedgehog (2023.1.1) or newer.** Older Studio releases don't bundle AGP 8.5.
- **Android Platform 34** installed via SDK Manager (`compileSdk = 34`).
- **A real Android device** (USB debugging enabled) or an emulator running API 26+. *osmdroid tiles need an internet connection at runtime* — there's no offline mode yet.

### Steps

1. **Open in Android Studio** → *File → Open → select the project root*. Wait for the initial Gradle sync.
2. **Run** ▶︎ → choose your device (`Shift+F10`).
3. **First launch flow**:
   - Grant `Precise location` and `Coarse location` (one prompt).
   - Grant `Notifications` (Android 13+) so the foreground service can post its sticky notification.
   - Optionally grant `Background location` to keep sampling while the screen is off — Android bounces you to system settings for this one.

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

- **`Could not find tools.jar`** → JDK 17 isn't on `JAVA_HOME`. `javac --version` should report 17.
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
| `ACCESS_BACKGROUND_LOCATION` | Sampling keeps accumulating while the screen is off. | API 30+: Android routes this to system Settings — it can't be granted from an in-app prompt. | User must explicitly choose "Allow all the time". |
| `POST_NOTIFICATIONS` | Sticky low-priority notification for `SamplingService`. | API 33+ only. | Asked on first launch alongside location. |
| `INTERNET` / `ACCESS_NETWORK_STATE` | osmdroid tile downloads. | All API levels. | Granted at install (normal permission). |
| `FOREGROUND_SERVICE` | The sampling service itself. | All API levels. | Granted at install. |
| `FOREGROUND_SERVICE_LOCATION` | Typed FGS for Android 14. | Required on API 34. | Granted at install. |
| `WAKE_LOCK` | Keep the CPU alive during short sampling windows. | All API levels. | Granted at install. |
| `READ_PHONE_STATE` (`maxSdkVersion=27`) | `allCellInfo` on API 24–27. Capped at 27 in the manifest because APIs 28+ no longer require it for cell info. | API ≤ 27 only. | Granted at install on those devices. |

**Permission flow**: `MainScreen.toggleSampling` triggers `RequestMultiplePermissions` when anything is missing. Background location is automatically routed to system Settings by Android — we surface a status banner in *Settings → Permissions* if it's still missing, with an **Open settings** action deep-linking to `ACTION_APPLICATION_DETAILS_SETTINGS`.

**Single source of truth**: `app/src/main/java/com/signalspotter/app/permissions/PermissionsInventory.kt`. UI rows in *Settings → Permissions* are generated from this list, so adding a new permission is one entry.

---

## Settings tour

The bottom navigation has three tabs. The first two (Map / List) show your data; the third (Settings) is where every knob lives.

### 🗺️ Map | 📋 List
- **Map**: coverage boxes on OpenStreetMap. Filter chips at the top. *Snackbar* sits in the outer Scaffold so messages reach you even when you drag the map around.
- **List**: every reading, most-recent first. Tap → bottom sheet with the full reading (signal dBm, MCC/MNC/cell ID, operator, accuracy, timestamp, lat/lng).

### ⚙️ Settings
| Section | Row | What it does |
| ------- | --- | ------------ |
| **Recording** | Sample interval | 3 s / 5 s / 15 s / 60 s. |
| **Coverage map** | Cell granularity | Slider 12 (≈9.6 km / tile) → 19 (≈75 m / tile). Live label: *"150 m per cell"*. |
| **Data** | Auto-expire readings | Forever / 30 d / 90 d / 6 mo / 1 y. Persisted in SharedPreferences. Silent sweep on app start. Snackbar feedback on manual change. |
| **Data** | Delete all readings | Confirmation dialog → hard delete of the whole `signal_readings` table. |
| **Permissions** | Precise location, Coarse, Background, Notifications, Phone state | Live grant status, **Grant** / **Open settings** actions, refreshes on `ON_RESUME`. |
| **Appearance** | Theme | *Follow system* / *Light* / *Dark*. |
| **Appearance** | Dynamic colour | Switch. On Android 12+ uses Material You; off falls back to the slate/sky palette. |
| **About** | App name, version, license | Static info. |

### AppBar (Map & List)
Two icons only:
- ▶︎ / ⏸ — start or stop sampling.
- ⤴ — Export readings as CSV via the Storage Access Framework.

---

## OpenStreetMap tile policy

osmdroid fetches tiles from the OpenStreetMap public servers (or any tile source you wire in). Their [tile usage policy](https://operations.osmfoundation.org/policies/tiles/) asks apps to:

1. **Set a real `User-Agent`** that identifies the app and includes a contact channel. Configured in `SignalSpotterApp.onCreate()` — change it to something like `SignalSpotter/1.0 (+https://your-website.example)`.
2. **Don't be a heavy site** — don't precompute large regions for offline use. Signal Spotter only zooms on demand, so this should be fine.
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

## Roadmap

Already shipped in this build: ⚙️ coverage grid + filter chips, **TTL retention**, **granularity slider**, **theme picker**, **dynamic colour**, **Settings tab**, multi-network slot grid, live network palette.

Next up:

- Heatmap / cluster overlay for hundreds of cells per region (current overlay scales to ~thousands; thousands-of-thousands needs quad-tree culling).
- Wi-Fi scanning alongside cellular (Android 9+ throttles `WifiManager.startScan`; needs workarounds).
- GPX / KML export alongside CSV — the geometry is already there, just a serializer.
- Route replay: draw a polyline through every reading in an animated tour.
- Live signal-vs-speed chart while the user is moving.
- Migration framework for Room v2+ (you're safe to add columns today; this just smooths upgrades).

---

## License

Private / unreleased. Add a `LICENSE` file (MIT / Apache 2.0 / your choice) before you publish, and update the badge at the top of this README.
