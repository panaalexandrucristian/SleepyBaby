# SleepyBaby – On‑device Cry Detection & Soothing

SleepyBaby is an Android app built with Jetpack Compose Material 3 that detects crying locally using a lightweight energy‑based approach and plays a soothing shush loop (or your own recording). Everything runs on‑device — no cloud audio processing, no account.

## Features

- **On‑device cry detection** using mel‑spectrogram energy — fast and private.
- **Automatic soothing**: play a gentle shush loop or your own recording when crying is detected.
- **One‑tap monitoring** with clear live status in a **foreground service** (persistent notification).
- **Calm controls**: smooth volume ramping and an in‑app brightness slider for night use.
- **Modern Material 3** expressive design with light/dark support and motion.
- **Privacy‑first**: audio stays on your phone; no account; limited diagnostics in release only (see Privacy).
- **Persistent settings** via DataStore for monitoring state, target volume, and display preferences.

## Getting Started

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Required runtime permissions:
1. **Microphone** – mandatory so the cry detector can listen.
2. **Notifications** (Android 13+) – needed to run the foreground service.

## Assets

Place the soothing loop in `app/src/main/assets/`:
```
app/src/main/assets/
└── shhh_loop.mp3    # default shush loop played after a cry is detected
```

## Usage Flow

1. Launch the app and grant microphone (and notification, if prompted) permissions.
2. Record your own shush in the **Active monitoring** section; monitoring stays disabled until a custom track is saved.
3. Adjust target volume and screen brightness to match your nursery.
4. Start monitoring — the foreground service keeps listening and plays the loop whenever crying is detected, then fades out when silence returns.

## Build Variants & Firebase

- `debug` build:
  - Analytics/Crashlytics/Performance collection disabled by default.
  - Safe for local development; no production telemetry.
- `release` build:
  - Analytics/Crashlytics/Performance enabled to collect limited, pseudonymous diagnostics that help reliability.
  - Requires a valid `app/google-services.json` tied to `ro.pana.sleepybaby`.

## Versioning

Semantic version is defined in `gradle.properties` and translated to Android’s integer `versionCode`:

- `VERSION_MAJOR`, `VERSION_MINOR`, `VERSION_PATCH` → `versionName = MAJOR.MINOR.PATCH`
- `versionCode = MAJOR*10000 + MINOR*100 + PATCH`

Example for 1.0.0:

```
VERSION_MAJOR=1
VERSION_MINOR=0
VERSION_PATCH=0
```

Increment `versionCode` implicitly by bumping any of the above.

## Architecture

- `CryDetectionEngine` – orchestrates audio capture, classification, and state automation.
- `MelSpecExtractor` – converts PCM into mel-spectrogram frames for the energy check.
- `EnergyCryClassifier` – the simple energy classifier shared by the UI and background service.
- `SleepyBabyService` – foreground service that hosts the detection engine and manages playback.
- `NoisePlayer` / `ShushRecorder` – handle playback of the loop and capture of the custom shush sample.

## Technical Notes

- **AudioRecord** at 16 kHz mono with 1-second windows and overlap.
- **Jetpack Compose Material 3** expressive theming and full‑screen layouts.
- **Media3 ExoPlayer** for seamless loop playback and previews.
- **Coroutines** with `StateFlow` to deliver reactive updates to the UI and notification layer.

## Testing

```bash
./gradlew test
```

## Release (Play Store)

1. Create an upload keystore (once):
   - `keytool -genkeypair -v -keystore ~/sleepybaby-upload.jks -alias sleepybaby-upload -keyalg RSA -keysize 2048 -validity 36500`
2. Add signing secrets (prefer `~/.gradle/gradle.properties`):
   - `sleepybabyReleaseStoreFile=/Users/<you>/sleepybaby-upload.jks`
   - `sleepybabyReleaseStorePassword=***`
   - `sleepybabyReleaseKeyAlias=sleepybaby-upload`
   - `sleepybabyReleaseKeyPassword=***`
3. Build bundle: `./gradlew clean :app:bundleRelease`
   - Output: `app/build/outputs/bundle/release/app-release.aab`
4. Enable Play App Signing and upload the `.aab` to your chosen track.

## Firebase & Privacy

- Uses Google Firebase in production builds only:
  - **Analytics**: usage events (e.g., monitoring start/stop, tutorial actions) to improve UX.
  - **Crashlytics**: crash and non‑fatal error reports to improve stability.
  - **Performance Monitoring**: traces/timings to diagnose slow paths.
- Audio never leaves the device; no account or personal profile is required.
- See the full policy: `docs/privacy_policy.md`.

## License

Personal project — use responsibly and adapt as needed.
