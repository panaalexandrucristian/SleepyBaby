package com.sleepybaby.core.ai

/**
 * Cry detection classification results
 */
data class ClassificationResult(
    val silenceProb: Float,
    val noiseProb: Float,
    val cryProb: Float,
    val predictedClass: CryClass
) {
    enum class CryClass { SILENCE, NOISE, BABY_CRY }
}

/**
 * Interface for cry detection classifiers
 */
interface CryClassifier {
    /**
     * Initialize the classifier. Returns true if successful.
     */
    suspend fun initialize(): Boolean

    /**
     * Classify mel-spectrogram features
     * @param features Float array [timeFrames, melBins]
     * @return Classification result with probabilities
     */
    suspend fun classify(features: Array<FloatArray>): ClassificationResult

    /**
     * Release resources
     */
    fun release()
}