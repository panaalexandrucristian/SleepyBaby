package ro.pana.sleepybaby.core.ai

import org.junit.Test
import org.junit.Assert.*

class CrySmootherTest {

    @Test
    fun `smooth returns input for first few results`() {
        val smoother = CrySmoother(windowSize = 5)

        val result = ClassificationResult(
            silenceProb = 0.8f,
            noiseProb = 0.1f,
            cryProb = 0.1f,
            predictedClass = ClassificationResult.CryClass.SILENCE
        )

        // First result should pass through unchanged
        val smoothed = smoother.smooth(result)
        assertEquals(result.predictedClass, smoothed.predictedClass)
        assertEquals(result.silenceProb, smoothed.silenceProb, 0.01f)
    }

    @Test
    fun `smooth applies majority vote`() {
        val smoother = CrySmoother(windowSize = 5)

        // Add 3 cry results
        repeat(3) {
            smoother.smooth(ClassificationResult(
                silenceProb = 0.1f,
                noiseProb = 0.2f,
                cryProb = 0.7f,
                predictedClass = ClassificationResult.CryClass.BABY_CRY
            ))
        }

        // Add 1 silence result - cry should still win majority
        val result = smoother.smooth(ClassificationResult(
            silenceProb = 0.8f,
            noiseProb = 0.1f,
            cryProb = 0.1f,
            predictedClass = ClassificationResult.CryClass.SILENCE
        ))

        assertEquals(ClassificationResult.CryClass.BABY_CRY, result.predictedClass)
    }

    @Test
    fun `smooth averages probabilities`() {
        val smoother = CrySmoother(windowSize = 3)

        // Add results with different probabilities
        smoother.smooth(ClassificationResult(0.6f, 0.2f, 0.2f, ClassificationResult.CryClass.SILENCE))
        smoother.smooth(ClassificationResult(0.4f, 0.3f, 0.3f, ClassificationResult.CryClass.SILENCE))
        val result = smoother.smooth(ClassificationResult(0.5f, 0.25f, 0.25f, ClassificationResult.CryClass.SILENCE))

        // Should average: (0.6+0.4+0.5)/3 = 0.5
        assertEquals(0.5f, result.silenceProb, 0.01f)
        assertEquals(0.25f, result.noiseProb, 0.01f)
        assertEquals(0.25f, result.cryProb, 0.01f)
    }
}