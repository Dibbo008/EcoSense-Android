# EcoSense

**A personal environment-health companion for Android.**
EcoSense tracks air quality and weather where you are, turns the numbers into clear decisions (when to go out, what is safe to do), and links them to how *you* feel through a symptom diary and chat.

![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)
![Language](https://img.shields.io/badge/language-Java-orange)
![minSdk](https://img.shields.io/badge/minSdk-24-blue)
![targetSdk](https://img.shields.io/badge/targetSdk-34-blue)
![Firebase](https://img.shields.io/badge/Firebase-Auth%20%7C%20Firestore%20%7C%20Realtime%20DB-FFCA28?logo=firebase&logoColor=black)

---

## 1. Overview

Most weather apps show conditions. EcoSense answers the next question: **"What should I do about it?"**

- It fetches **live weather and air quality** for your location (PM2.5, PM10, US AQI, temperature, humidity) and stores every reading offline.
- It **forecasts the next hours** and tells you the best and worst time to be outside, tuned to your health profile.
- It lets you **log symptoms** and discovers which air or weather conditions trigger them.
- It offers a short **symptom chat** that explains what may be happening, why, and what to do.
- It keeps working in the background and **reacts to the device**: low battery, airplane mode and connectivity changes.

> **Health disclaimer.** EcoSense gives general guidance based on public air-quality data and your own logs. It is not a medical device and does not diagnose. Seek professional care for serious or persistent symptoms.

---

## 2. Features

### Monitoring and alerts
- Real-time temperature, humidity, PM2.5, PM10 and US AQI with an animated AQI gauge.
- **Foreground monitoring service** with a configurable interval (default 15 min, minimum 5 min). Its notification text updates after every check, for example `AQI 82 · 29°C · updated 14:05`.
- **Custom alert limits** for temperature, humidity, PM2.5 and PM10 with push notifications when exceeded.
- **Spike detection**: warns about *sudden* rises (for example PM2.5 jumping 3x within about an hour) rather than only fixed limits. Notifications are rate-limited to once every 3 hours.
- Location name shown on the dashboard (reverse-geocoded), tap to open in Maps. Falls back to Khulna, Bangladesh when location is unavailable and says so.

### Today's Plan
- **Best time to go outside**: finds the best (and worst) 2-hour daytime window in the next 24 hours from the hourly forecast.
- **24-hour strip**: colour-coded bars per hour, best window outlined.
- **Activity advisor** with reasons: outdoor exercise, walking or cycling, opening windows, wearing a mask, drying laundry outside, kids and older adults outdoors, and heat and hydration.
- **Health profile**: Normal, Sensitive (asthma, allergy), Elderly, Child or Athlete. The profile scales the advice and can apply recommended alert limits in one tap.
- Works **offline** from the last cached forecast (valid for up to 12 hours).

### Symptom Link
- **10-second diary**: headache, cough, eye or throat irritation, breathing trouble, fatigue, poor sleep, sneezing or allergy, or "feeling fine". Each entry stores a snapshot of the air at that moment.
- **Personal pattern finder** that compares symptom days with high- and low-exposure days:
  - After **3 logged days**: *early signals* (clearly labelled low confidence).
  - After **7 days**: statistically tested patterns (risk ratio, Pearson correlation, Fisher exact test).
  - Warns you on days when today's conditions match your own trigger levels.
- **Doctor report**: export the diary as CSV (with air data) and share it.

### Symptom Chat (Firebase Realtime Database)
- A minimal guided chat: choose symptoms (multi-select), strength, duration and an optional note.
- EcoSense replies with four sections: **what may be happening**, **why**, **what you can do**, and **when to see a doctor**.
- Uses current air and weather, your health profile and your own diary patterns. Red-flag symptoms (for example chest pain or severe breathing trouble) trigger an urgent-care message.
- History is stored in **Firebase Realtime Database** and works offline; guests get a local in-memory chat.
- Rule-based and on-device. No external AI service is called.

### Data and history
- **Offline-first** storage in Room. Readings are never lost when the network drops.
- History in three views (ListView, card list, grid), with search and Safe/Unsafe filters.
- **Trend prediction**: custom linear regression with R², trend per hour, a confidence band, your safe-limit line and a 6-step forecast.
- **CSV export** to Downloads, with share.
- **Cloud sync** (Firestore) and restore for signed-in users.

### Device awareness (system broadcasts)
| System event | EcoSense response |
|---|---|
| Battery low | Background checks slow down (3x, minimum 30 min); banner and notification say "power saving" |
| Battery OK | Normal interval resumes |
| Airplane mode on | Polling pauses; the dashboard shows saved data |
| Connectivity lost / restored | Pauses when offline; on reconnect it refreshes immediately and syncs unsynced readings |

### Experience
- Sky-and-glass visual design with a floating bottom navigation bar, light and dark themes, and smooth screen transitions.
- Daily eco-tip, quick one-tap symptom logging from the dashboard, and a share-report action.

---

## 3. Screens

| Screen | Purpose |
|---|---|
| **Login** | Firebase email/password sign-in, sign-up, password reset, or continue as guest |
| **Dashboard** | Live conditions, AQI gauge, recent readings, Today's Plan card, Symptom Link card, monitoring switch |
| **Today's Plan** | Best and worst time, 24-hour strip, activity advice, personal triggers |
| **Symptom Link** | Log symptoms, patterns, today's triggers, recent entries, doctor report |
| **Symptom Chat** | Guided chat with advice |
| **History** | Search, filter, view modes, CSV export, cloud sync |
| **Trend Prediction** | Regression chart, forecast and insight |
| **Settings** | Alert limits, interval, health profile, notifications, dark mode, restore, logout |

> Add screenshots under `docs/screenshots/` and reference them here.

---

## 4. Tech stack

| Area | Technology |
|---|---|
| Language | Java 17 |
| Min / target / compile SDK | 24 / 34 / 34 |
| Build | Android Gradle Plugin 8.7.3, Gradle 8.9 |
| UI | Material Components 1.12 (Material 3), ConstraintLayout, RecyclerView, custom `View`s |
| Local data | Room 2.6.1 (schema v2 with a real migration), SharedPreferences |
| Networking | Retrofit 2.11, OkHttp 4.12, Gson |
| Cloud | Firebase Authentication, Cloud Firestore, Realtime Database (BoM 33.5.1) |
| Background | Foreground Service, dynamic and manifest BroadcastReceivers, ConnectivityManager callbacks |
| Data providers | [Open-Meteo](https://open-meteo.com) Forecast and Air-Quality APIs (no API key) |

---

## 5. Architecture

A pragmatic layered structure. Activities handle UI, `data` handles persistence and network, and `utils` holds framework-free logic so it stays testable.

```
EcoSense
└── app/src/main/java/com/ecosense/app
    ├── EcoSenseApp.java              Theme, notification channels, RTDB offline cache
    ├── data
    │   ├── local                     Room: EnvLog, SymptomEntry, DAOs, AppDatabase (+migration),
    │   │                             EnvLogProvider (ContentProvider), ForecastCache, SymptomRepository
    │   └── remote                    Retrofit API + models, EnvRepository, ForecastRepository,
    │                                 FirebaseHelper (Firestore), ChatRepository (Realtime DB)
    ├── service
    │   ├── MonitoringService.java    Foreground monitoring, reacts to system events
    │   └── AlertReceiver.java        Turns alert broadcasts into notifications
    ├── ui
    │   ├── auth                      LoginActivity
    │   ├── dashboard                 DashboardActivity, AqiGaugeView
    │   ├── plan                      PlanActivity
    │   ├── symptom                   SymptomActivity, ChatActivity, ChatAdapter
    │   ├── history                   HistoryActivity, LogListAdapter, LogRecyclerAdapter
    │   ├── prediction                PredictionActivity, TrendChartView
    │   └── settings                  SettingsActivity
    └── utils                         AdvisorEngine, CorrelationEngine, SymptomAdvisor,
                                      SpikeDetector, RegressionCalculator, SystemMonitor,
                                      AlertChecker, NotificationHelper, PermissionHelper,
                                      LocationHelper, CsvExporter, SymptomExporter,
                                      NavHelper, Prefs, LogFormatter, EcoTips, SymptomTypes
```

**Data flow**

```
Open-Meteo ──► EnvRepository ──► Room (EnvLog) ──► Dashboard / History / Prediction
                    │                  │
                    │                  ├──► AlertChecker + SpikeDetector ──► AlertReceiver ──► Notification
                    │                  └──► FirebaseHelper ──► Firestore (users/{uid}/logs)
                    │
Hourly forecast ──► ForecastRepository ──► ForecastCache ──► AdvisorEngine ──► Today's Plan

Symptom diary (Room) + readings ──► CorrelationEngine ──► patterns, today's triggers
Chat input ──► SymptomAdvisor ──► ChatRepository ──► Realtime DB (users/{uid}/chat)

System broadcasts ──► SystemMonitor ──► MonitoringService / Dashboard
```

Database work and network calls run on a shared background executor (`AppDatabase.IO`). UI updates are posted to the main thread.

---

## 6. Core algorithms

All implemented in plain Java, with no external math or ML libraries.

### Linear regression (`RegressionCalculator`)
Ordinary least squares over the latest readings (x = hours since the first reading). Provides slope, intercept, R², residual standard error and a trend label. The chart extends the line six steps ahead and draws a widening confidence band.

### Personal pattern finder (`CorrelationEngine`)
1. Group diary entries and readings by calendar day. A day counts only if the user logged something (a "Feeling fine" entry counts) and there are at least 2 readings.
2. For each symptom and each factor (PM2.5, PM10, temperature, humidity, AQI), split days at the factor's median into high and low.
3. Compare symptom rates between the two groups (risk ratio), compute Pearson correlation with severity, and run a one-sided Fisher exact test.
4. Keep only the best factor per symptom to limit false positives from testing many factors.
5. With 3 to 6 days of data it returns descriptive *early signals* instead of tested patterns.

> This finds correlation, not causation, and the app says so.

### Advisor (`AdvisorEngine`)
The AQI is multiplied by a profile factor (for example 1.4 for Sensitive). Rules then produce advice per activity. The best and worst 2-hour windows are found by scoring each forecast hour (profile-adjusted AQI plus a heat penalty) between 05:00 and 20:00.

### Spike detector (`SpikeDetector`)
Compares the newest reading with the average of readings from 20 to 120 minutes earlier. Thresholds are tighter for higher-risk profiles.

### Symptom advisor (`SymptomAdvisor`)
Maps selected symptoms, strength, duration, notes, current air and profile to a four-section reply. Detects red-flag keywords and severe breathing trouble and switches to an urgent-care message.

---

## 7. Android components

| Component | Where it is used |
|---|---|
| **Activities** (8) | Login, Dashboard, Plan, Symptom, Chat, History, Prediction, Settings |
| **Explicit Intents** | All navigation (bottom bar, cards, buttons) |
| **Implicit Intents** | Share report or CSV (`ACTION_SEND`), open location in Maps (`ACTION_VIEW`), open data-source website |
| **Foreground Service** | `MonitoringService` (type `dataSync`) with a live-updating notification |
| **Broadcast Receivers** | `AlertReceiver` (manifest); a dynamic receiver for new readings; system receivers in `SystemMonitor` (battery low/okay, airplane mode) |
| **Content Provider** | `EnvLogProvider` exposes readings at `content://com.ecosense.app.provider/logs` (read-only, guarded by `com.ecosense.app.permission.READ_LOGS`); the CSV exporter reads through it |
| **Notifications** | Alert and monitoring channels, `BigTextStyle`, deep link back to the dashboard |
| **Runtime permissions** | Location, notifications (Android 13+), storage (Android 9 and below) with a rationale dialog |
| **Lifecycle** | `onStart`/`onStop` for receiver and monitor registration, `onPause` for state saving, `onSaveInstanceState` for UI state |
| **Lists** | `RecyclerView` (`ListAdapter` + `DiffUtil`, `LinearLayoutManager` and `GridLayoutManager`, two view types), `ListView` with `ArrayAdapter` and a ViewHolder |
| **Layouts** | ConstraintLayout, RelativeLayout, LinearLayout, ScrollView |
| **Networking** | Retrofit + Gson (JSON) over HTTPS |

---

## 8. Getting started

### Prerequisites
- Android Studio **Ladybug (2024.2.2)** or newer
- JDK 17 or newer (the bundled JBR works)
- Android SDK 34
- A Firebase project (see [Firebase setup](#9-firebase-setup))

### Build and run
1. Clone or extract the project and open the `EcoSense` folder in Android Studio.
2. Let Gradle sync. If prompted about the Gradle wrapper, accept the download (Gradle 8.9).
3. Add your own `app/google-services.json` (next section).
4. Run on a device or emulator (API 24+).

> The repository contains a **placeholder** `app/google-services.json` so the project compiles. Sign-in, Firestore sync and cloud chat will not work until you replace it with your own file. Guest mode works without Firebase.

### Using the app
1. Choose **Log in** or **Continue offline (guest)**.
2. Allow location and notifications when asked. Without location the app uses Khulna as the default and says so.
3. Tap refresh on the dashboard, or switch on **Background monitoring**.
4. Set your **health profile** and alert limits in Settings.

---

## 9. Firebase setup

1. Create a project in the [Firebase Console](https://console.firebase.google.com) and add an Android app with package name **`com.ecosense.app`**.
2. **Authentication**: enable *Email/Password*.
3. **Cloud Firestore**: create a database.
4. **Realtime Database**: create a database.
5. **Download `google-services.json` after creating the databases**, then place it in `app/`. Downloading it earlier omits the Realtime Database URL and the chat falls back to local mode.

### Security rules

**Firestore**
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{uid}/logs/{doc} {
      allow read, write: if request.auth != null && request.auth.uid == uid;
    }
  }
}
```

**Realtime Database**
```json
{
  "rules": {
    "users": {
      "$uid": {
        ".read": "auth.uid === $uid",
        ".write": "auth.uid === $uid"
      }
    }
  }
}
```

### Data layout
| Store | Path | Content |
|---|---|---|
| Firestore | `users/{uid}/logs/{timestamp}` | One environment reading per document |
| Realtime Database | `users/{uid}/chat/{pushId}` | `role`, `text`, `ts` |

---

## 10. Permissions

| Permission | Why |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Fetch data and detect connectivity |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | Local air quality and weather |
| `POST_NOTIFICATIONS` | Alerts and the monitoring notification (Android 13+) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` | Background monitoring |
| `WRITE_EXTERNAL_STORAGE` (max SDK 28) | CSV export on Android 9 and below only |
| `com.ecosense.app.permission.READ_LOGS` | Custom permission guarding the content provider |

Every runtime permission is optional. The app degrades gracefully if one is denied.

---

## 11. Demo guide

A suggested order for a live demonstration:

1. **Login**: sign in with email/password, or guest mode.
2. **Dashboard**: refresh and show the AQI gauge, location chip and glass "Recent readings" card.
3. **Today's Plan**: best time to go outside, the 24-hour strip, and the advice list. Change the health profile in Settings and show the advice changing.
4. **Monitoring service**: switch on background monitoring and pull down the notification tray to show the live text.
5. **System broadcasts**
   - Toggle **airplane mode**: banner appears, polling pauses, saved data is shown.
   - Turn Wi-Fi off and on: it reconnects, refreshes and syncs.
   - Emulator: *Extended controls → Battery*, set the level to 15% or less with charging off: "power saving" appears.
6. **Alerts**: lower an alert limit in Settings and refresh to trigger a push notification.
7. **History**: switch ListView, cards and grid; search; filter; export CSV and share it.
8. **Prediction**: open the regression chart and explain R², the trend and the limit line.
9. **Symptom Link**: log a few symptoms (quick chips on the dashboard) and show progress toward unlocking patterns.
10. **Symptom Chat**: run a guided check and show the structured advice. In the Firebase console, show the message under `users/{uid}/chat` in real time.
11. **Cloud**: sync from History and show the documents in Firestore.
12. **Content Provider**: explain that the CSV exporter reads data through `EnvLogProvider`.

---

## 12. Data and privacy

- All readings and symptom entries are stored **on the device** (Room).
- Signed-in users can sync readings to **Firestore** and keep chat history in **Realtime Database**, both scoped to their own user ID by the security rules above. Guests keep everything local.
- Location is used only to request weather and air quality and to show a place name. Only the last known fix is read; the app never tracks movement.
- The reverse-geocoded place name is stored locally for display.
- Weather data comes from Open-Meteo; coordinates are sent to that service with each request.
- The symptom diary is **not** uploaded unless you export it yourself.

---

## 13. Known limitations

- **Location** uses the device's *last known* fix, not an active GPS request. If none exists the default location is used.
- **Symptom patterns** need data: early signals at 3 logged days, tested patterns at 7. Days with fewer than 2 air readings are ignored, so keep monitoring on.
- **Pattern results are correlations**, not proof of cause.
- **Symptom chat is rule-based**. It does not understand free text beyond red-flag keywords and cannot diagnose.
- **Open-Meteo** air-quality values are model-based estimates for a grid cell, not a measurement at your exact spot.
- **Accessibility and localisation**: UI text is currently in English and mostly hard-coded in layouts rather than `strings.xml`.
- **Not implemented**: View Binding (the project uses `findViewById`), XML parsing (JSON only), reading device contacts or media through system content providers, multi-city comparison, home-screen widget.
- The app has not been exhaustively tested across all devices and Android versions.

---

## 14. Roadmap

- Morning briefing notification
- Multi-city comparison
- Home-screen widget
- Migrate to View Binding and move strings to resources (Bengali translation)
- Emergency-contact picker through the Contacts content provider
- Unit and instrumentation tests for the logic classes in `utils`

---

## 15. Credits and licence

- Weather and air-quality data: **[Open-Meteo](https://open-meteo.com)** (CC BY 4.0, free for non-commercial use).
- UI toolkit: Google **Material Components**. Cloud services: **Firebase**.
- Icons: Material Design icons (Apache 2.0). The EcoSense logo was created for this project.

**Author:** *Your name, student ID, course*
**Licence:** *Add a licence of your choice (for example MIT) before publishing.*
