package ro.pana.sleepybaby.ui.viewmodel

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ro.pana.sleepybaby.R
import ro.pana.sleepybaby.domain.controller.SleepyBabyController
import ro.pana.sleepybaby.domain.usecase.AutomationConfigUseCases
import ro.pana.sleepybaby.domain.usecase.MonitoringUseCases
import ro.pana.sleepybaby.domain.usecase.ShushUseCases
import ro.pana.sleepybaby.domain.usecase.TutorialUseCases
import ro.pana.sleepybaby.engine.AutomationConfig
import ro.pana.sleepybaby.engine.AutomationState

data class SleepyBabyUiState(
    val automationConfig: AutomationConfig = AutomationConfig(),
    val engineState: AutomationState = AutomationState.Stopped,
    val isMonitoringEnabled: Boolean = false,
    val hasAudioPermission: Boolean = false,
    val serviceConnected: Boolean = false,
    val hasCustomShush: Boolean = false,
    val configLoaded: Boolean = false,
    val isRecordingShush: Boolean = false,
    val isPlayingShushPreview: Boolean = false,
    val shushCountdownSeconds: Int? = null,
    @StringRes val shushStatusMessage: Int? = null,
    val tutorialVisible: Boolean = false,
    val brightness: Float = 1f
) {
    val monitorControlsEnabled: Boolean
        get() = serviceConnected && hasAudioPermission && hasCustomShush
}

sealed class SleepyBabyEffect {
    data class Toast(@StringRes val messageRes: Int) : SleepyBabyEffect()
    data class ToastText(val message: String) : SleepyBabyEffect()
    data class ShortcutAvailability(val enabled: Boolean) : SleepyBabyEffect()
}

