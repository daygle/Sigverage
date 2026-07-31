# Sigverage

> **Native cellular signal mapper. Record cellular technology and signal strength at your location, then paint a coverage map from your own readings.**
> Supporting 2G, 3G, 4G (LTE), and 5G (NR/NSA), visualized on OpenStreetMap, fully on-device.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.10-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-26--37-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Version](https://img.shields.io/badge/Version-1.0.1-blue)](#summary)

---

## Summary

Sigverage is a privacy-first Android application that records cellular signal strength (dBm) and technology type along with GPS coordinates. It aggregates these readings into a high-granularity coverage overlay on top of OpenStreetMap.

**Privacy Posture**: Everything stays on your device. There are no accounts, no backends, no telemetry, and no remote sync. Your location and cellular data are stored in a local SQLite database and never leave the phone unless you explicitly export them.

---

## Key Functions

### 📡 Advanced Signal Sampling
- **Movement-Based Recording**: Uses the Activity Recognition Transition API to sample only when you are moving (walking, driving, etc.), significantly reducing battery drain when stationary.
- **Background Persistence**: A dedicated foreground service (`SamplingService`) keeps recording alive while the screen is off.
- **Reliability Management**: Integrated `WakeLockManager` ensures the CPU stays active during sampling bursts, with intelligent battery-level gating to preserve charge.
- **Smart Sampling**: Implements tile-based deduplication—recording only one reading per ~50m coverage cell per visit. Revisiting a cell adds a new data point to the average.
- **Schedules**: Define recurring windows (e.g., Weekday commute) to automatically start and stop recording using exact alarms.

### 🗺️ Coverage Visualization
- **Averaged Grid Overlay**: Readings are binned into Mercator tiles at zoom level 20. The grid displays the dominant network type (hue) and mean signal strength (opacity).
- **Secondary Network Indicators**: Each tile contains a 2x4 "corner grid" showing all technologies present in that cell, not just the strongest one.
- **Interactive Details**: Tap any coverage square to see precise coordinates, a breakdown of readings, dominant operators, and signal range (Best/Worst dBm).
- **Immersive Controls**: Full-screen map with floating FABs for recentering, manual capture, and zoom.

### 📋 Data & Filtering
- **Live Filters**: Instantly toggle network types (5G, LTE, etc.) or specific operators (carriers) on the map to see how coverage varies.
- **List View**: A chronological history of all readings with detailed signal metrics (RSRP, RSRQ, SNR for LTE).
- **CSV Management**: Robust `CsvManager` for importing and exporting your data. Move your coverage history between devices or analyze it in spreadsheet software.
- **Auto-Expiry**: Set a retention policy (e.g., 90 days) to automatically prune old data and keep the local database lean.

---

## Tech Stack

| Component | Technology |
| --- | --- |
| **Language** | Kotlin 2.1.10 |
| **UI Framework** | Jetpack Compose (Material 3) |
| **Maps** | [osmdroid](https://github.com/osmdroid/osmdroid) (OpenStreetMap) |
| **Database** | Room (SQLite) with KSP |
| **Location** | Android `LocationManager` with adaptive sampling |
| **Activity** | Google Play Services (Activity Recognition) |
| **Background** | Typed Foreground Service + WakeLock |
| **Architecture** | MVVM (Split Concern ViewModels) |

---

## Architecture Overview

Sigverage follows a modular MVVM architecture designed for reliability and efficiency:

- **MainViewModel**: Manages the core UI state for the map and readings list.
- **SettingsViewModel**: Handles application configuration, schedules, and data lifecycle (Import/Export/Purge).
- **SamplingService**: The background engine. Orchestrates `LocationTracker`, `CellularScanner`, and `WakeLockManager` to record data without user intervention.
- **SignalRepository**: The single source of truth for the local Room database.
- **CsvManager**: Dedicated logic for RFC-4180 compliant CSV processing with formula-injection protection.
- **SelectionDialog**: A unified, generic UI component for all single-selection settings.

---

## Project Layout

```
com.sigverage.app
├── MainActivity.kt        # Activity Host & Theme Orchestration
├── SigverageApp.kt        # Application Setup (osmdroid/Notification Channels)
├── data/
│   ├── CsvManager         # CSV I/O Logic
│   ├── PreferencesStore   # SharedPreferences Wrapper
│   └── SignalRepository   # Database Access Layer
├── service/
│   ├── SamplingService    # Foreground Recording Service
│   ├── WakeLockManager    # CPU & Battery Management
│   └── ScheduleManager    # AlarmManager Coordination
├── location/              # GPS Fix Streaming
├── cellular/              # Telephony Info Snapshots
├── coverage/              # Grid Rendering & Mercator Math
├── model/                 # Domain Entities (Reading, Schedule, Enums)
└── ui/
    ├── MainViewModel      # Map/List State
    ├── SettingsViewModel  # Config & Data State
    ├── MainScreen         # Primary Navigation & Scaffold
    ├── SettingsScreen     # Configuration Pages
    ├── OnboardingScreen   # Multi-step Permission Guide
    └── SelectionDialog    # Reusable Selection UI
```

---

## Building

1.  **JDK 21** and **Android Studio Meerkat** (2025.1+) or newer.
2.  **compileSdk 37** / **minSdk 26**.
3.  Open the project and sync Gradle.
4.  Run the `app` configuration on a physical device (recommended for cellular/GPS accuracy).

---

## License

Private / Unreleased. (c) 2026 Sigverage Authors.
