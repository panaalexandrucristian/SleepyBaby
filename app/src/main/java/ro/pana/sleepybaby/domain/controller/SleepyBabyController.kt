package ro.pana.sleepybaby.domain.controller

import kotlinx.coroutines.flow.StateFlow
import ro.pana.sleepybaby.engine.AutomationConfig
import ro.pana.sleepybaby.engine.AutomationState
import ro.pana.sleepybaby.core.ai.OnDeviceCryClassifier

/**
 * Abstraction exposing the operations provided by [SleepyBabyService]
 * so that presentation layer can depend on an interface and be testable.
 */
interface SleepyBabyController {
    val engineState: StateFlow<AutomationState>

    fun startDetection()
    fun stopDetection()

    suspend fun initializeClassifier(): OnDeviceCryClassifier.Backend
    suspend fun recordShushSample(): String?
    suspend fun playShushPreview(): Boolean

    fun stopShushPreview()
    fun isShushPreviewPlaying(): Boolean

    fun updateConfig(config: AutomationConfig)
}
