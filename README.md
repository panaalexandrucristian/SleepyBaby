# SleepyBaby – Cry Detection & Soothing

SleepyBaby is an Android app built with Jetpack Compose that detects crying locally using a lightweight energy-based approach and plays a soothing shush loop. Everything runs offline—no external ML models or cloud services are required.

## Features

- **Local detection** that analyses mel-spectrogram energy to decide when to react.
- **Foreground service** that keeps automation alive with a persistent notification.
- **Personal shush recording**: capture a 10s sample, preview it, and keep monitoring gated until a custom loop exists.
- **Material 3 UI** with a blue & white palette, immersive full-screen mode, and an in-app brightness slider for night use.
- **Persistent settings** via DataStore for cry/silence thresholds, target volume, and automation state.

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
3. Adjust cry/silence thresholds, target volume, and screen brightness to match your nursery.
4. Start monitoring—the foreground service keeps listening and plays the loop whenever crying is detected, then fades out when silence returns.

## Architecture

- `CryDetectionEngine` – orchestrates audio capture, classification, and state automation.
- `MelSpecExtractor` – converts PCM into mel-spectrogram frames for the energy check.
- `EnergyCryClassifier` – the simple energy classifier shared by the UI and background service.
- `SleepyBabyService` – foreground service that hosts the detection engine and manages playback.
- `NoisePlayer` / `ShushRecorder` – handle playback of the loop and capture of the custom shush sample.

## Technical Notes

- **AudioRecord** at 16 kHz mono with 1-second windows and overlap.
- **Jetpack Compose Material 3** theming, tuned for blue/white branding and full-screen layouts.
- **Media3 ExoPlayer** for seamless loop playback and previews.
- **Coroutines** with `StateFlow` to deliver reactive updates to the UI and notification layer.

## Testing

```bash
./gradlew test
```

## License

Personal project — use responsibly and adapt as needed.
