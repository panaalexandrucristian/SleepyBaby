package ro.pana.sleepybaby.core.ai

import org.junit.Test
import org.junit.Assert.*
import kotlin.math.sin
import kotlin.math.PI

class MelSpecExtractorTest {

    @Test
    fun `extract returns correct shape`() {
        val extractor = MelSpecExtractor(
            sampleRate = 16000,
            windowSizeMs = 1000,
            hopSizeMs = 500,
            melBins = 64
        )

        // 2 seconds of audio = 32000 samples
        val audioData = ShortArray(32000) { (sin(2 * PI * 440 * it / 16000) * 16000).toInt().toShort() }

        val features = extractor.extract(audioData)

        // Should have 3 frames: 0-1s, 0.5-1.5s, 1.0-2.0s
        assertEquals(3, features.size)
        assertEquals(64, features[0].size)
    }

    @Test
    fun `extract returns absolute energy values`() {
        val extractor = MelSpecExtractor(melBins = 64)

        // Generate tone
        val audioData = ShortArray(16000) { (sin(2 * PI * 440 * it / 16000) * 16000).toInt().toShort() }

        val features = extractor.extract(audioData)

        // With absolute log-mel, values should be non-negative and preserve relative energy.
        if (features.isNotEmpty()) {
            val frame = features[0]
            assertTrue("Frame should contain positive energy bins", frame.all { it >= 0f })
            assertTrue("Energy should not be all zeros", frame.any { it > 0f })
        }
    }

    @Test
    fun `extract normalizes frames when enabled`() {
        val extractor = MelSpecExtractor(melBins = 64, normalizePerFrame = true)
        val audioData = ShortArray(16000) { (sin(2 * PI * 440 * it / 16000) * 16000).toInt().toShort() }

        val features = extractor.extract(audioData)

        if (features.isNotEmpty()) {
            val frame = features[0]
            val mean = frame.average()
            val variance = frame.map { (it - mean) * (it - mean) }.average()

            assertTrue("Mean should be close to 0", kotlin.math.abs(mean) < 0.1)
            assertTrue("Variance should remain significant", variance > 0.1)
        }
    }

    @Test
    fun `extract handles empty input`() {
        val extractor = MelSpecExtractor()
        val features = extractor.extract(ShortArray(0))
        assertEquals(0, features.size)
    }
}
