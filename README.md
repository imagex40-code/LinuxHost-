# LinuxHost

The ultimate Ubuntu host and manager for Android.

LinuxHost is a dedicated Android application designed to provide the best experience for running and managing Ubuntu through PRoot.

## Quick Start

1. **Download the APK** from the latest [GitHub Actions build](https://github.com/YOUR_USERNAME/LinuxHost/actions)
2. **Install** on your Android device
3. **Launch** LinuxHost and follow the setup wizard

## Development

### Prerequisites
- Android SDK (API 35)
- JDK 21
- Gradle 8.10+

### Build
```bash
./gradlew assembleDebug
```

APK will be at `app/build/outputs/apk/debug/app-debug.apk`

### CI
Every push to `main` triggers a GitHub Actions build. The debug APK is available as an artifact.

## Architecture

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **DI:** Koin
- **Database:** Room
- **Architecture:** MVVM with single-activity navigation

## License

MIT