class SleepyBabyViewModel(
    application: Application,
    private val configUseCases: AutomationConfigUseCases,
    private val monitoringUseCases: MonitoringUseCases,
    private val tutorialUseCases: TutorialUseCases,
    private val shushUseCases: ShushUseCases
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SleepyBabyUiState())
    val uiState: StateFlow<SleepyBabyUiState> = _uiState.asStateFlow()

    private val effectsChannel = Channel<SleepyBabyEffect>(Channel.BUFFERED)
    val effects: Flow<SleepyBabyEffect> = effectsChannel.receiveAsFlow()

    private var controller: SleepyBabyController? = null
    private var engineStateJob: Job? = null
    private var previewMonitorJob: Job? = null
    private var pendingShortcutStart = false
    private var pendingShortcutCustomToast = false
    private var pendingRecordRequest = false

    init {
        observeConfigUpdates()
        observeMonitoringFlag()
        observeTutorialState()
    }

    fun onAudioPermissionChanged(granted: Boolean) {
        _uiState.update { it.copy(hasAudioPermission = granted) }
        if (!granted) {
            onStopMonitoringRequested()
            pendingShortcutStart = false
            pendingRecordRequest = false
            viewModelScope.launch { monitoringUseCases.setEnabled(false) }
        } else {
            maybeResumeMonitoring()
            tryFulfillShortcutRequest()
            tryStartPendingRecord()
        }
    }

    fun setInitialBrightness(value: Float) {
        _uiState.update { it.copy(brightness = value.coerceIn(0.1f, 1f)) }
    }

    fun onBrightnessChanged(value: Float) {
        _uiState.update { it.copy(brightness = value.coerceIn(0.1f, 1f)) }
    }

    fun onControllerConnected(controller: SleepyBabyController) {
        this.controller = controller
        controller.updateConfig(_uiState.value.automationConfig)
        _uiState.update { it.copy(serviceConnected = true) }

        engineStateJob?.cancel()
        engineStateJob = viewModelScope.launch {
            monitoringUseCases.observeEngine(controller).collect { state ->
                _uiState.update { current -> current.copy(engineState = state) }
            }
        }

        maybeResumeMonitoring()
        tryFulfillShortcutRequest()
        tryStartPendingRecord()
    }

    fun onControllerDisconnected() {
        controller = null
        pendingRecordRequest = false
        engineStateJob?.cancel()
        engineStateJob = null
        previewMonitorJob?.cancel()
        previewMonitorJob = null
        _uiState.update {
            it.copy(
                serviceConnected = false,
                engineState = AutomationState.Stopped,
                isPlayingShushPreview = false
            )
        }
    }

    fun onShortcutStartRequested() {
        val state = _uiState.value
        if (!state.hasCustomShush) {
            if (state.configLoaded) {
                viewModelScope.launch {
                    effectsChannel.send(SleepyBabyEffect.Toast(R.string.shortcut_monitor_missing_custom))
                }
                pendingShortcutCustomToast = false
            } else {
                pendingShortcutCustomToast = true
            }
        } else {
            pendingShortcutCustomToast = false
        }
        if (!state.hasAudioPermission) {
            viewModelScope.launch {
                effectsChannel.send(SleepyBabyEffect.Toast(R.string.shortcut_monitor_missing_permission))
            }
        }

        pendingShortcutStart = true
        tryFulfillShortcutRequest()
    }

    fun onStartMonitoringRequested(persist: Boolean = true) {
        val controller = controller
        when {
            controller == null -> effectsChannel.trySend(SleepyBabyEffect.Toast(R.string.detector_unavailable))
            !_uiState.value.hasAudioPermission -> effectsChannel.trySend(SleepyBabyEffect.Toast(R.string.monitor_toggle_support_off))
            !_uiState.value.hasCustomShush -> effectsChannel.trySend(SleepyBabyEffect.Toast(R.string.monitor_needs_recording))
            else -> viewModelScope.launch {
                monitoringUseCases.start(controller)
                if (persist) {
                    monitoringUseCases.setEnabled(true)
                }
            }
        }
    }

    fun onStopMonitoringRequested(persist: Boolean = true) {
        val controller = controller ?: return
        viewModelScope.launch {
            monitoringUseCases.stop(controller)
            if (persist) {
                monitoringUseCases.setEnabled(false)
            }
        }
    }

    fun onMonitoringToggleChanged(enabled: Boolean) {
        if (enabled) {
            onStartMonitoringRequested()
        } else {
            onStopMonitoringRequested()
        }
    }

    fun onCryThresholdChanged(seconds: Int) {
        viewModelScope.launch {
            configUseCases.updateCryThreshold(seconds)
        }
    }

    fun onSilenceThresholdChanged(seconds: Int) {
        viewModelScope.launch {
            configUseCases.updateSilenceThreshold(seconds)
        }
    }

    fun onTargetVolumeChanged(volume: Float) {
        viewModelScope.launch {
            configUseCases.updateTargetVolume(volume)
        }
    }

    fun onRecordShushRequested() {
        if (_uiState.value.isRecordingShush) return
        pendingRecordRequest = true
        tryStartPendingRecord()
    }

    private fun tryStartPendingRecord() {
        if (!pendingRecordRequest) return
        val activeController = controller ?: return
        pendingRecordRequest = false

        val resumeAfter = _uiState.value.isMonitoringEnabled && _uiState.value.monitorControlsEnabled

        viewModelScope.launch {
            _uiState.update { it.copy(isRecordingShush = true, shushStatusMessage = null) }

            val countdownJob = launch {
                for (second in 10 downTo 1) {
                    _uiState.update { it.copy(shushCountdownSeconds = second) }
                    delay(1000)
                }
                _uiState.update { it.copy(shushCountdownSeconds = null) }
            }

            val recordedUri = shushUseCases.record(activeController)
            countdownJob.cancel()
            _uiState.update { it.copy(isRecordingShush = false, shushCountdownSeconds = null) }

            if (recordedUri != null) {
                configUseCases.updateTrackId(recordedUri)
                _uiState.update { it.copy(shushStatusMessage = R.string.shush_record_success) }

                if (resumeAfter) {
                    monitoringUseCases.start(activeController)
                    monitoringUseCases.setEnabled(true)
                }
                maybeResumeMonitoring()
            } else {
                _uiState.update { it.copy(shushStatusMessage = R.string.shush_record_failure) }
                effectsChannel.send(SleepyBabyEffect.Toast(R.string.shush_record_failure))
            }
        }
    }

    fun onPreviewToggleRequested() {
        val controller = controller ?: run {
            effectsChannel.trySend(SleepyBabyEffect.Toast(R.string.shush_preview_failed))
            return
        }

        if (_uiState.value.isPlayingShushPreview) {
            shushUseCases.stopPreview(controller)
            previewMonitorJob?.cancel()
            previewMonitorJob = null
            _uiState.update { it.copy(isPlayingShushPreview = false, shushStatusMessage = R.string.shush_preview_stopped) }
            return
        }

        viewModelScope.launch {
            val success = shushUseCases.playPreview(controller)
            if (success) {
                _uiState.update { it.copy(isPlayingShushPreview = true, shushStatusMessage = R.string.shush_preview_playing) }
                monitorPreviewPlayback(controller)
            } else {
                _uiState.update { it.copy(shushStatusMessage = R.string.shush_preview_failed) }
                effectsChannel.send(SleepyBabyEffect.Toast(R.string.shush_preview_failed))
            }
        }
    }

    fun onTutorialSkipped() {
        viewModelScope.launch { tutorialUseCases.setCompleted(true) }
        _uiState.update { it.copy(tutorialVisible = false) }
    }

    fun onTutorialFinished() {
        viewModelScope.launch { tutorialUseCases.setCompleted(true) }
        _uiState.update { it.copy(tutorialVisible = false) }
    }

    fun onTutorialReplayRequested() {
        viewModelScope.launch { tutorialUseCases.setCompleted(false) }
    }

    private fun observeConfigUpdates() {
        viewModelScope.launch {
            configUseCases.observeConfig().collect { config ->
                val previousState = _uiState.value
                val hasCustomShush = config.trackId.startsWith("file://")
                controller?.updateConfig(config)
                _uiState.update {
                    it.copy(
                        automationConfig = config,
                        hasCustomShush = hasCustomShush,
                        configLoaded = true
                    )
                }
                if (!previousState.configLoaded || previousState.hasCustomShush != hasCustomShush) {
                    viewModelScope.launch {
                        effectsChannel.send(SleepyBabyEffect.ShortcutAvailability(hasCustomShush))
                    }
                }
                if (pendingShortcutCustomToast) {
                    pendingShortcutCustomToast = false
                    if (!hasCustomShush) {
                        viewModelScope.launch {
                            effectsChannel.send(SleepyBabyEffect.Toast(R.string.shortcut_monitor_missing_custom))
                        }
                    }
                }
                maybeResumeMonitoring()
                tryFulfillShortcutRequest()
            }
        }
    }

    private fun observeMonitoringFlag() {
        viewModelScope.launch {
            monitoringUseCases.observeEnabled().collect { enabled ->
                _uiState.update { it.copy(isMonitoringEnabled = enabled) }
                if (enabled) {
                    maybeResumeMonitoring()
                } else {
                    onStopMonitoringRequested(persist = false)
                }
            }
        }
    }

    private fun observeTutorialState() {
        viewModelScope.launch {
            tutorialUseCases.observeCompleted().collect { completed ->
                _uiState.update { it.copy(tutorialVisible = !completed) }
            }
        }
    }

    private fun monitorPreviewPlayback(controller: SleepyBabyController) {
        previewMonitorJob?.cancel()
        previewMonitorJob = viewModelScope.launch {
            while (controller.isShushPreviewPlaying()) {
                delay(500)
            }
            _uiState.update { it.copy(isPlayingShushPreview = false, shushStatusMessage = R.string.shush_preview_finished) }
        }
    }

    private fun maybeResumeMonitoring() {
        val controller = controller ?: return
        val state = _uiState.value
        val canResume = state.isMonitoringEnabled && state.monitorControlsEnabled && state.engineState is AutomationState.Stopped
        if (canResume) {
            viewModelScope.launch {
                monitoringUseCases.start(controller)
            }
        }
    }

    private fun tryFulfillShortcutRequest() {
        if (!pendingShortcutStart) return
        if (controller == null) return
        val state = _uiState.value
        val controlsReady = state.monitorControlsEnabled && !state.isMonitoringEnabled
        if (!controlsReady) return
        pendingShortcutStart = false
        onStartMonitoringRequested()
    }
}
