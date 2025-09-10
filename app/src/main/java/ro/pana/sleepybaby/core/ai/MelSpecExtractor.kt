package ro.pana.sleepybaby.core.ai

import kotlin.math.*

/**
 * Extracts mel-scale spectrogram features from audio PCM data.
 * Variant A: keeps absolute log-mel energy (NO per-frame z-score).
 */
class MelSpecExtractor(
    private val sampleRate: Int = 16000,
    private val windowSizeMs: Int = 1000,
    private val hopSizeMs: Int = 500,
    private val melBins: Int = 64,
    private val minFreq: Float = 80f,
    private val maxFreq: Float = 8000f,
    private val normalizePerFrame: Boolean = false // keep false for EnergyCryClassifierMel
) {
    private val windowSize = (sampleRate * windowSizeMs / 1000).coerceAtLeast(1)
    private val hopSize = (sampleRate * hopSizeMs / 1000).coerceAtLeast(1)
    private val fftSize = nextPowerOfTwo(windowSize)
    private val melFilterBank = createMelFilterBank()
    private val window = createHannWindow(windowSize)

    /** @return Float array [timeFrames, melBins] */
    fun extract(audioData: ShortArray): Array<FloatArray> {
        val frames = mutableListOf<FloatArray>()
        var pos = 0
        while (pos + windowSize <= audioData.size) {
            val frame = audioData.sliceArray(pos until pos + windowSize)
            val melFrame = processFrame(frame)
            frames.add(melFrame)
            pos += hopSize
        }
        return frames.toTypedArray()
    }

    private fun processFrame(frame: ShortArray): FloatArray {
        val windowed = FloatArray(fftSize)
        val limit = minOf(frame.size, windowSize)
        for (i in 0 until limit) {
            windowed[i] = frame[i].toFloat() / 32768f * window[i]
        }
        val spectrum = computeMagnitudeSpectrum(windowed)

        val melSpec = FloatArray(melBins)
        for (i in 0 until melBins) {
            var energy = 0f
            val filter = melFilterBank[i]
            for (j in spectrum.indices) {
                energy += spectrum[j] * filter[j]
            }
            // absolute log-like energy; positive, stable
            melSpec[i] = ln(1f + energy)
        }

        if (normalizePerFrame) { // disabled for Variant A
            val mean = melSpec.average().toFloat()
            var varAcc = 0f
            for (v in melSpec) varAcc += (v - mean) * (v - mean)
            val std = sqrt((varAcc / melSpec.size).coerceAtLeast(1e-12f))
            for (i in melSpec.indices) melSpec[i] = (melSpec[i] - mean) / std
        }
        return melSpec
    }

    private fun computeMagnitudeSpectrum(data: FloatArray): FloatArray {
        val n = data.size
        val real = data.copyOf()
        val imag = FloatArray(n)
        fft(real, imag)
        val spectrum = FloatArray(n / 2 + 1)
        for (k in spectrum.indices) {
            spectrum[k] = sqrt(real[k] * real[k] + imag[k] * imag[k])
        }
        return spectrum
    }

    private fun createMelFilterBank(): Array<FloatArray> {
        val filters = Array(melBins) { FloatArray(fftSize / 2 + 1) }
        val melMin = hzToMel(minFreq)
        val melMax = hzToMel(maxFreq)
        val melPoints = FloatArray(melBins + 2) { i -> melMin + (melMax - melMin) * i / (melBins + 1) }
        val freqPoints = melPoints.map { melToHz(it) }
        val binPoints = freqPoints.map { it * fftSize / sampleRate }

        for (i in 1..melBins) {
            val left = binPoints[i - 1].toInt()
            val center = binPoints[i].toInt()
            val right = binPoints[i + 1].toInt()
            val leftWidth = (center - left).coerceAtLeast(1)
            val rightWidth = (right - center).coerceAtLeast(1)
            val filt = filters[i - 1]
            for (j in left..right) {
                if (j !in 0 until filt.size) continue
                filt[j] = when {
                    j < center -> (j - left).toFloat() / leftWidth
                    j == center -> 1f
                    else -> (right - j).toFloat() / rightWidth
                }.coerceAtLeast(0f)
            }
        }
        return filters
    }

    private fun createHannWindow(size: Int): FloatArray = FloatArray(size) { i ->
        if (size == 1) 1f else 0.5f * (1f - cos(2f * Math.PI.toFloat() * i / (size - 1)))
    }

    // In-place radix-2 FFT
    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val halfLen = len / 2
            val angle = (-2.0 * Math.PI / len).toFloat()
            val wStepReal = cos(angle)
            val wStepImag = sin(angle)
            for (i in 0 until n step len) {
                var wReal = 1f
                var wImag = 0f
                for (k in 0 until halfLen) {
                    val i1 = i + k
                    val i2 = i1 + halfLen
                    val tReal = wReal * real[i2] - wImag * imag[i2]
                    val tImag = wReal * imag[i2] + wImag * real[i2]
                    real[i2] = real[i1] - tReal
                    imag[i2] = imag[i1] - tImag
                    real[i1] += tReal
                    imag[i1] += tImag
                    val nextWReal = wReal * wStepReal - wImag * wStepImag
                    val nextWImag = wReal * wStepImag + wImag * wStepReal
                    wReal = nextWReal
                    wImag = nextWImag
                }
            }
            len *= 2
        }
    }

    private fun nextPowerOfTwo(value: Int): Int {
        var v = value.coerceAtLeast(1)
        v--
        v = v or (v shr 1)
        v = v or (v shr 2)
        v = v or (v shr 4)
        v = v or (v shr 8)
        v = v or (v shr 16)
        return v + 1
    }

    private fun hzToMel(hz: Float): Float = 2595f * log10(1f + hz / 700f)
    private fun melToHz(mel: Float): Float = 700f * (10f.pow(mel / 2595f) - 1f)
}
