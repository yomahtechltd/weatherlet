# Weatherlet 🌦️

An Android weather-alert app and widgets that watch the next 5 days and warn you about snow, extreme cold, heavy rain, strong wind, heat waves and more, early enough to prepare.

**Website:** https://yomahtechltd.github.io/weatherlet/ · **Download:** [latest release](https://github.com/yomahtechltd/weatherlet/releases/latest)

## Features
- One daily alert, plus instant updates when the forecast gets worse (or clears)
- Profiles (driver, cyclist, gardener, pets, outdoor work, allergies/asthma, migraines) and a sensitivity setting
- Official warnings: UK Met Office, US NWS, European MeteoAlarm
- Home-screen widgets (5-day outlook, next alert, hourly), a Quick Settings tile and Android 16 Live Update chip
- Commute alerts, rain-soon nudge, quiet hours, prepare checklists, best-day planner
- Honest offline behaviour: stale forecasts are labelled by age, and too-old ones are never presented as fact
- No accounts, analytics or ads. Data stays on the device.

## Build
Kotlin, Jetpack Compose (Material 3), Jetpack Glance, WorkManager. Min SDK 31, target SDK 36, JDK 17.

```
./gradlew testDebugUnitTest      # unit tests
./gradlew assembleDebug          # debug APK
```

For a signed release build create `keystore.properties` (git-ignored) next to `settings.gradle.kts`:

```
storeFile=your-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

then `./gradlew assembleRelease`. Add `-PappIdSuffix=.test` to install a build beside the real app.

## Data and credits
Forecasts and air quality by [Open-Meteo.com](https://open-meteo.com/) (CC BY 4.0). Open-Meteo's free tier is for non-commercial use; commercial use (including ads or subscriptions) needs their paid plan. Official warnings from the UK Met Office, US National Weather Service and MeteoAlarm; El Niño data from NOAA CPC.

Weatherlet is an information tool, not a safety service. Always follow official guidance.

## Licence
[MIT](LICENSE) © 2026 Yomah Tech Ltd. Contact: yomahtechuk@outlook.com
