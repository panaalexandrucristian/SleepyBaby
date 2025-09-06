package ro.pana.sleepybaby.ui.viewmodel

import android.app.Application
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
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
import org.junit.Before
import org.junit.Test
import ro.pana.sleepybaby.domain.controller.SleepyBabyController
import ro.pana.sleepybaby.domain.usecase.AutomationConfigUseCases
import ro.pana.sleepybaby.domain.usecase.MonitoringUseCases
import ro.pana.sleepybaby.domain.usecase.ShushUseCases
import ro.pana.sleepybaby.domain.usecase.TutorialUseCases
import ro.pana.sleepybaby.engine.AutomationConfig
import ro.pana.sleepybaby.engine.AutomationState
import ro.pana.sleepybaby.core.ai.OnDeviceCryClassifier
import ro.pana.sleepybaby.R
import org.junit.Assert.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SleepyBabyViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val application: Application = mockk(relaxed = true)

    private val automationFlow = MutableStateFlow(AutomationConfig())
    private val monitoringFlow = MutableStateFlow(false)
    private val tutorialFlow = MutableStateFlow(true)
    private val engineStateFlow = MutableStateFlow<AutomationState>(AutomationState.Stopped)

    private val configUseCases = mockk<AutomationConfigUseCases>()
    private val monitoringUseCases = mockk<MonitoringUseCases>()
    private val tutorialUseCases = mockk<TutorialUseCases>()
    private val shushUseCases = mockk<ShushUseCases>()

    private val controller = mockk<SleepyBabyController>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)

        every { configUseCases.observeConfig() } returns automationFlow
        coEvery { configUseCases.updateCryThreshold(any()) } just runs
        coEvery { configUseCases.updateSilenceThreshold(any()) } just runs
        coEvery { configUseCases.updateTargetVolume(any()) } just runs
        coEvery { configUseCases.updateTrackId(any()) } just runs

        every { monitoringUseCases.observeEnabled() } returns monitoringFlow
        coEvery { monitoringUseCases.setEnabled(any()) } just runs
        coEvery { monitoringUseCases.initializeDetector(any()) } returns OnDeviceCryClassifier.Backend.ENERGY
        every { monitoringUseCases.start(any()) } just runs
        every { monitoringUseCases.stop(any()) } just runs
        every { monitoringUseCases.observeEngine(any()) } returns engineStateFlow

        every { tutorialUseCases.observeCompleted() } returns tutorialFlow
        coEvery { tutorialUseCases.setCompleted(any()) } just runs

        coEvery { shushUseCases.record(any()) } returns "file:///custom_shush.mp3"
        coEvery { shushUseCases.playPreview(any()) } returns true
        every { shushUseCases.stopPreview(any()) } just runs

        every { controller.isShushPreviewPlaying() } returns false
        every { controller.updateConfig(any()) } just runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SleepyBabyViewModel {
        return SleepyBabyViewModel(
            application = application,
            configUseCases = configUseCases,
            monitoringUseCases = monitoringUseCases,
            tutorialUseCases = tutorialUseCases,
            shushUseCases = shushUseCases
        )
    }

    @Test
    fun `onStartMonitoringRequested starts service and persists flag`() = runTest {
        automationFlow.value = AutomationConfig(trackId = "file:///custom_shush.mp3")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAudioPermissionChanged(true)
        viewModel.onControllerConnected(controller)
        advanceUntilIdle()

        viewModel.onStartMonitoringRequested()
        advanceUntilIdle()

        verify(exactly = 1) { monitoringUseCases.start(controller) }
        coVerify(exactly = 1) { monitoringUseCases.setEnabled(true) }
    }

    @Test
    fun `onRecordShushRequested saves track id and updates state`() = runTest {
        automationFlow.value = AutomationConfig(trackId = "asset:///shhh_loop.mp3")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAudioPermissionChanged(true)
        viewModel.onControllerConnected(controller)
        advanceUntilIdle()

        viewModel.onRecordShushRequested()
        advanceUntilIdle()

        coVerify(exactly = 1) { configUseCases.updateTrackId("file:///custom_shush.mp3") }
        assertEquals(R.string.shush_record_success, viewModel.uiState.value.shushStatusMessage)
    }
}
