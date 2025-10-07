package com.sleepybaby.core.ai

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnergyCryClassifierTest {

    @Test
    fun `initialize returns true`() = runTest {
        val classifier = EnergyCryClassifier()
        assertTrue(classifier.initialize())
    }

    @Test
    fun `silence frames classified as silence`() = runTest {
        val classifier = EnergyCryClassifier()
        classifier.initialize()

        val features = arrayOf(FloatArray(64) { 0f })
        val result = classifier.classify(features)

        assertEquals(ClassificationResult.CryClass.SILENCE, result.predictedClass)
        assertTrue(result.silenceProb > result.cryProb)
    }

    @Test
    fun `high energy frames classified as cry`() = runTest {
        val classifier = EnergyCryClassifier()
        classifier.initialize()

        val features = Array(3) { FloatArray(64) { 2.5f } }
        val result = classifier.classify(features)

        assertEquals(ClassificationResult.CryClass.BABY_CRY, result.predictedClass)
        assertTrue(result.cryProb > 0.6f)
    }

    @Test
    fun `moderate energy frames classified as noise`() = runTest {
        val classifier = EnergyCryClassifier()
        classifier.initialize()

        val frame = FloatArray(64) { index -> if (index % 2 == 0) 1.2f else 0.4f }
        val features = arrayOf(frame, frame)
        val result = classifier.classify(features)

        assertEquals(ClassificationResult.CryClass.NOISE, result.predictedClass)
        assertTrue(result.noiseProb > result.silenceProb)
    }
}
