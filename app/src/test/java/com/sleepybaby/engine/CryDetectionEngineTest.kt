package com.sleepybaby.engine

import com.sleepybaby.core.ai.ClassificationResult
import com.sleepybaby.core.ai.CryClassifier
import org.junit.Test
import org.junit.Assert.*

class CryDetectionEngineTest {

    @Test
    fun `automation config creation works`() {
        val config = AutomationConfig(
            cryThresholdSeconds = 5,
            silenceThresholdSeconds = 15
        )
        assertEquals(5, config.cryThresholdSeconds)
        assertEquals(15, config.silenceThresholdSeconds)
    }

    @Test
    fun `automation states have correct string representation`() {
        assertEquals("Listening", AutomationState.Listening.toString())
        assertEquals("Playing", AutomationState.Playing.toString())
        assertEquals("Stopped", AutomationState.Stopped.toString())
        assertEquals("CryingPending(consecutiveCrySeconds=3)", AutomationState.CryingPending(3).toString())
        assertEquals("FadingOut(remainingMs=5000)", AutomationState.FadingOut(5000).toString())
    }
}

// Mock classifier for testing
class TestCryClassifier(
    private val responses: List<ClassificationResult.CryClass>
) : CryClassifier {

    private var callCount = 0

    override suspend fun initialize(): Boolean = true

    override suspend fun classify(features: Array<FloatArray>): ClassificationResult {
        val response = if (callCount < responses.size) {
            responses[callCount]
        } else {
            ClassificationResult.CryClass.SILENCE
        }
        callCount++

        return when (response) {
            ClassificationResult.CryClass.BABY_CRY -> ClassificationResult(0.1f, 0.2f, 0.7f, response)
            ClassificationResult.CryClass.NOISE -> ClassificationResult(0.2f, 0.7f, 0.1f, response)
            ClassificationResult.CryClass.SILENCE -> ClassificationResult(0.8f, 0.1f, 0.1f, response)
        }
    }

    override fun release() {}
}