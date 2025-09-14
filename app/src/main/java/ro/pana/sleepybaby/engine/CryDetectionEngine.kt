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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ro.pana.sleepybaby.audio.NoisePlayer
import ro.pana.sleepybaby.core.ai.ClassificationResult
import ro.pana.sleepybaby.core.ai.CryClassifier
import ro.pana.sleepybaby.core.ai.EnergyCryClassifierMel
import ro.pana.sleepybaby.core.ai.MelSpecExtractor
import kotlin.math.log10
import kotlin.math.roundToInt

private const val PLAYBACK_LOOP_COUNT = 3
private const val POST_PLAYBACK_PAUSE_MS = 1500L
private const val TAG = "CryDetectionEngine"

// Anti-liniște + fallback pe energie de bandă (500–5000 Hz)
private const val MIN_ENERGY_FOR_TRIGGER = 0.32f
private const val BAND_ENERGY_TRIGGER    = 0.70f  // dacă banda e ≥ 0.70 două cadre, pornește
private const val TRIGGER_CONFIRM_FRAMES = 1      // 1 sau 2

class CryDetectionEngine(
    private val context: Context,
    initialConfig: AutomationConfig = AutomationConfig()
) {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<AutomationState>(AutomationState.Stopped)
    val state: StateFlow<AutomationState> = _state.asStateFlow()

    // Variant A: absolute log-mel
    private val melExtractor = MelSpecExtractor(
        sampleRate = 16000,
        windowSizeMs = 1000,
        hopSizeMs = 500,
        melBins = 64,
        minFreq = 80f,
        maxFreq = 8000f,
        normalizePerFrame = false
    )

    private var classifier: CryClassifier = EnergyCryClassifierMel(
        melFmin = 80f,
        melFmax = 8000f,
        bandLowHz = 500,
        bandHighHz = 5000,
        dbFloor = 0.0f,
        dbCeil = 6.5f,
        emaAlpha = 0.985f,
        energyFactor = 2.0f,
        steadyDeltaGate = 0.04f,
        minConsecutiveWindows = 2,
        debug = false
    )
    private val classifierMutex = Mutex()

    private var noisePlayer: NoisePlayer? = null
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var processingJob: Job? = null

    @Volatile
    private var currentConfig: AutomationConfig = initialConfig

    private val bufferLock = Any()
    private var bufferFilled = false
    private var bufferPosition = 0

    private var cooldownSamples = 0
    private var playbackJob: Job? = null

    // energy guards (0..1)
    private var lastWindowEnergy01 = 0f      // global average energy (all mels)
    private var lastBandEnergy01   = 0f      // band (500–5000 Hz) average
    private var triggerConfirmCount = 0

    fun start() {
        if (_state.value != AutomationState.Stopped) return
        try {
            cooldownSamples = 0
            triggerConfirmCount = 0
            resetAudioBuffer()
            startAudioCapture()
            _state.value = AutomationState.Listening
            Log.d(TAG, "Engine started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start engine", e)
            stop()
        }
    }

    fun stop() {
        recordingJob?.cancel()
        processingJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        noisePlayer?.stop()
        playbackJob?.cancel()
        playbackJob = null
        cooldownSamples = 0
        triggerConfirmCount = 0
        resetAudioBuffer()
        _state.value = AutomationState.Stopped
        Log.d(TAG, "Engine stopped")
    }

    fun updateConfig(newConfig: AutomationConfig) {
        currentConfig = newConfig
        noisePlayer?.setVolume(newConfig.targetVolume)
        Log.d(TAG, "Config updated: $newConfig")
    }

    private fun startAudioCapture() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
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

        // AEC / NS dacă există
        try { android.media.audiofx.AcousticEchoCanceler.create(audioRecord!!.audioSessionId)?.enabled = true } catch (_: Throwable) {}
        try { android.media.audiofx.NoiseSuppressor.create(audioRecord!!.audioSessionId)?.enabled = true } catch (_: Throwable) {}

        audioRecord?.startRecording()

        val audioBuffer = ShortArray(sampleRate) // 1s ring buffer

        recordingJob = coroutineScope.launch {
            val readBuffer = ShortArray(1024)
            while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val samplesRead = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: 0
                if (samplesRead > 0) {
                    synchronized(bufferLock) {
                        for (i in 0 until samplesRead) {
                            audioBuffer[bufferPosition] = readBuffer[i]
                            bufferPosition = (bufferPosition + 1) % audioBuffer.size
                            if (!bufferFilled && bufferPosition == 0) bufferFilled = true
                        }
                    }
                }
                delay(10)
            }
        }

        processingJob = coroutineScope.launch {
            while (isActive) {
                try {
                    val snapshot = synchronized(bufferLock) { if (!bufferFilled) null else buildSnapshot(audioBuffer) }
                    if (snapshot != null) {
                        val features = melExtractor.extract(snapshot)
                        if (features.isNotEmpty()) {
                            // energy guards
                            computeEnergies(features)

                            val result = classifierMutex.withLock { classifier.classify(features) }

                            Log.d(TAG, "Classifier => ${result.predictedClass} cry=${"%.2f".format(result.cryProb)} noise=${"%.2f".format(result.noiseProb)} E=${"%.2f".format(lastWindowEnergy01)} B=${"%.2f".format(lastBandEnergy01)}")

                            processClassification(result)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Processing error: ${e.message}")
                }
                delay(currentConfig.samplePeriodMs)
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

    // calculează media log-mel globală (0..1) și media pe bandă (500–5000 Hz) (0..1)
    private fun computeEnergies(features: Array<FloatArray>) {
        // global
        var s = 0f; var n = 0
        for (fr in features) for (v in fr) { s += v; n++ }
        val avgDb = if (n > 0) s / n else 0f
        lastWindowEnergy01 = (avgDb / 6.5f).coerceIn(0f, 1f)

        // band 500–5000 Hz
        val nBins = features[0].size
        val lo = hzToMelBin(500f, 80f, 8000f, nBins)
        val hi = hzToMelBin(5000f, 80f, 8000f, nBins).coerceAtLeast(lo)
        var sb = 0f; var nb = 0
        for (fr in features) {
            for (i in lo..hi) { sb += fr[i]; nb++ }
        }
        val bandDb = if (nb > 0) sb / nb else 0f
        lastBandEnergy01 = (bandDb / 6.5f).coerceIn(0f, 1f)
    }

    private fun hzToMel(hz: Float) = 2595f * log10(1f + hz / 700f)
    private fun hzToMelBin(hz: Float, fmin: Float, fmax: Float, nBins: Int): Int {
        val mMin = hzToMel(fmin); val mMax = hzToMel(fmax)
        val r = ((hzToMel(hz) - mMin) / (mMax - mMin)).coerceIn(0f, 1f)
        return (r * (nBins - 1)).roundToInt()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun processClassification(result: ClassificationResult) {
        if (_state.value == AutomationState.Stopped) return

        // nu clasificăm/declanșăm în timpul playback-ului
        if (playbackJob?.isActive == true || _state.value is AutomationState.Playing) return

        // cooldown post-playback
        if (cooldownSamples > 0) {
            cooldownSamples--
            if (cooldownSamples <= 0) {
                triggerConfirmCount = 0
                Log.i(TAG, "Cooldown completed; monitoring resumed")
            }
            return
        }

        // anti-liniște
        if (lastWindowEnergy01 < MIN_ENERGY_FOR_TRIGGER) {
            triggerConfirmCount = 0
            return
        }

        // acceptăm CRY/NOISE, SAU fallback pe energie de bandă (două cadre)
        val nonSilence = result.predictedClass != ClassificationResult.CryClass.SILENCE
        val bandHot = lastBandEnergy01 >= BAND_ENERGY_TRIGGER

        val hit = nonSilence || bandHot

        if (hit) {
            triggerConfirmCount++
            Log.d(TAG, "Trigger conf $triggerConfirmCount/$TRIGGER_CONFIRM_FRAMES (cls=$nonSilence bandHot=$bandHot)")
            if (triggerConfirmCount >= TRIGGER_CONFIRM_FRAMES) {
                triggerConfirmCount = 0
                beginPlaybackCycle()
            }
        } else {
            if (triggerConfirmCount > 0) Log.d(TAG, "Trigger reset")
            triggerConfirmCount = 0
        }
    }

    private fun beginPlaybackCycle() {
        if (playbackJob?.isActive == true) {
            Log.v(TAG, "Playback already in progress; ignoring trigger")
            return
        }
        playbackJob = coroutineScope.launch {
            val config = currentConfig
            try {
                val player = ensureNoisePlayer()
                _state.value = AutomationState.Playing
                Log.i(TAG, "Starting shush playback loops ($PLAYBACK_LOOP_COUNT) volume=${"%.2f".format(config.targetVolume)}")
                player.playLoops(
                    trackUri = config.trackId,
                    loopCount = PLAYBACK_LOOP_COUNT,
                    targetVolume = config.targetVolume,
                    fadeInMs = config.fadeInMs,
                    fadeOutMs = config.fadeOutMs
                )
                Log.i(TAG, "Shush playback completed")
            } catch (e: Exception) {
                Log.e(TAG, "Playback cycle failed", e)
            } finally {
                noisePlayer?.stop()
                enterPostPlaybackPause()
            }
        }
    }

    private fun enterPostPlaybackPause() {
        val config = currentConfig
        _state.value = AutomationState.Listening
        triggerConfirmCount = 0
        cooldownSamples = calculateSampleBudget(config.samplePeriodMs, POST_PLAYBACK_PAUSE_MS)
        resetAudioBuffer()
        // resetăm classifier-ul ca baseline-ul să revină la normal pentru o nouă rundă
        coroutineScope.launch {
            try { recreateClassifier() } catch (e: Exception) { Log.w(TAG, "Classifier reset failed: ${e.message}") }
        }
        Log.i(TAG, "Playback finished; pausing detection for ${POST_PLAYBACK_PAUSE_MS}ms (~$cooldownSamples samples)")
    }

    private suspend fun recreateClassifier() {
        classifierMutex.withLock {
            try { classifier.release() } catch (_: Throwable) {}
            classifier = EnergyCryClassifierMel(
                melFmin = 80f,
                melFmax = 8000f,
                bandLowHz = 500,
                bandHighHz = 5000,
                dbFloor = 0.0f,
                dbCeil = 6.5f,
                emaAlpha = 0.985f,
                energyFactor = 2.0f,
                steadyDeltaGate = 0.04f,
                minConsecutiveWindows = 2,
                debug = false
            )
        }
    }

    private fun resetAudioBuffer() {
        synchronized(bufferLock) {
            bufferPosition = 0
            bufferFilled = false
        }
    }

    private fun calculateSampleBudget(samplePeriodMs: Long, durationMs: Long): Int {
        if (samplePeriodMs <= 0) return 1
        val budget = ((durationMs + samplePeriodMs - 1) / samplePeriodMs).coerceAtLeast(1)
        return budget.toInt()
    }

    private suspend fun ensureNoisePlayer(): NoisePlayer {
        return noisePlayer ?: withContext(Dispatchers.Main) {
            noisePlayer ?: NoisePlayer(context).also { noisePlayer = it }
        }
    }

    fun release() {
        stop()
        if (classifierMutex.tryLock()) {
            try { classifier.release() } finally { classifierMutex.unlock() }
        }
        noisePlayer?.release()
        coroutineScope.cancel()
    }
}
