package com.sleepybaby.core.ai

import kotlin.math.abs
import kotlin.math.max

/**
 * Lightweight heuristic classifier that estimates crying based on mel energy patterns.
 * Provides a fully local fallback when ML models are unavailable.
 */
class EnergyCryClassifier(
    private val cryEnergyThreshold: Float = 0.3f,
    private val cryPeakThreshold: Float = 0.08f,
    private val noiseEnergyThreshold: Float = 0.16f,
    private val noiseFluctuationThreshold: Float = 0.22f
) : CryClassifier {

    private val smoother = CrySmoother()

    override suspend fun initialize(): Boolean = true

    override suspend fun classify(features: Array<FloatArray>): ClassificationResult {
        if (features.isEmpty()) {
            return createSilenceResult()
        }

        var energySum = 0f
        var peakSum = 0f
        var fluctuationSum = 0f
        var framesCount = 0

        for (frame in features) {
            if (frame.isEmpty()) continue
            val positiveRatio = frame.count { it > 0.5f }.toFloat() / frame.size
            val peakRatio = frame.count { it > 1.5f }.toFloat() / frame.size
            val maxValue = frame.maxOrNull() ?: 0f
            val energyScore = 0.55f * positiveRatio +
                0.45f * (max(maxValue, 0f) / 3f).coerceIn(0f, 1f)

            var diffSum = 0f
            for (i in 1 until frame.size) {
                diffSum += abs(frame[i] - frame[i - 1])
            }
            val fluctuationScore = (diffSum / frame.size).coerceIn(0f, 1f)

            energySum += energyScore
            peakSum += peakRatio
            fluctuationSum += fluctuationScore
            framesCount++
        }

        if (framesCount == 0) {
            return createSilenceResult()
        }

        val avgEnergy = (energySum / framesCount).coerceIn(0f, 1f)
        val avgPeaks = (peakSum / framesCount).coerceIn(0f, 1f)
        val avgFluctuation = (fluctuationSum / framesCount).coerceIn(0f, 1f)

        val result = when {
            avgEnergy >= cryEnergyThreshold && avgPeaks >= cryPeakThreshold ->
                createCryResult(avgEnergy, avgPeaks)
            avgEnergy >= noiseEnergyThreshold || avgFluctuation >= noiseFluctuationThreshold ->
                createNoiseResult(avgEnergy, avgFluctuation)
            else -> createSilenceResult()
        }

        return smoother.smooth(result)
    }

    override fun release() {
        // Nothing to release
    }

    private fun createSilenceResult() = ClassificationResult(
        silenceProb = 0.82f,
        noiseProb = 0.13f,
        cryProb = 0.05f,
        predictedClass = ClassificationResult.CryClass.SILENCE
    )

    private fun createNoiseResult(energy: Float, fluctuation: Float): ClassificationResult {
        val noiseProb = (0.45f + energy * 0.3f + fluctuation * 0.15f).coerceIn(0.4f, 0.9f)
        val cryProb = (0.12f + energy * 0.1f).coerceAtMost(0.35f)
        val silenceProb = (1f - noiseProb - cryProb).coerceAtLeast(0.05f)
        return ClassificationResult(
            silenceProb = silenceProb,
            noiseProb = noiseProb,
            cryProb = cryProb,
            predictedClass = ClassificationResult.CryClass.NOISE
        )
    }

    private fun createCryResult(energy: Float, peaks: Float): ClassificationResult {
        val peakBoost = (peaks / 0.4f).coerceIn(0f, 1f)
        val cryProb = (0.68f + energy * 0.22f + peakBoost * 0.18f).coerceIn(0.68f, 0.99f)
        val noiseProb = (0.2f - peakBoost * 0.12f).coerceIn(0.05f, 0.25f)
        val silenceProb = (1f - cryProb - noiseProb).coerceAtLeast(0.01f)
        return ClassificationResult(
            silenceProb = silenceProb,
            noiseProb = noiseProb,
            cryProb = cryProb,
            predictedClass = ClassificationResult.CryClass.BABY_CRY
        )
    }
}
