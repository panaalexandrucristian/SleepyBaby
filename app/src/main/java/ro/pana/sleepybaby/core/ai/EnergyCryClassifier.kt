package ro.pana.sleepybaby.core.ai

import android.util.Log
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Mel-aware heuristic cry detector (Variant A: absolute log-mel).
 * - Warm-up + adaptive threshold (clamped)
 * - Steady-background adaptation (slow)
 * - "Sustained": după un rise/margin clar, păstrăm CRY dacă rămâne peste prag
 */
class EnergyCryClassifierMel(
    private val sampleRate: Int = 16_000,
    private val melFmin: Float = 80f,
    private val melFmax: Float = 8000f,
    private val bandLowHz: Int = 500,
    private val bandHighHz: Int = 5000,

    // map log-mel -> [0..1]
    private val dbFloor: Float = 0.0f,
    private val dbCeil: Float = 6.5f,

    // adaptivity & stability
    private val emaAlpha: Float = 0.985f,      // baseline relax under threshold
    private val energyFactor: Float = 2.0f,    // ↓ mai permisiv (era 2.2)
    private val steadyDeltaGate: Float = 0.04f,
    private val minConsecutiveWindows: Int = 2,
    private val debug: Boolean = false,

    // warm-up & clamps
    private val warmupWindows: Int = 4,
    private val steadyAdaptAlpha: Float = 0.08f,
    private val maxThreshold: Float = 0.90f,   // ↓ plafon prag (era 0.95)

    // CRY helpers (relaxate)
    private val cryRiseGate: Float = 0.10f,    // ↓ era 0.14/0.18
    private val marginGate: Float = 1.02f,     // ↓ era 1.06/1.12
    private val noiseDeltaMin: Float = 0.05f   // micșorat ușor
) : CryClassifier {

    private var noiseEma = 0.10f
    private var lastAvgE: Float? = null
    private var prevWindowStrongRise = false
    private var consecutive = 0

    private var warmupCount = 0
    private var warmupMin = 1f
    private var warmupMax = 0f
    private var warmupSum = 0f

    override suspend fun initialize(): Boolean = true

    override suspend fun classify(features: Array<FloatArray>): ClassificationResult {
        if (features.isEmpty() || features[0].isEmpty()) return silence()

        val nBins = features[0].size
        val lo = hzToMelBin(bandLowHz.toFloat(), nBins)
        val hi = max(lo, hzToMelBin(bandHighHz.toFloat(), nBins))

        var sum = 0f; var count = 0
        for (frame in features) { sum += bandMean(frame, lo, hi); count++ }
        if (count == 0) return silence()

        val avgDb = sum / count
        val avgE = ((avgDb - dbFloor) / (dbCeil - dbFloor)).coerceIn(0f, 1f)
        val delta = lastAvgE?.let { abs(avgE - it) } ?: 0f

        // ---- warm-up ----
        if (warmupCount < warmupWindows) {
            warmupCount++
            warmupSum += avgE
            warmupMin = minOf(warmupMin, avgE)
            warmupMax = maxOf(warmupMax, avgE)
            val roughMean = (warmupSum / warmupCount).coerceIn(warmupMin, warmupMax)
            noiseEma = roughMean.coerceIn(0.02f, 0.60f)
            if (debug) {
                val thrWU = clampThr(noiseEma * energyFactor)
                Log.d("CryMel", "WARMUP[$warmupCount/$warmupWindows] avgE=%.3f base=%.3f thr=%.3f"
                    .format(avgE, noiseEma, thrWU))
            }
            lastAvgE = avgE
            prevWindowStrongRise = false
            return silence()
        }

        var thr = clampThr(noiseEma * energyFactor)

        // steady background: energy above thr but delta small → slowly raise baseline
        if (avgE > thr && delta < steadyDeltaGate) {
            noiseEma = lerp(noiseEma, avgE, steadyAdaptAlpha)
            thr = clampThr(noiseEma * energyFactor)
        }

        // adapt down only when clearly below threshold
        if (avgE < thr) {
            noiseEma = ema(noiseEma, avgE, emaAlpha)
        }

        val energyOk = avgE > thr
        val strongMargin = if (thr > 0f) (avgE / thr) >= marginGate else false
        val strongRise = delta >= cryRiseGate

        // sustained: dacă am avut rise/margin anterior, acceptăm dacă stăm peste prag (ușor)
        val sustained = energyOk && prevWindowStrongRise && avgE >= thr * 1.005f

        val windowCry = energyOk && (strongRise || strongMargin || sustained)
        consecutive = if (windowCry) consecutive + 1 else 0
        val isCry = consecutive >= minConsecutiveWindows

        val isNoise = energyOk && !isCry && delta in noiseDeltaMin..cryRiseGate

        if (debug) {
            Log.d(
                "CryMel",
                "avgE=%.3f thr=%.3f dE=%.3f ratio=%.2f cons=%d prevRise=%s -> %s".format(
                    avgE, thr, delta, if (thr>0f) avgE/thr else 0f, consecutive, prevWindowStrongRise,
                    when { isCry -> "CRY"; isNoise -> "NOISE"; else -> "SILENCE" }
                )
            )
        }

        lastAvgE = avgE
        prevWindowStrongRise = energyOk && (strongRise || strongMargin)

        return when {
            isCry   -> cry((0.55f * ((avgE / thr).coerceIn(0f, 3f) / 3f)) + 0.45f * delta)
            isNoise -> noise(0.25f + 0.5f * ((avgE / thr).coerceIn(0f, 3f) / 3f))
            else    -> silence()
        }
    }

    override fun release() { /* no-op */ }

    // helpers
    private fun clampThr(t: Float) = t.coerceIn(0.01f, maxThreshold)

    private fun bandMean(frame: FloatArray, lo: Int, hi: Int): Float {
        val l = lo.coerceIn(0, frame.lastIndex)
        val h = hi.coerceIn(l, frame.lastIndex)
        var s = 0f; var c = 0
        for (i in l..h) { s += frame[i]; c++ }
        return if (c == 0) 0f else s / c
    }

    private fun hzToMel(hz: Float) = 2595f * log10(1f + hz / 700f)
    private fun hzToMelBin(hz: Float, nBins: Int): Int {
        val mMin = hzToMel(melFmin); val mMax = hzToMel(melFmax)
        val r = ((hzToMel(hz) - mMin) / (mMax - mMin)).coerceIn(0f, 1f)
        return (r * (nBins - 1)).roundToInt()
    }

    private fun ema(prev: Float, now: Float, a: Float) = a * prev + (1f - a) * now
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun silence() = ClassificationResult(
        silenceProb = 0.94f, noiseProb = 0.05f, cryProb = 0.01f,
        predictedClass = ClassificationResult.CryClass.SILENCE
    )
    private fun noise(c: Float): ClassificationResult {
        val x = c.coerceIn(0f, 1f)
        return ClassificationResult(
            silenceProb = (0.65f - 0.35f * x).coerceIn(0.1f, 0.75f),
            noiseProb   = (0.30f + 0.60f * x).coerceIn(0.20f, 0.85f),
            cryProb     = (0.05f + 0.10f * x).coerceIn(0.02f, 0.20f),
            predictedClass = ClassificationResult.CryClass.NOISE
        )
    }
    private fun cry(c: Float): ClassificationResult {
        val x = (c + 0.10f).coerceIn(0.70f, 0.99f) // boost mic
        return ClassificationResult(
            silenceProb = (0.06f - 0.04f * x).coerceIn(0.01f, 0.20f),
            noiseProb   = (0.12f - 0.08f * x).coerceIn(0.03f, 0.25f),
            cryProb     = x,
            predictedClass = ClassificationResult.CryClass.BABY_CRY
        )
    }
}
