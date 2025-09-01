package com.sleepybaby.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sleepybaby.data.SettingsRepository
import com.sleepybaby.engine.AutomationState
import com.sleepybaby.core.ai.OnDeviceCryClassifier
import com.sleepybaby.service.SleepyBabyService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import android.content.Intent
import androidx.core.content.ContextCompat
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepyBabyScreen(
    service: SleepyBabyService?,
    settingsRepository: SettingsRepository,
    hasAudioPermission: Boolean
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val automationConfig by settingsRepository.automationConfig.collectAsState(
        initial = com.sleepybaby.engine.AutomationConfig()
    )
    val isEnabled by settingsRepository.isEnabled.collectAsState(initial = false)
    val serviceAvailable = service != null && hasAudioPermission

    var isRecordingShush by remember { mutableStateOf(false) }
    var isPlayingShush by remember { mutableStateOf(false) }
    var shushStatusMessage by remember { mutableStateOf<String?>(null) }
    var shushCountdownSeconds by remember { mutableStateOf<Int?>(null) }
    val hasCustomShush by remember(automationConfig.trackId) {
        mutableStateOf(automationConfig.trackId.startsWith("file://"))
    }

    LaunchedEffect(automationConfig.trackId) {
        isPlayingShush = false
        shushCountdownSeconds = null
    }

    LaunchedEffect(isPlayingShush, service) {
        if (isPlayingShush) {
            while (true) {
                delay(500)
                val stillPlaying = service?.isShushPreviewPlaying() == true
                if (!stillPlaying) {
                    isPlayingShush = false
                    shushStatusMessage = context.getString(com.sleepybaby.R.string.shush_preview_finished)
                    break
                }
            }
        }
    }

    val engineState by (service?.getEngineState()?.collectAsState(initial = AutomationState.Stopped)
        ?: remember { mutableStateOf(AutomationState.Stopped) })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "SleepyBaby",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "AI Cry Detection & Soothing",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!hasAudioPermission) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = context.getString(com.sleepybaby.R.string.microphone_permission_required),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = context.getString(com.sleepybaby.R.string.microphone_permission_instructions),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Status",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = engineState.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = when (engineState) {
                        is AutomationState.Listening -> MaterialTheme.colorScheme.primary
                        is AutomationState.CryingPending -> MaterialTheme.colorScheme.tertiary
                        is AutomationState.Playing -> MaterialTheme.colorScheme.secondary
                        is AutomationState.FadingOut -> MaterialTheme.colorScheme.secondary
                        is AutomationState.Stopped -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        // Controls Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Controls",
                    style = MaterialTheme.typography.titleMedium
                )

                // Enable/Disable Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Detection")
                    Switch(
                        checked = isEnabled && hasAudioPermission,
                        enabled = hasAudioPermission,
                        onCheckedChange = { enabled ->
                            if (!hasAudioPermission) {
                                Toast.makeText(
                                    context,
                                    context.getString(com.sleepybaby.R.string.microphone_permission_required),
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@Switch
                            }
                            scope.launch {
                                settingsRepository.updateEnabled(enabled)
                                if (enabled) {
                                    val intent = Intent(context, SleepyBabyService::class.java).apply {
                                        action = SleepyBabyService.ACTION_START_DETECTION
                                    }
                                    ContextCompat.startForegroundService(context, intent)
                                    service?.startDetection()
                                } else {
                                    val intent = Intent(context, SleepyBabyService::class.java).apply {
                                        action = SleepyBabyService.ACTION_STOP_DETECTION
                                    }
                                    ContextCompat.startForegroundService(context, intent)
                                    service?.stopDetection()
                                }
                            }
                        }
                    )
                }

                // Start/Stop Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                if (!hasAudioPermission || service == null) {
                                    Toast.makeText(
                                        context,
                                        context.getString(com.sleepybaby.R.string.microphone_permission_required),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@launch
                                }
                                settingsRepository.updateEnabled(true)
                                val intent = Intent(context, SleepyBabyService::class.java).apply {
                                    action = SleepyBabyService.ACTION_START_DETECTION
                                }
                                ContextCompat.startForegroundService(context, intent)
                                service.startDetection()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = serviceAvailable && (!isEnabled || engineState is AutomationState.Stopped)
                    ) {
                        Text("Start")
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                settingsRepository.updateEnabled(false)
                                val intent = Intent(context, SleepyBabyService::class.java).apply {
                                    action = SleepyBabyService.ACTION_STOP_DETECTION
                                }
                                ContextCompat.startForegroundService(context, intent)
                                service?.stopDetection()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = serviceAvailable && isEnabled && engineState !is AutomationState.Stopped
                    ) {
                        Text("Stop")
                    }
                }
            }
        }

        // Settings Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = "AI Backend",
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = context.getString(com.sleepybaby.R.string.ai_backend_on_device_info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            when (service?.initializeClassifier()) {
                                OnDeviceCryClassifier.Backend.ONNX,
                                OnDeviceCryClassifier.Backend.TFLITE -> Toast.makeText(
                                    context,
                                    context.getString(com.sleepybaby.R.string.on_device_models_loaded),
                                    Toast.LENGTH_SHORT
                                ).show()
                                OnDeviceCryClassifier.Backend.ENERGY -> Toast.makeText(
                                    context,
                                    context.getString(com.sleepybaby.R.string.on_device_energy_fallback),
                                    Toast.LENGTH_SHORT
                                ).show()
                                OnDeviceCryClassifier.Backend.UNINITIALIZED, null -> Toast.makeText(
                                    context,
                                    context.getString(com.sleepybaby.R.string.on_device_models_missing),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    enabled = serviceAvailable
                ) {
                    Text(text = context.getString(com.sleepybaby.R.string.reload_ai_models))
                }

                HorizontalDivider()

                Column {
                    Text(
                        text = stringResource(id = com.sleepybaby.R.string.shush_recording_title),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = stringResource(id = com.sleepybaby.R.string.shush_recording_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (hasCustomShush) {
                            stringResource(id = com.sleepybaby.R.string.shush_record_available)
                        } else {
                            stringResource(id = com.sleepybaby.R.string.shush_record_missing)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (hasCustomShush) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isRecordingShush) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }

                    shushCountdownSeconds?.let { remaining ->
                        Text(
                            text = stringResource(id = com.sleepybaby.R.string.shush_recording_countdown, remaining),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    shushStatusMessage?.let { status ->
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val sleepyService = service
                                    if (sleepyService == null) {
                                        shushStatusMessage = context.getString(com.sleepybaby.R.string.shush_record_failure)
                                        return@launch
                                    }

                                    val resumeAfter = serviceAvailable && isEnabled
                                    isRecordingShush = true
                                    isPlayingShush = false
                                    shushCountdownSeconds = 10
                                    shushStatusMessage = context.getString(com.sleepybaby.R.string.shush_recording_in_progress)

                                    val countdownJob = launch {
                                        for (second in 10 downTo 1) {
                                            shushCountdownSeconds = second
                                            delay(1000)
                                        }
                                        shushCountdownSeconds = null
                                    }

                                    val recordedUri = sleepyService.recordShushSample()
                                    countdownJob.cancel()
                                    shushCountdownSeconds = null
                                    isRecordingShush = false

                                    if (recordedUri != null) {
                                        settingsRepository.updateTrackId(recordedUri)
                                        sleepyService.updateConfig(automationConfig.copy(trackId = recordedUri))
                                        shushStatusMessage = context.getString(com.sleepybaby.R.string.shush_record_success)

                                        if (resumeAfter && hasAudioPermission) {
                                            val intent = Intent(context, SleepyBabyService::class.java).apply {
                                                action = SleepyBabyService.ACTION_START_DETECTION
                                            }
                                            ContextCompat.startForegroundService(context, intent)
                                            sleepyService.startDetection()
                                        }
                                    } else {
                                        shushStatusMessage = context.getString(com.sleepybaby.R.string.shush_record_failure)
                                    }
                                }
                            },
                            enabled = serviceAvailable && !isRecordingShush && !isPlayingShush
                        ) {
                            Text(
                                text = if (isRecordingShush) {
                                    stringResource(id = com.sleepybaby.R.string.shush_recording_in_progress)
                                } else {
                                    stringResource(id = com.sleepybaby.R.string.shush_record_button)
                                }
                            )
                        }

                        if (hasCustomShush) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        val sleepyService = service
                                        if (sleepyService == null) {
                                            shushStatusMessage = context.getString(com.sleepybaby.R.string.shush_preview_failed)
                                            return@launch
                                        }

                                        if (isPlayingShush) {
                                            sleepyService.stopShushPreview()
                                            isPlayingShush = false
                                            shushStatusMessage = context.getString(com.sleepybaby.R.string.shush_preview_stopped)
                                        } else {
                                            val success = sleepyService.playShushPreview()
                                            if (success) {
                                                isPlayingShush = true
                                                shushStatusMessage = context.getString(com.sleepybaby.R.string.shush_preview_playing)
                                            } else {
                                                shushStatusMessage = context.getString(com.sleepybaby.R.string.shush_preview_failed)
                                                Toast.makeText(
                                                    context,
                                                    context.getString(com.sleepybaby.R.string.shush_preview_failed),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                },
                                enabled = serviceAvailable && !isRecordingShush
                            ) {
                                Text(
                                    text = if (isPlayingShush) {
                                        stringResource(id = com.sleepybaby.R.string.shush_preview_button_stop)
                                    } else {
                                        stringResource(id = com.sleepybaby.R.string.shush_preview_button_play)
                                    }
                                )
                            }
                        }
                    }
                }

                // Cry Threshold Slider
                Column {
                    Text(
                        text = "Cry Threshold: ${automationConfig.cryThresholdSeconds}s",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Slider(
                        value = automationConfig.cryThresholdSeconds.toFloat(),
                        onValueChange = { value ->
                            scope.launch {
                                val seconds = value.toInt()
                                settingsRepository.updateCryThreshold(seconds)
                                service?.updateConfig(automationConfig.copy(cryThresholdSeconds = seconds))
                            }
                        },
                        valueRange = 1f..10f,
                        steps = 8
                    )
                }

                // Silence Threshold Slider
                Column {
                    Text(
                        text = "Silence Threshold: ${automationConfig.silenceThresholdSeconds}s",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Slider(
                        value = automationConfig.silenceThresholdSeconds.toFloat(),
                        onValueChange = { value ->
                            scope.launch {
                                val seconds = value.toInt()
                                settingsRepository.updateSilenceThreshold(seconds)
                                service?.updateConfig(automationConfig.copy(silenceThresholdSeconds = seconds))
                            }
                        },
                        valueRange = 5f..30f,
                        steps = 24
                    )
                }

                // Volume Slider
                Column {
                    Text(
                        text = "Volume: ${(automationConfig.targetVolume * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Slider(
                        value = automationConfig.targetVolume,
                        onValueChange = { value ->
                            scope.launch {
                                settingsRepository.updateTargetVolume(value)
                                service?.updateConfig(automationConfig.copy(targetVolume = value))
                            }
                        },
                        valueRange = 0.1f..1f
                    )
                }
            }
        }

        // Info Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "How it works",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• Listens for baby cries using on-device AI\n" +
                            "• Plays soothing sounds after consecutive cry detection\n" +
                            "• Automatically fades out when baby calms down\n" +
                            "• Runs completely offline for privacy",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
