# RunConnect

A personal Android fitness analytics app that pulls data from Garmin Connect and Withings via Health Connect, with a dark UI inspired by Withings HealthMate.

## Install

Download the latest APK from the [Releases](https://github.com/beastzone/RunConnect/releases) tab and sideload it on your Android phone.

> Settings → Security → Install unknown apps → allow from Files/Browser

---

## Features

### Home Dashboard
- Weekly summary: distance, activity count, time, day streak
- 5 most recent activities with type, distance, duration, pace, heart rate
- Pulls from Health Connect — works with any app that syncs there (Garmin Connect, Withings, etc.)

### Activities
- Full list of runs, hikes, walks, cycles from the past 90 days
- Per-activity detail screen with:
  - **3D Mapbox map** with terrain elevation (requires Mapbox public token in Settings)
  - Elevation profile chart
  - Pace chart
  - Heart rate chart
  - Lap splits table
  - **Race predictions** via the Riegel formula (`T2 = T1 × (D2/D1)^1.06`) for 1 mi, 5K, 10K, half marathon, and full marathon, benchmarked against your personal bests

### Sleep Analytics
- Sleep sessions from the last 30 days pulled from Health Connect
- Per-night breakdown: Deep / Light / REM / Awake time
- Color-coded stage timeline bar
- Averages across all recorded nights

### Heart Rate
- HR zone distribution (Zone 1–5 based on % of max HR)
- Trends and resting heart rate over time
- Max HR configurable in Settings (default 190 bpm)

### Settings
- **Health Connect** — tap "Grant Permissions" to authorize data access (required)
- **Units** — toggle miles/km, lbs/kg
- **Max heart rate** — used to compute HR zone boundaries
- **Mapbox token** — free public token from account.mapbox.com, required for 3D route maps
- **Garmin Connect API** (optional) — enter Consumer Key + Secret from developer.garmin.com for deeper Garmin data; most data works without this via Health Connect

---

## First-Time Setup

1. Install the APK from Releases
2. Open the app → **Settings tab**
3. Tap **"Grant Permissions"** under Health Connect and approve all data types
4. In the **Garmin Connect** app: Settings → Health Connect → enable sync, do a manual sync
5. In the **Withings** app: Health Connect sync should be on by default
6. Return to the Home tab — activities load automatically

---

## Data Sources

| Data Type | Source |
|---|---|
| Exercise sessions (runs, hikes, etc.) | Health Connect ← Garmin Connect / Withings |
| Heart rate samples | Health Connect |
| Sleep sessions + stages | Health Connect ← Garmin Connect / Withings |
| Speed / pace | Health Connect |
| Distance | Health Connect |
| Elevation gain | Health Connect |
| GPS route | Health Connect (route stubbed — follow-up) |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material3 |
| Architecture | MVVM + StateFlow |
| DI | Hilt |
| Health data | Health Connect SDK 1.1.0-rc01 |
| Maps | Mapbox Maps SDK v11 (3D terrain) |
| Charts | Custom Canvas (elevation, pace, HR) |
| Networking | Retrofit + OkHttp + Moshi |
| Storage | DataStore Preferences |
| Race predictions | Riegel formula |
| Build | GitHub Actions (Gradle 8.12, AGP 8.9.1, compileSdk 36) |

---

## Building

No local Android Studio required. Every push to `main` triggers a GitHub Actions build that produces a signed debug APK and publishes it as a GitHub Release.

**Required GitHub Secrets** (Settings → Secrets → Actions):

| Secret | Description |
|---|---|
| `MAPBOX_DOWNLOADS_TOKEN` | Mapbox secret token (`sk.…`) for downloading the SDK |
| `MAPBOX_ACCESS_TOKEN` | Mapbox public token (`pk.…`) baked into the APK |
| `GARMIN_CONSUMER_KEY` | Optional — Garmin OAuth consumer key |
| `GARMIN_CONSUMER_SECRET` | Optional — Garmin OAuth consumer secret |

---

## Planned / In Progress

- [ ] GPS route display on 3D map (stubbed — Health Connect 1.1.0 `readExerciseRoute()` API)
- [x] Health Connect permission grant button (fixed `PermissionController.createRequestPermissionResultContract()`)
- [ ] Withings body composition (weight, body fat) via Health Connect
- [ ] VO2 max trend chart
- [ ] Training load / chronic load calculations
- [ ] Widget for home screen
