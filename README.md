# RunConnect

A personal Android fitness analytics app that pulls data from Garmin Connect and Withings via Health Connect, with a dark UI inspired by Withings HealthMate.

## Install

Download the latest APK from the [Releases](https://github.com/beastzone/RunConnect/releases) tab and sideload it on your Android phone.

> Settings → Security → Install unknown apps → allow from Files/Browser

---

## Features

### Home Dashboard
- **Health Score** — composite score (0–100) broken down into Sleep, Activity, and Recovery rings
- **Today's metrics** — steps, active calories, last night's sleep, resting HR, HRV (when available from Withings/Garmin)
- **AI-style insights** — rule-based coaching cards: training load warnings (10% rule), sleep duration/quality, recovery alerts (elevated resting HR, low HRV), consistency streaks
- Weekly summary: distance, activity count, time, day streak
- 5 most recent activities with type, distance, duration, pace, heart rate

### Activities
- Full list of runs, hikes, walks, cycles from the past 90 days
- Per-activity detail screen with:
  - **3D Mapbox map** with GPS route and terrain elevation (route loaded on-demand from Health Connect)
  - Elevation profile chart
  - Pace chart
  - Heart rate chart
  - Lap splits table
  - **Race predictions** via the Riegel formula (`T2 = T1 × (D2/D1)^1.06`) for 1 mi, 5K, 10K, half marathon, and full marathon

### Sleep Analytics
- Sleep sessions from the last 30 days pulled from Health Connect
- Per-night breakdown: Deep / Light / REM / Awake time
- Color-coded stage timeline bar
- 30-day averages: total sleep, deep sleep, REM, sleep efficiency

### Heart Rate
- HR zone distribution (Zone 1–5 based on % of max HR)
- **Resting HR trend chart** — uses dedicated `RestingHeartRateRecord` from Garmin/Withings (not estimated from activity samples)
- Max HR configurable in Settings

### Body Metrics _(new)_
- Weight trend chart (90 days from Withings via Health Connect)
- Body fat % trend chart
- 90-day change indicators
- Measurement history table

### Settings
- **Health Connect** — tap "Grant Permissions" (uses OS permission dialog on Android 14+, HC app dialog on Android 13-); shows SDK status and granted/required count; "Open Health Connect App" fallback button
- **Units** — toggle miles/km, lbs/kg
- **Max heart rate** — used to compute HR zone boundaries
- **Garmin Connect API** (optional) — Consumer Key + Secret for deeper Garmin data
- **Mapbox token** — already baked into the APK from the GitHub build secret; no manual entry needed

---

## First-Time Setup

1. Install the APK from Releases
2. Open the app → **Settings tab**
3. Tap **"Grant Permissions"** under Health Connect and approve all data types
4. In the **Garmin Connect** app: Settings → Health Connect → enable sync, do a manual sync
5. In the **Withings** app: Health Connect sync should be on by default
6. Return to the Home tab — activities, health score, and insights load automatically

---

## Data Sources

| Data Type | Source |
|---|---|
| Exercise sessions (runs, hikes, etc.) | Health Connect ← Garmin Connect / Withings |
| GPS route points | Health Connect `readExerciseRoute()` |
| Heart rate samples | Health Connect |
| Resting heart rate | Health Connect `RestingHeartRateRecord` |
| HRV (RMSSD) | Health Connect `HeartRateVariabilityRmssdRecord` |
| Sleep sessions + stages | Health Connect ← Garmin Connect / Withings |
| Steps | Health Connect `StepsRecord` |
| Speed / pace | Health Connect |
| Distance | Health Connect |
| Elevation gain | Health Connect |
| Weight | Health Connect `WeightRecord` ← Withings |
| Body fat % | Health Connect `BodyFatRecord` ← Withings |

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
| Charts | Custom Canvas (elevation, pace, HR, resting HR trend, weight trend) |
| Networking | Retrofit + OkHttp + Moshi |
| Storage | DataStore Preferences |
| Race predictions | Riegel formula |
| Insights | Rule-based engine (sleep, training load, recovery, consistency) |
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

## Permissions Requested (Health Connect)

| Permission | Used For |
|---|---|
| READ_EXERCISE | Activity sessions |
| READ_EXERCISE_ROUTE | GPS route on activity detail map |
| READ_HEART_RATE | HR charts and zone analysis |
| READ_RESTING_HEART_RATE | Resting HR trend, recovery score |
| READ_HEART_RATE_VARIABILITY_RMSSD | HRV trend, recovery insights |
| READ_SLEEP | Sleep analytics |
| READ_STEPS | Daily step count on dashboard |
| READ_DISTANCE | Activity distance |
| READ_SPEED | Pace charts |
| READ_ELEVATION_GAINED | Elevation profile |
| READ_ACTIVE_CALORIES_BURNED | Calorie tracking |
| READ_POWER | Power data for cycling |
| READ_VO2_MAX | VO2 max (display only) |
| READ_WEIGHT | Body weight trend (Withings) |
| READ_BODY_FAT | Body fat trend (Withings) |
