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
- **Pull-to-refresh** — swipe down to fetch the latest data from Health Connect

### Activities
- Full list of runs, hikes, walks, cycles from the past 90 days
- **Data source badge** — each activity card shows its source app (Garmin Connect, Withings, Google Fit, Samsung Health, etc.) from HC metadata
- **Source filter chips** — when activities come from multiple apps, a second row of chips lets you filter by source
- **Offline indicator** — amber banner shown when the device has no internet ("Offline · Garmin/Withings may not have synced recently"); disappears automatically on reconnect
- **Duplicate detection** — activities recorded simultaneously by different apps are flagged with a ⚠ Dup badge; overlap > 50% of the shorter session's duration triggers the flag
- **Data completeness bar** — thin 3-color bar at the bottom of each card (teal ≥ 80%, amber ≥ 50%, coral < 50%) based on presence of HR, distance, calories, elevation, and laps (max 100 pts)
- Per-activity detail screen with:
  - **3D Mapbox map** with GPS route and terrain elevation (route loaded on-demand from Health Connect)
  - **Elevation profile chart** — with X/Y axis labels and touch scrubbing (altitude + distance tooltip)
  - **Pace chart** — with X/Y axis labels and touch scrubbing (pace tooltip)
  - **Heart rate chart** — with X/Y axis labels and touch scrubbing (BPM tooltip)
  - Lap splits table
  - **Race predictions** via the Riegel formula (`T2 = T1 × (D2/D1)^1.06`) for 1 mi, 5K, 10K, half marathon, and full marathon

### Sleep Analytics
- 90-day sleep sessions pulled from Health Connect — supports both Garmin Connect and Withings simultaneously
- **Source toggle** — flip between Garmin / Withings / Combined view; all analytics recalculate per source
- **Correct stage mapping** — Health Connect integer constants properly decoded: 1=Awake, 2=Sleeping (unspecified), 3=Out of Bed, 4=Light, 5=Deep, 6=REM; Garmin single-stage `SLEEPING_UNSPECIFIED` sessions count toward total sleep
- **Source-aware HR/HRV/SpO2** — biometric samples matched to the session's originating device first; cross-device fallback only when same-source data is absent
- **Date navigation** — browse any night with ← / → arrows; chart highlights selected night automatically
- **6-component sleep score** — transparent weighted composite (0–100) with dynamic weight redistribution when components are unavailable:
  - Duration (35% nominal) — how close to your target sleep time
  - Efficiency (25%) — time asleep / time in bed, full credit at ≥ 90%
  - Continuity (15%, requires ≥ 40% stage coverage) — penalizes WASO, long latency, and frequent awakenings
  - Consistency (10%, requires ≥ 7-night history) — standard deviation of bedtimes; 120 min variation = 0 points
  - Stages (10%, requires ≥ 80% stage coverage) — deep + REM % vs 20% target each
  - Recovery (5%, requires ≥ 5 HR samples) — nightly HR vs your baseline; lower = better
- **Score hero card** — large score + rating (Excellent / Good / Fair / Low / Poor) with tap-to-expand component breakdown showing per-component score, progress bar, and effective weight
- **Rolling score chart** — Canvas bar chart with 7D / 14D / 30D / 3M / 6M / 1Y range selector; score-zone color bands; touch scrubbing shows date and score
- **Hypnogram** — vertical-depth stage lanes (Awake → REM → Light → Deep) with proportional time axis and touch scrubbing showing stage name, time range, duration, and avg HR
- **Metrics grid** — 8-cell summary card: Total Sleep, Deep, REM, Efficiency (row 1); Awake (includes Out-of-Bed), WASO, Latency, Awakenings (row 2)
- **Sleep overview card** — last-night total sleep, time in bed, efficiency, latency, WASO, awakenings, bedtime–wake window, sleep midpoint
- **Interactive stage timeline** — Canvas-rendered color bands (Deep/Light/REM/Awake/Unspecified) with touch scrubbing; HR line overlay when data available
- **Stage detail table** — per-stage duration, %, episode count, longest episode, and delta vs your historical averages
- **Efficiency, latency, WASO, and midpoint** — computed from stage boundary data; latency supports a manual correction offset; Awake and WASO include Out-of-Bed stages
- **Bedtime consistency** — avg, earliest, latest, std-dev; weekday vs weekend split; social jet-lag indicator
- **Wake-time consistency** — same breakdown as bedtime
- **Sleep debt** — last-night deficit and 7-day cumulative debt vs your personal sleep need
- **Personalized sleep need** — auto-detected as the 75th-percentile of your unrestricted nights; overridable in preferences
- **Nap detection** — sessions < 3 h starting between 8 am–8 pm shown in a separate nap section, excluded from main analytics
- **Overnight HR chart** — Canvas line chart with touch scrubbing; avg and lowest HR + time-of-lowest callouts
- **Overnight HRV chart** — ms trend with avg and range
- **Overnight respiration** — shown when data is available (requires a future HC SDK update; currently displays "not available" placeholder)
- **Overnight SpO2 chart** — %-saturation trend with 95% threshold line; avg, min, and minutes-below-threshold callouts; wellness disclaimer
- **Sleep factor tags** — tag a night with Alcohol, Caffeine, Late Meal, Evening Workout, Medication, Stress, Illness, Screen Time, Travel, Meditation, or Reading
- **Sleep environment notes** — free-text notes, room temperature, noise level (Silent/Low/Medium/High), light level (Dark/Dim/Light), travel toggle; auto-saved with 300 ms debounce
- **Correlation view** — for each tag with ≥ 3 nights tagged, shows avg sleep duration and score with vs without the factor
- **Smart recommendations** — rule-based cards flagging high WASO, long latency, inconsistent bedtimes, social jet-lag, low deep %, and high accumulated debt
- **Bedtime recommendation** — calculated from desired wake time, sleep need, and avg latency
- **Sleep prediction** — tonight's predicted duration and score based on 7-night rolling median
- **Weekly reports** — last 4 ISO weeks: avg duration, avg score, avg debt, consistency, best/worst night
- **Monthly summary** — avg duration, weekday vs weekend split, longest/shortest night
- **Per-session detail screen** — tap any night to open a full-detail view with all of the above, navigable with the back button

