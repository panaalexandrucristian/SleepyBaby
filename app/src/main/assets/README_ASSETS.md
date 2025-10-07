# Assets Required

Place the following files in this directory:

## Required Audio Files
- `shhh_loop.mp3` - Soothing white noise/shushing sound that loops seamlessly

## Optional AI Model Files (for on-device classification)
- `cry_cnn_int8.onnx` - ONNX quantized cry detection model (preferred)
- `cry_cnn_int8.tflite` - TensorFlow Lite quantized cry detection model (fallback)

## Model Specifications

### Input Tensor
- **Shape**: `[1, timeFrames, 64]` (e.g., `[1, 96, 64]` for ~1 second)
- **Type**: `float32`
- **Data**: Normalized mel-spectrogram features

### Output Tensor
- **Shape**: `[1, 3]`
- **Type**: `float32`
- **Classes**: `[silence_prob, noise_prob, baby_cry_prob]` (softmax probabilities)

### Audio Preprocessing
- 16kHz mono audio
- 1s windows with 50% overlap (hop size = 0.5s)
- STFT with Hann window (512 FFT size)
- 64 mel bins (80-8000 Hz frequency range)
- Log(1+x) scaling + per-frame normalization

## Fallback Behavior

If model files are missing, the app automatically switches to an energy-based heuristic classifier so detection stays 100% local.

## Custom Recording

The app can capture a 10 second "shh" loop from the Settings screen. The recording is stored under the app's private files directory and used automatically instead of `shhh_loop.mp3` when available.
