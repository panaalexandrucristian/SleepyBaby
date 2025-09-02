package ro.pana.sleepybaby.core.ai

/**
 * Smooths classification results using temporal consistency
 */
class CrySmoother(private val windowSize: Int = 5) {
    private val recentResults = mutableListOf<ClassificationResult>()

    /**
     * Apply temporal smoothing to classification result
     */
    fun smooth(result: ClassificationResult): ClassificationResult {
        recentResults.add(result)
        if (recentResults.size > windowSize) {
            recentResults.removeAt(0)
        }

        if (recentResults.size < 3) {
            return result
        }

        // Majority vote for predicted class
        val classCounts = recentResults.groupingBy { it.predictedClass }.eachCount()
        val majorityClass = classCounts.maxByOrNull { it.value }?.key ?: result.predictedClass

        // Average probabilities
        val avgSilence = recentResults.map { it.silenceProb }.average().toFloat()
        val avgNoise = recentResults.map { it.noiseProb }.average().toFloat()
        val avgCry = recentResults.map { it.cryProb }.average().toFloat()

        return ClassificationResult(
            silenceProb = avgSilence,
            noiseProb = avgNoise,
            cryProb = avgCry,
            predictedClass = majorityClass
        )
    }
}