package ro.pana.sleepybaby.engine

import android.content.Context
import android.util.Log
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ro.pana.sleepybaby.audio.NoisePlayer
import ro.pana.sleepybaby.core.ai.ClassificationResult

@OptIn(ExperimentalCoroutinesApi::class)
class CryDetectionEngineTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var engine: CryDetectionEngine
    private val context: Context = mockk(relaxed = true)
    private val noisePlayer: NoisePlayer = mockk(relaxed = true)
    private val config = AutomationConfig(
        cryThresholdSeconds = 2,
        silenceThresholdSeconds = 2,
        fadeInMs = 100,
        fadeOutMs = 200,
        targetVolume = 0.5f,
        trackId = "file:///sample.mp3",
        samplePeriodMs = 1000
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.v(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<Throwable>()) } returns 0
        engine = CryDetectionEngine(context)
        engine.setConfigForTest(config)
        engine.setNoisePlayerForTest(noisePlayer)
        engine.setStateForTest(AutomationState.Listening)
    }

    @After
    fun tearDown() {
        engine.stop()
        unmockkStatic(Log::class)
        Dispatchers.resetMain()
    }

    @Test
    fun `sound detection triggers shush playback loops`() = runTest {
        coEvery { noisePlayer.playLoops(any(), any(), any(), any()) } returns Unit
        every { noisePlayer.stop() } returns Unit

        engine.setEnergyForTest(window = 0.6f, band = 0.75f)
        engine.processClassification(soundResult())
        advanceUntilIdle()
        engine.awaitPlaybackJobForTest()

        assertTrue(engine.state.value is AutomationState.Listening)
        assertEquals(2, engine.getCooldownForTest())
        coVerify(exactly = 1) {
            noisePlayer.playLoops(
                config.trackId,
                any(),
                config.targetVolume,
                config.fadeInMs
            )
        }
        verify(exactly = 1) { noisePlayer.stop() }
    }

    @Test
    fun `cooldown prevents immediate retrigger`() = runTest {
        coEvery { noisePlayer.playLoops(any(), any(), any(), any()) } returns Unit
        every { noisePlayer.stop() } returns Unit

        engine.setEnergyForTest(window = 0.6f, band = 0.75f)
        engine.processClassification(soundResult())
        advanceUntilIdle()
        engine.awaitPlaybackJobForTest()

        assertEquals(2, engine.getCooldownForTest())

        // First sample after playback only decrements cooldown
        engine.setEnergyForTest(window = 0.6f, band = 0.75f)
        engine.processClassification(soundResult())
        advanceUntilIdle()
        assertEquals(1, engine.getCooldownForTest())

        // Second sample clears cooldown without triggering playback
        engine.setEnergyForTest(window = 0.6f, band = 0.75f)
        engine.processClassification(soundResult())
        advanceUntilIdle()
        assertEquals(0, engine.getCooldownForTest())

        // Third sample (after cooldown elapsed) should trigger again
        engine.setEnergyForTest(window = 0.6f, band = 0.75f)
        engine.processClassification(soundResult())
        advanceUntilIdle()
        engine.awaitPlaybackJobForTest()

        assertEquals(2, engine.getCooldownForTest())
        coVerify(exactly = 2) {
            noisePlayer.playLoops(
                config.trackId,
                any(),
                config.targetVolume,
                config.fadeInMs
            )
        }
    }

    @Test
    fun `silence input does not trigger playback`() = runTest {
        coEvery { noisePlayer.playLoops(any(), any(), any(), any()) } returns Unit
        every { noisePlayer.stop() } returns Unit

        engine.setEnergyForTest(window = 0.1f, band = 0.1f)
        engine.processClassification(silenceResult())
        advanceUntilIdle()
        assertEquals(0, engine.getCooldownForTest())

        engine.setEnergyForTest(window = 0.6f, band = 0.75f)
        engine.processClassification(soundResult())
        advanceUntilIdle()
        engine.awaitPlaybackJobForTest()

        // Cooldown should only start after the actual sound-triggered playback
        assertEquals(2, engine.getCooldownForTest())
    }
}

private fun CryDetectionEngine.setStateForTest(state: AutomationState) {
    val field = CryDetectionEngine::class.java.getDeclaredField("_state")
    field.isAccessible = true
    val flow = field.get(this) as MutableStateFlow<AutomationState>
    flow.value = state
}

private fun soundResult() = ClassificationResult(
    silenceProb = 0.1f,
    noiseProb = 0.8f,
    cryProb = 0.1f,
    predictedClass = ClassificationResult.CryClass.NOISE
)

private fun silenceResult() = ClassificationResult(
    silenceProb = 0.9f,
    noiseProb = 0.05f,
    cryProb = 0.05f,
    predictedClass = ClassificationResult.CryClass.SILENCE
)

private fun CryDetectionEngine.setConfigForTest(config: AutomationConfig) {
    val field = CryDetectionEngine::class.java.getDeclaredField("currentConfig")
    field.isAccessible = true
    field.set(this, config)
}

private fun CryDetectionEngine.setNoisePlayerForTest(player: NoisePlayer) {
    val field = CryDetectionEngine::class.java.getDeclaredField("noisePlayer")
    field.isAccessible = true
    field.set(this, player)
}

private fun CryDetectionEngine.getCooldownForTest(): Int {
    val field = CryDetectionEngine::class.java.getDeclaredField("cooldownSamples")
    field.isAccessible = true
    return field.getInt(this)
}

private suspend fun CryDetectionEngine.awaitPlaybackJobForTest() {
    val field = CryDetectionEngine::class.java.getDeclaredField("playbackJob")
    field.isAccessible = true
    val job = field.get(this) as? Job
    job?.join()
}

private fun CryDetectionEngine.setEnergyForTest(window: Float, band: Float) {
    val windowField = CryDetectionEngine::class.java.getDeclaredField("lastWindowEnergy01")
    windowField.isAccessible = true
    windowField.setFloat(this, window)

    val bandField = CryDetectionEngine::class.java.getDeclaredField("lastBandEnergy01")
    bandField.isAccessible = true
    bandField.setFloat(this, band)
}
