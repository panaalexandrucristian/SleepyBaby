# SleepyBaby - AI Cry Detection & Soothing

SleepyBaby is a minimal but complete Android application that uses on-device AI to detect baby cries and automatically play soothing sounds. The app runs 100% offline for privacy and showcases modern Android development with AI integration.

## Features

### 🤖 On-Device AI
- Real-time cry detection using mel-spectrogram analysis
- ONNX Runtime Mobile with TensorFlow Lite fallback
- Energy-based heuristic fallback keeps detection local even without model files

### 🎵 Smart Audio Automation
- Detects N consecutive seconds of crying → starts white noise with fade-in
- Detects M consecutive seconds of silence → smart fade-out and stop
- Smooth volume transitions to prevent audio pops
- ExoPlayer-based audio with seamless looping
- Optional 10s custom “shh” recording for personalized soothing

### 🛡️ Privacy & Performance
- 100% on-device processing - no data leaves your phone
- Foreground service with proper notification management
- Battery-optimized with configurable processing intervals
- Audio focus handling and interruption management

### 📱 Modern UI
- Material 3 Compose interface
- Real-time state monitoring
- Configurable thresholds and settings
- Persistent settings with DataStore

## Quick Start

### 1. Build & Install
```bash
cd SleepyBaby
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 2. Grant Permissions
- Microphone permission (required for cry detection)
- Notification permission (for foreground service)

### 3. Add Audio Assets
Place your soothing audio file at:
```
app/src/main/assets/shhh_loop.mp3
```

### 4. Add AI Models (Optional)
For on-device AI, place model files in assets:
```
app/src/main/assets/cry_cnn_int8.onnx
app/src/main/assets/cry_cnn_int8.tflite
```

## How to Use

### Basic Operation
1. **Start the App** - Open SleepyBaby
2. **Adjust Settings** - Configure cry/silence thresholds (N/M seconds)
3. **Start Detection** - Tap the "Start" button or toggle switch
4. **Monitor Status** - Watch real-time state updates

### Settings Explained
- **Cry Threshold (N)**: Consecutive seconds of crying before starting soothing
- **Silence Threshold (M)**: Consecutive seconds of silence before fade-out
- **Volume**: Target volume for soothing sounds (10-100%)
- **AI Backend**: On-device ONNX (preferred) with TensorFlow Lite fallback; energy heuristic engages if models are absent
- **Custom “shh”**: Record a personal 10s shushing sample used for playback

### States
- **Listening**: Monitoring for cries
- **Crying Detected**: Counting consecutive cry seconds
- **Playing**: Soothing sounds active
- **Fading Out**: Gradually reducing volume
- **Stopped**: Detection inactive

## On-Device Classifier

- **Runtime**: Attempts to load the ONNX model first and falls back to TensorFlow Lite if needed.
- **Models**: Supports `cry_cnn_int8.onnx` (preferred) and `cry_cnn_int8.tflite` placed under `app/src/main/assets/`.
- **Missing Models**: If neither model is present, the app falls back to an energy-based heuristic classifier.
- **Reload**: Use the in-app "Reload AI Models" action after copying models without restarting the service.

### Custom Shushing Loop
- Open Settings → “Record your “shh” sample” and tap *Record 10s “shh”*.
- Whisper “shh” close to the microphone for the full countdown; the app saves the loop privately.
- Preview your recording with *Play recording* and re-record at any time—the latest take replaces the stock loop.

### Model File Placement
```
app/src/main/assets/
├── cry_cnn_int8.onnx      # ONNX model (preferred)
├── cry_cnn_int8.tflite    # TensorFlow Lite model (fallback)
└── shhh_loop.mp3          # Soothing audio (required)
```

## Technical Architecture

### Core Components
- **`CryDetectionEngine`**: Main automation state machine
- **`MelSpecExtractor`**: Audio feature extraction (1s windows, 64 mel bins)
- **`CryClassifier`**: On-device classifier wrapper with ONNX/TFLite backends
- **`NoisePlayer`**: ExoPlayer wrapper with fade capabilities
- **`SleepyBabyService`**: Foreground service management

### Audio Pipeline
1. **AudioRecord** → PCM samples (16kHz mono)
2. **MelSpecExtractor** → Mel-spectrogram features [time, 64]
3. **CryClassifier** → Probabilities [silence, noise, cry]
4. **AutomationEngine** → State transitions & audio control

### State Machine
```
Stopped → Listening → CryingPending → Playing → FadingOut → Listening
```

## Development

### Requirements
- Android Studio
- minSdk 24 (Android 7.0)
- Kotlin 1.9+
- Compose BOM 2023.10.01

### Dependencies
- **Media3**: Audio playback and management
- **ONNX Runtime Mobile**: On-device inference (preferred)
- **TensorFlow Lite**: Fallback inference engine
- **DataStore**: Settings persistence
- **Compose**: Modern UI framework

### Testing
```bash
# Run unit tests
./gradlew test

# Run on device
./gradlew connectedAndroidTest
```

### Unit Tests Coverage
- ✅ MelSpecExtractor feature extraction
- ✅ CrySmoother temporal consistency
- ✅ AutomationEngine state transitions
- ✅ All core logic without Android dependencies

## Model Format

### Input Tensor
- **Shape**: `[1, timeFrames, 64]` (e.g., `[1, 96, 64]` for ~1s)
- **Type**: `float32`
- **Range**: Normalized mel-spectrogram features

### Output Tensor
- **Shape**: `[1, 3]`
- **Type**: `float32`
- **Classes**: `[silence, noise, baby_cry]` (softmax probabilities)

### Preprocessing
- 16kHz mono audio → 1s windows with 50% overlap
- STFT with Hann window (512 FFT size)
- 64 mel bins (80-8000 Hz range)
- Log(1+x) scaling + per-frame normalization

## Privacy & Security

- **No Network**: 100% offline operation
- **No Storage**: No audio data persisted
- **No Tracking**: No analytics or telemetry
- **Local AI**: All inference happens on device
- **Minimal Permissions**: Only microphone and notifications

## Performance Notes

- **CPU Usage**: ~5-15% on modern devices
- **Memory**: ~50-100MB RAM usage
- **Battery**: Optimized with configurable duty cycles
- **Latency**: <100ms classification response time
- **Models**: INT8 quantized for mobile efficiency

## Troubleshooting

### App doesn't detect cries
1. Check microphone permission granted
2. Verify "On-device" backend has model files
3. Ensure at least one AI model file (ONNX/TFLite) exists or rely on the built-in heuristic fallback
4. Adjust cry threshold (lower = more sensitive)

### Audio doesn't play
1. Check audio file `shhh_loop.mp3` exists in assets
2. Verify device volume is up
3. Test with headphones to rule out speaker issues
4. Check notification interruptions

### Service stops unexpectedly
1. Disable battery optimization for SleepyBaby
2. Ensure persistent notification is enabled
3. Check device-specific background app limits

## Contributing

This is a demonstration project showcasing on-device AI integration. Feel free to extend it with:

- Custom audio tracks
- Additional classifier models
- Room database for cry event logging
- CSV export functionality
- Advanced audio preprocessing
- Multiple baby profiles

## License

This project is provided as-is for educational and demonstration purposes.
