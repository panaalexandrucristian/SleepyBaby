package ro.pana.sleepybaby.core.ai

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnergyCryClassifierMelTest {

    private fun frame(value: Float) = FloatArray(64) { value }

    private suspend fun EnergyCryClassifierMel.runWarmup(
        repeats: Int = 4,
        value: Float = 0.05f
    ) {
        repeat(repeats) { classify(arrayOf(frame(value))) }
    }

    @Test
    fun `initialize returns true`() = runTest {
        val classifier = EnergyCryClassifierMel()
        assertTrue(classifier.initialize())
    }

    @Test
    fun `silence frames classified as silence`() = runTest {
        val classifier = EnergyCryClassifierMel()
        classifier.initialize()
        classifier.runWarmup()

        val features = arrayOf(frame(0.05f))
        val result = classifier.classify(features)

        assertEquals(ClassificationResult.CryClass.SILENCE, result.predictedClass)
        assertTrue(result.silenceProb > result.noiseProb)
    }

    @Test
    fun `high energy frames classified as cry`() = runTest {
        val classifier = EnergyCryClassifierMel()
        classifier.initialize()
        classifier.runWarmup()

        val first = classifier.classify(arrayOf(frame(5.5f)))
        val second = classifier.classify(arrayOf(frame(5.5f)))

        assertEquals(ClassificationResult.CryClass.SILENCE, first.predictedClass)
        assertEquals(ClassificationResult.CryClass.BABY_CRY, second.predictedClass)
        assertTrue(second.cryProb > second.noiseProb)
    }

    @Test
    fun `moderate energy frames classified as noise`() = runTest {
        val classifier = EnergyCryClassifierMel(
            energyFactor = 1.0f,
            cryRiseGate = 0.5f,
            marginGate = 5.0f,
            noiseDeltaMin = 0.02f,
            warmupWindows = 0,
            debug = false
        )
        classifier.initialize()

        classifier.classify(arrayOf(frame(1.0f)))
        val result = classifier.classify(arrayOf(frame(1.4f)))

        assertEquals(ClassificationResult.CryClass.NOISE, result.predictedClass)
        assertTrue(result.noiseProb > result.silenceProb)
    }
}