### Heart Rate
- HR zone distribution (Zone 1–5 based on % of max HR)
- **Resting HR trend chart** — uses dedicated `RestingHeartRateRecord` from Garmin/Withings (not estimated from activity samples); X/Y axis labels + touch scrubbing
- **HRV trend chart** — X/Y axis labels + touch scrubbing (ms tooltip with date)
- Max HR configurable in Settings

### Body Metrics _(new)_
- Weight trend chart (90 days from Withings via Health Connect) — X/Y axis labels + touch scrubbing
- Body fat % trend chart — X/Y axis labels + touch scrubbing
- 90-day change indicators
- Measurement history table

### Settings
- **Health Connect** — tap "Grant Permissions" (uses OS permission dialog on Android 14+, HC app dialog on Android 13-); **per-permission breakdown** (14 permissions, collapsible list with display name + purpose + granted/denied indicator); **Sync Now** reports what changed ("3 new activities", "Up to date", etc.)
- **Background Sync & Diagnostics** — toggle automatic HC polling every 15 min (WorkManager); shows incremental change token status, cached activity count, last manual and background sync times; uses HC change tokens to skip full 6-call fetch when nothing changed
- **Data History** — choose how far back to load from HC: 1 Day / 1 Week / 1 Month / 3 Months / 6 Months / 1 Year (default 3 months); changing this automatically re-fetches with the new range
- **Historical Import** — one-tap import of up to 5 years of workout history from Health Connect in 90-day chunks with live progress display (current period + activity count); last import date persisted in DataStore
- **Time-Zone Handling** — activity dates and times are displayed in the time zone where the activity was recorded (from HC `ExerciseSessionRecord.startZoneOffset`); "Today"/"Yesterday" labels also evaluate in the recording zone so travel runs display correctly
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
| GPS route points | Health Connect `ExerciseSessionRecord.exerciseRouteResult` |
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
| Overnight SpO2 | Health Connect `OxygenSaturationRecord` ← Garmin / Withings |

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
| Charts | Custom Canvas with touch scrubbing and axis labels (elevation, pace, HR, resting HR, HRV, weight, body fat, sleep stage timeline, overnight HR/HRV/SpO2/respiration) |
| Networking | Retrofit + OkHttp + Moshi |
| Storage | Room DB v1 (`runconnect_db`) + DataStore Preferences (`runconnect_prefs`, 18 keys) + WorkManager (background sync) |
| Race predictions | Riegel formula |
| Insights | Rule-based engine (sleep, training load, recovery, consistency) |
| Build | GitHub Actions (Gradle 8.12, AGP 8.9.1, compileSdk 36, lifecycle-process 2.8.6) |

---

## Update Safety

Every app update is a **non-destructive in-place upgrade**. Nothing is ever deleted on update:

| Preserved across updates | Mechanism |
|---|---|
| Unit preference (mi/km, lbs/kg) | DataStore — key string never changes |
| Health Connect permissions | HC SDK preserves them on update |
| HC change token (incremental sync state) | Room `sync_state` table (DataStore fallback for first upgrade) |
| Sync timestamps | DataStore |
| Sleep target, annotations, tags | DataStore |
| Activity + sleep data | Room DB (upsert — never truncate) |
| Sleep chart range preference | DataStore `sleep_chart_range` key |
| First-install version tracking | DataStore `app_first_install_version_code` |

**Rules enforced in code:**
- `fallbackToDestructiveMigration()` is **never used** — future DB version bumps require an explicit `Migration` object
- DataStore keys are append-only; existing key strings never renamed or removed
- Room DB excluded from backup (large, device-specific); DataStore prefs included

### Room Database (v1)

File: `runconnect_db` · Schema exported to `app/schemas/`

| Table | Purpose |
|---|---|
| `activities` | Exercise session scalars + laps JSON |
| `activity_hr_samples` | Per-activity heart-rate samples (FK → activities, CASCADE) |
| `route_points` | GPS route points (FK → activities, CASCADE) |
| `sleep_sessions` | Sleep session boundaries |
| `sleep_stages` | Sleep stage segments (FK → sleep_sessions, CASCADE) |
| `sync_state` | Per-data-type HC change token + sync timestamps |

### DataStore (`runconnect_prefs`, 18 keys)

Settings schema version: `1`

Keys added in v1.1.0: `app_first_install_version_code`, `app_last_launched_version_code`, `app_settings_schema_version`, `onboarding_complete`, `sleep_chart_range`

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
| `RELEASE_KEYSTORE_PATH` | Path to keystore file for signed release APK |
| `RELEASE_KEYSTORE_PASSWORD` | Keystore password |
| `RELEASE_KEY_ALIAS` | Key alias inside the keystore |
| `RELEASE_KEY_PASSWORD` | Key password |

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
| READ_OXYGEN_SATURATION | Overnight SpO2 in sleep analytics |
