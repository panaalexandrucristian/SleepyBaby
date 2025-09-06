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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
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
        samplePeriodMs = 10
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        engine = CryDetectionEngine(context)
        engine.setConfigForTest(config)
        engine.setNoisePlayerForTest(noisePlayer)
        engine.setStateForTest(AutomationState.Listening)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
        Dispatchers.resetMain()
    }

    @Test
    fun `when cry threshold reached playback starts`() = runTest {
        coEvery { noisePlayer.play(any(), any(), any()) } returns Unit

        engine.processClassification(ClassificationResult.CryClass.BABY_CRY)

        assertTrue(engine.state.value is AutomationState.CryingPending)

        engine.processClassification(ClassificationResult.CryClass.BABY_CRY)
        advanceUntilIdle()

        assertTrue(engine.state.value is AutomationState.Playing)
        coVerify(exactly = 1) {
            noisePlayer.play(config.trackId, config.targetVolume, config.fadeInMs)
        }
    }

    @Test
    fun `when silence threshold reached playback fades out`() = runTest {
        coEvery { noisePlayer.play(any(), any(), any()) } returns Unit
        every { noisePlayer.fadeOut(any()) } returns Unit
        every { noisePlayer.stop() } returns Unit

        engine.processClassification(ClassificationResult.CryClass.BABY_CRY)
        engine.processClassification(ClassificationResult.CryClass.BABY_CRY)
        advanceUntilIdle()

        repeat(config.silenceThresholdSeconds) {
            engine.processClassification(ClassificationResult.CryClass.SILENCE)
        }

        advanceUntilIdle()

        verify(exactly = 1) { noisePlayer.fadeOut(config.fadeOutMs) }
        verify(exactly = 1) { noisePlayer.stop() }
        assertTrue(engine.state.value is AutomationState.Listening)
    }

    @Test
    fun `non cry classification resets pending state`() = runTest {
        engine.processClassification(ClassificationResult.CryClass.BABY_CRY)
        assertTrue(engine.state.value is AutomationState.CryingPending)

        engine.processClassification(ClassificationResult.CryClass.SILENCE)
        advanceUntilIdle()

        assertTrue(engine.state.value is AutomationState.Listening)
    }
}

private fun CryDetectionEngine.setStateForTest(state: AutomationState) {
    val field = CryDetectionEngine::class.java.getDeclaredField("_state")
    field.isAccessible = true
    val flow = field.get(this) as MutableStateFlow<AutomationState>
    flow.value = state
}

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
