package ro.pana.sleepybaby.domain.usecase

import kotlinx.coroutines.flow.Flow
import ro.pana.sleepybaby.data.SettingsRepository
import ro.pana.sleepybaby.domain.controller.DetectionServiceLauncher
import ro.pana.sleepybaby.domain.controller.SleepyBabyController
import ro.pana.sleepybaby.engine.AutomationConfig
import ro.pana.sleepybaby.engine.AutomationState

class AutomationConfigUseCases(private val repository: SettingsRepository) {
    fun observeConfig(): Flow<AutomationConfig> = repository.automationConfig

    suspend fun updateCryThreshold(seconds: Int) = repository.updateCryThreshold(seconds)

    suspend fun updateSilenceThreshold(seconds: Int) = repository.updateSilenceThreshold(seconds)

    suspend fun updateTargetVolume(volume: Float) = repository.updateTargetVolume(volume)

    suspend fun updateTrackId(trackId: String) = repository.updateTrackId(trackId)
}

class MonitoringUseCases(
    private val repository: SettingsRepository,
    private val launcher: DetectionServiceLauncher
) {
    fun observeEnabled(): Flow<Boolean> = repository.isEnabled

    suspend fun setEnabled(enabled: Boolean) = repository.updateEnabled(enabled)

    fun start(controller: SleepyBabyController) {
        launcher.start()
        controller.startDetection()
    }

    fun stop(controller: SleepyBabyController) {
        controller.stopDetection()
        launcher.stop()
    }

    fun observeEngine(controller: SleepyBabyController): Flow<AutomationState> = controller.engineState
}

class TutorialUseCases(private val repository: SettingsRepository) {
    fun observeCompleted(): Flow<Boolean> = repository.tutorialCompleted

    suspend fun setCompleted(completed: Boolean) = repository.setTutorialCompleted(completed)
}

class ShushUseCases {
    suspend fun record(controller: SleepyBabyController): String? = controller.recordShushSample()

    suspend fun playPreview(controller: SleepyBabyController): Boolean = controller.playShushPreview()

    fun stopPreview(controller: SleepyBabyController) = controller.stopShushPreview()
}
