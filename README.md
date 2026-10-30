# EcoSense (Java, Android Studio Ladybug 2024.2.2)

## Open & run
1. File > Open > select the `EcoSense` folder, let Gradle sync (AGP 8.7.3, Gradle 8.9, JDK 17+).
   If Studio asks for a Gradle wrapper, accept the download or run `gradle wrapper --gradle-version 8.9`.
2. **Firebase (required for sign-in / cloud sync):**
   - Create a Firebase project, add an Android app with package `com.ecosense.app`.
   - Enable Authentication > Email/Password and create a Firestore database.
   - Replace `app/google-services.json` (currently a PLACEHOLDER so the project compiles).
   - Suggested Firestore rule:
     `match /users/{uid}/logs/{doc} { allow read, write: if request.auth.uid == uid; }`
3. Run on a device/emulator (API 24+). "Continue offline (guest)" works without Firebase.

## Data source
Open-Meteo (no API key): weather + air quality. Falls back to Khulna coordinates if location is denied.

## Where each requirement lives
- Lifecycle / Intents / clicks: `ui/dashboard/DashboardActivity`, `ui/auth/LoginActivity`
- ListView + ArrayAdapter + getView: `ui/history/LogListAdapter`
- RecyclerView + ViewHolder + Linear/Grid managers + DiffUtil: `ui/history/LogRecyclerAdapter`, `HistoryActivity`
- Service / Receiver / Provider / Notifications: `service/*`, `data/local/EnvLogProvider`, `utils/NotificationHelper`
- Retrofit: `data/remote/*` | Firebase Auth + Firestore: `ui/auth`, `data/remote/FirebaseHelper`
- Room: `data/local/*` | CSV export (reads via the ContentProvider): `utils/CsvExporter`
- Linear regression: `utils/RegressionCalculator`, chart: `ui/prediction/TrendChartView`
