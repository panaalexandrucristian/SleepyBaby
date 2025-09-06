package ro.pana.sleepybaby.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ro.pana.sleepybaby.audio.NoisePlayer
import ro.pana.sleepybaby.core.ai.ClassificationResult
import ro.pana.sleepybaby.core.ai.CryClassifier
import ro.pana.sleepybaby.core.ai.EnergyCryClassifier
import ro.pana.sleepybaby.core.ai.MelSpecExtractor
import ro.pana.sleepybaby.core.ai.OnDeviceCryClassifier

/**
 * Main cry detection automation engine that coordinates audio capture,
 * classification, and automated response
 */
class CryDetectionEngine(
    private val context: Context,
    initialConfig: AutomationConfig = AutomationConfig()
) {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<AutomationState>(AutomationState.Stopped)
    val state: StateFlow<AutomationState> = _state.asStateFlow()

    private val melExtractor = MelSpecExtractor()
    private var classifier: CryClassifier = EnergyCryClassifier()
    private var noisePlayer: NoisePlayer? = null
    private val classifierMutex = Mutex()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var processingJob: Job? = null

    private var consecutiveCryCount = 0
    private var consecutiveSilenceCount = 0
    private var lastClassification: ClassificationResult.CryClass = ClassificationResult.CryClass.SILENCE
    @Volatile
    private var currentConfig: AutomationConfig = initialConfig
    private val bufferLock = Any()
    private var bufferFilled = false
    private var bufferPosition = 0

    /**
     * Load the on-device classifier. Returns true if a backend was initialized.
     */
    suspend fun loadOnDeviceClassifier(): OnDeviceCryClassifier.Backend {
        return classifierMutex.withLock {
            val candidate = OnDeviceCryClassifier()
            val initialized = candidate.initialize()
            if (!initialized) {
                candidate.release()
                Log.e("CryDetectionEngine", "Failed to initialize on-device classifier")
                return@withLock OnDeviceCryClassifier.Backend.UNINITIALIZED
            }

            val backend = candidate.currentBackend()
            classifier.release()
            classifier = candidate
            backend
        }
    }

    /**
     * Start the cry detection engine
     */
    fun start() {
        if (_state.value != AutomationState.Stopped) return

        try {
            bufferFilled = false
            bufferPosition = 0
            startAudioCapture()
            _state.value = AutomationState.Listening
            consecutiveCryCount = 0
            consecutiveSilenceCount = 0

            Log.d("CryDetectionEngine", "Engine started")
        } catch (e: Exception) {
            Log.e("CryDetectionEngine", "Failed to start engine", e)
            stop()
        }
    }

    /**
     * Stop the cry detection engine
     */
    fun stop() {
        recordingJob?.cancel()
        processingJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        noisePlayer?.stop()

        _state.value = AutomationState.Stopped
        Log.d("CryDetectionEngine", "Engine stopped")
    }

    /**
     * Update automation configuration
     */
    fun updateConfig(newConfig: AutomationConfig) {
        currentConfig = newConfig
        noisePlayer?.setVolume(newConfig.targetVolume)
        Log.d("CryDetectionEngine", "Config updated: $newConfig")
    }

    private fun startAudioCapture() {
        // Check permission before creating AudioRecord
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("CryDetectionEngine", "RECORD_AUDIO permission not granted")
            throw SecurityException("RECORD_AUDIO permission required")
        }

        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize * 2
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("AudioRecord initialization failed")
        }

        audioRecord?.startRecording()

        // Audio capture coroutine
        val audioBuffer = ShortArray(sampleRate) // 1 second buffer

        recordingJob = coroutineScope.launch {
            val readBuffer = ShortArray(1024)

            while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val samplesRead = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: 0
                if (samplesRead > 0) {
                    // Copy to circular buffer
                    synchronized(bufferLock) {
                        for (i in 0 until samplesRead) {
                            audioBuffer[bufferPosition] = readBuffer[i]
                            bufferPosition = (bufferPosition + 1) % audioBuffer.size
                            if (!bufferFilled && bufferPosition == 0) {
                                bufferFilled = true
                            }
                        }
                    }
                }
                delay(10) // Small delay to prevent busy waiting
            }
        }

        // Processing coroutine
        processingJob = coroutineScope.launch {
            while (isActive) {
                try {
                    val snapshot = synchronized(bufferLock) {
                        if (!bufferFilled) null else buildSnapshot(audioBuffer)
                    }

                    if (snapshot != null) {
                        // Extract features from current buffer
                        val features = melExtractor.extract(snapshot)

                        if (features.isNotEmpty()) {
                            // Classify
                            val result = classifierMutex.withLock {
                                classifier.classify(features)
                            }

                            // Update state machine
                            processClassification(result.predictedClass)
                        }
                    }

                } catch (e: Exception) {
                    Log.w("CryDetectionEngine", "Processing error: ${e.message}")
                }

                val configSnapshot = currentConfig
                delay(configSnapshot.samplePeriodMs)
            }
        }
    }

    private fun buildSnapshot(buffer: ShortArray): ShortArray {
        val snapshot = ShortArray(buffer.size)
        val head = bufferPosition
        val tailLength = buffer.size - head

        System.arraycopy(buffer, head, snapshot, 0, tailLength)
        System.arraycopy(buffer, 0, snapshot, tailLength, head)

        return snapshot
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal suspend fun processClassification(classification: ClassificationResult.CryClass) {
        val currentState = _state.value

        when (currentState) {
            is AutomationState.Listening -> {
                when (classification) {
                    ClassificationResult.CryClass.BABY_CRY -> {
                        consecutiveCryCount++
                        consecutiveSilenceCount = 0

                        val config = currentConfig
                        if (consecutiveCryCount >= config.cryThresholdSeconds) {
                            startPlayback()
                        } else {
                            _state.value = AutomationState.CryingPending(consecutiveCryCount)
                        }
                    }
                    else -> {
                        consecutiveCryCount = 0
                        _state.value = AutomationState.Listening
                    }
                }
            }

            is AutomationState.CryingPending -> {
                when (classification) {
                    ClassificationResult.CryClass.BABY_CRY -> {
                        consecutiveCryCount++
                        val config = currentConfig
                        if (consecutiveCryCount >= config.cryThresholdSeconds) {
                            startPlayback()
                        } else {
                            _state.value = AutomationState.CryingPending(consecutiveCryCount)
                        }
                    }
                    else -> {
                        consecutiveCryCount = 0
                        _state.value = AutomationState.Listening
                    }
                }
            }

            is AutomationState.Playing -> {
                when (classification) {
                    ClassificationResult.CryClass.BABY_CRY -> {
                        consecutiveSilenceCount = 0
                        consecutiveCryCount++
                    }
                    else -> {
                        consecutiveCryCount = 0
                        consecutiveSilenceCount++

                        val config = currentConfig
                        if (consecutiveSilenceCount >= config.silenceThresholdSeconds) {
                            startFadeOut()
                        }
                    }
                }
            }

            is AutomationState.FadingOut -> {
                // Let fade complete naturally
            }

            is AutomationState.Stopped -> {
                // Engine is stopped, ignore
            }
        }

        lastClassification = classification
    }

    private suspend fun startPlayback() {
        val config = currentConfig
        try {
            val player = ensureNoisePlayer()

            player.play(
                trackUri = config.trackId,
                targetVolume = config.targetVolume,
                fadeInMs = config.fadeInMs
            )

            _state.value = AutomationState.Playing
            consecutiveSilenceCount = 0

            Log.d("CryDetectionEngine", "Started playback")
        } catch (e: Exception) {
            Log.e("CryDetectionEngine", "Failed to start playback", e)
            _state.value = AutomationState.Listening
        }
    }

    private suspend fun startFadeOut() {
        val config = currentConfig
        _state.value = AutomationState.FadingOut(config.fadeOutMs)

        try {
            ensureNoisePlayer().fadeOut(config.fadeOutMs)
            var remaining = config.fadeOutMs
            val updateInterval = 100L

            while (remaining > 0 && coroutineScope.isActive) {
                val step = updateInterval.coerceAtMost(remaining)
                delay(step)
                remaining -= step
                _state.value = AutomationState.FadingOut(remaining)
            }

            noisePlayer?.stop()
            _state.value = AutomationState.Listening
            consecutiveCryCount = 0
            consecutiveSilenceCount = 0

            Log.d("CryDetectionEngine", "Fade out completed")
        } catch (e: Exception) {
            Log.e("CryDetectionEngine", "Fade out failed", e)
            _state.value = AutomationState.Listening
        }
    }

    private suspend fun ensureNoisePlayer(): NoisePlayer {
        return noisePlayer ?: withContext(Dispatchers.Main) {
            noisePlayer ?: NoisePlayer(context).also { created ->
                noisePlayer = created
            }
        }
    }

    /**
     * Release resources
     */
    fun release() {
        stop()
        if (classifierMutex.tryLock()) {
            try {
                classifier.release()
            } finally {
                classifierMutex.unlock()
            }
        }
        noisePlayer?.release()
        coroutineScope.cancel()
    }
}
