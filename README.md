# Voice2 Android App

This is the Android companion app for the Voice2 project. It is built using modern Android technologies:

- **Jetpack Compose** for UI
- **Hilt** for Dependency Injection
- **Retrofit & Moshi** for API communication
- **DataStore** for local preferences
- **Navigation Compose** for app flow

## Structure

- `app/src/main/java/com/voice2/app/ui`: UI components and screens
- `app/src/main/java/com/voice2/app/data`: Data layer (API, Preferences, Repository)
- `app/src/main/java/com/voice2/app/di`: Dependency Injection modules

## Configuration

The default backend URL is set in `app/build.gradle.kts` to `https://192.168.2.244:4712`.

## Scaffolding

This project was scaffolded to match the architecture of `app_droid` and `app_droid_inv4`.
