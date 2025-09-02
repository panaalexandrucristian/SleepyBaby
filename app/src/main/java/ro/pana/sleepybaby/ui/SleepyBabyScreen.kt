package ro.pana.sleepybaby.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ro.pana.sleepybaby.R
import ro.pana.sleepybaby.data.SettingsRepository
import ro.pana.sleepybaby.engine.AutomationState
import ro.pana.sleepybaby.service.SleepyBabyService
import kotlin.math.roundToInt

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
        initial = ro.pana.sleepybaby.engine.AutomationConfig()
    )
    val isEnabled by settingsRepository.isEnabled.collectAsState(initial = false)
    val serviceAvailable = service != null && hasAudioPermission

    var isRecordingShush by remember { mutableStateOf(false) }
    var isPlayingShush by remember { mutableStateOf(false) }
    var shushStatusMessage by remember { mutableStateOf<String?>(null) }
    var shushCountdownSeconds by remember { mutableStateOf<Int?>(null) }
    val hasCustomShush = automationConfig.trackId.startsWith("file://")

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
                    shushStatusMessage = context.getString(R.string.shush_preview_finished)
                    break
                }
            }
        }
    }

    val engineState by (service?.getEngineState()?.collectAsState(initial = AutomationState.Stopped)
        ?: remember { mutableStateOf(AutomationState.Stopped) })

    val engineStatusLabel = when (engineState) {
        is AutomationState.Listening -> stringResource(id = R.string.state_listening)
        is AutomationState.CryingPending -> stringResource(id = R.string.state_pending)
        is AutomationState.Playing -> stringResource(id = R.string.state_playing)
        is AutomationState.FadingOut -> stringResource(id = R.string.state_fading)
        is AutomationState.Stopped -> stringResource(id = R.string.state_stopped)
    }

    val engineStatusColor = when (engineState) {
        is AutomationState.Stopped -> MaterialTheme.colorScheme.onSurfaceVariant
        is AutomationState.Listening -> MaterialTheme.colorScheme.primary
        is AutomationState.CryingPending -> MaterialTheme.colorScheme.secondary
        is AutomationState.Playing -> MaterialTheme.colorScheme.primaryContainer
        is AutomationState.FadingOut -> MaterialTheme.colorScheme.secondary
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                title = {
                    Text(
                        text = stringResource(id = R.string.appbar_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            HeroBanner(
                engineStatusLabel = engineStatusLabel,
                serviceAvailable = serviceAvailable,
                hasCustomShush = hasCustomShush
            )

            if (!hasAudioPermission) {
                PermissionBanner(
                    title = stringResource(id = R.string.microphone_permission_required),
                    description = stringResource(id = R.string.microphone_permission_instructions)
                )
            }

            SectionCard(
                title = stringResource(id = R.string.monitor_title),
                subtitle = if (serviceAvailable) {
                    stringResource(id = R.string.monitor_subtitle_on)
                } else {
                    stringResource(id = R.string.monitor_subtitle_off)
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatusBadge(text = engineStatusLabel, color = engineStatusColor)

                    Text(
                        text = if (hasCustomShush) {
                            stringResource(id = R.string.monitor_helper)
                        } else {
                            stringResource(id = R.string.monitor_helper_requires_recording)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    ListItem(
                        headlineContent = { Text(text = stringResource(id = R.string.monitor_toggle_title)) },
                        supportingContent = {
                            Text(
                                text = if (hasAudioPermission) {
                                    stringResource(id = R.string.monitor_toggle_support_on)
                                } else {
                                    stringResource(id = R.string.monitor_toggle_support_off)
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                        val toggleEnabled = hasAudioPermission && hasCustomShush
                        Switch(
                            checked = isEnabled && toggleEnabled,
                            enabled = toggleEnabled,
                            onCheckedChange = { enabled ->
                                if (!toggleEnabled) {
                                    val message = when {
                                        !hasAudioPermission -> context.getString(R.string.monitor_toggle_support_off)
                                        !hasCustomShush -> context.getString(R.string.monitor_needs_recording)
                                        else -> null
                                    }
                                    message?.let {
                                        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                                    }
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
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    if (!hasAudioPermission || service == null || !hasCustomShush) {
                                        Toast.makeText(
                                            context,
                                            when {
                                                !hasAudioPermission -> context.getString(R.string.monitor_toggle_support_off)
                                                !hasCustomShush -> context.getString(R.string.monitor_needs_recording)
                                                else -> context.getString(R.string.microphone_permission_required)
                                            },
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
                            modifier = Modifier.fillMaxWidth(),
                            enabled = serviceAvailable && hasCustomShush && (!isEnabled || engineState is AutomationState.Stopped)
                        ) {
                            Text(text = stringResource(id = R.string.monitor_btn_start))
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
                            modifier = Modifier.fillMaxWidth(),
                            enabled = serviceAvailable && isEnabled && engineState !is AutomationState.Stopped
                        ) {
                            Text(text = stringResource(id = R.string.monitor_btn_stop))
                        }
                }
            }
            }

            SectionCard(
                title = stringResource(id = R.string.shush_recording_title),
                subtitle = stringResource(id = R.string.detector_info)
            ) {
                Text(
                    text = stringResource(id = R.string.shush_recording_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                val sleepyService = service
                                if (sleepyService == null) {
                                    shushStatusMessage = context.getString(R.string.shush_record_failure)
                                    return@launch
                                }

                                val resumeAfter = serviceAvailable && isEnabled
                                isRecordingShush = true
                                isPlayingShush = false
                                shushCountdownSeconds = 10
                                shushStatusMessage = context.getString(R.string.shush_recording_in_progress)

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
                                    shushStatusMessage = context.getString(R.string.shush_record_success)

                                    if (resumeAfter && hasAudioPermission) {
                                        val intent = Intent(context, SleepyBabyService::class.java).apply {
                                            action = SleepyBabyService.ACTION_START_DETECTION
                                        }
                                        ContextCompat.startForegroundService(context, intent)
                                        sleepyService.startDetection()
                                    }
                                } else {
                                    shushStatusMessage = context.getString(R.string.shush_record_failure)
                                }
                            }
                        },
                        enabled = serviceAvailable && !isRecordingShush && !isPlayingShush,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isRecordingShush) {
                                stringResource(id = R.string.shush_recording_in_progress)
                            } else {
                                stringResource(id = R.string.shush_record_button)
                            }
                        )
                    }

                    if (hasCustomShush) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val sleepyService = service
                                    if (sleepyService == null) {
                                        shushStatusMessage = context.getString(R.string.shush_preview_failed)
                                        return@launch
                                    }

                                    if (isPlayingShush) {
                                        sleepyService.stopShushPreview()
                                        isPlayingShush = false
                                        shushStatusMessage = context.getString(R.string.shush_preview_stopped)
                                    } else {
                                        val success = sleepyService.playShushPreview()
                                        if (success) {
                                            isPlayingShush = true
                                            shushStatusMessage = context.getString(R.string.shush_preview_playing)
                                        } else {
                                            shushStatusMessage = context.getString(R.string.shush_preview_failed)
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.shush_preview_failed),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            },
                            enabled = serviceAvailable && !isRecordingShush,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isPlayingShush) {
                                    stringResource(id = R.string.shush_preview_button_stop)
                                } else {
                                    stringResource(id = R.string.shush_preview_button_play)
                                }
                            )
                        }
                    }
                }

                if (shushCountdownSeconds != null) {
                    StatusBadge(
                        text = stringResource(
                            id = R.string.shush_recording_countdown,
                            shushCountdownSeconds!!
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (hasCustomShush) {
                    Text(
                        text = stringResource(id = R.string.shush_record_available),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = stringResource(id = R.string.shush_record_missing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                shushStatusMessage?.let { status ->
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SectionCard(
                title = stringResource(id = R.string.params_title),
                subtitle = stringResource(id = R.string.params_subtitle)
            ) {
                SliderSetting(
                    title = stringResource(id = R.string.param_cry_threshold, automationConfig.cryThresholdSeconds),
                    value = automationConfig.cryThresholdSeconds.toFloat(),
                    valueRange = 1f..10f,
                    steps = 8,
                    onValueChange = { value ->
                        val seconds = value.roundToInt()
                        if (seconds != automationConfig.cryThresholdSeconds) {
                            scope.launch {
                                settingsRepository.updateCryThreshold(seconds)
                                service?.updateConfig(automationConfig.copy(cryThresholdSeconds = seconds))
                            }
                        }
                    }
                )

                SliderSetting(
                    title = stringResource(id = R.string.param_silence_threshold, automationConfig.silenceThresholdSeconds),
                    value = automationConfig.silenceThresholdSeconds.toFloat(),
                    valueRange = 5f..30f,
                    steps = 24,
                    onValueChange = { value ->
                        val seconds = value.roundToInt()
                        if (seconds != automationConfig.silenceThresholdSeconds) {
                            scope.launch {
                                settingsRepository.updateSilenceThreshold(seconds)
                                service?.updateConfig(automationConfig.copy(silenceThresholdSeconds = seconds))
                            }
                        }
                    }
                )

                SliderSetting(
                    title = stringResource(id = R.string.param_target_volume, (automationConfig.targetVolume * 100).toInt()),
                    value = automationConfig.targetVolume,
                    valueRange = 0.1f..1f,
                    steps = 0,
                    onValueChange = { value ->
                        if (value != automationConfig.targetVolume) {
                            scope.launch {
                                settingsRepository.updateTargetVolume(value)
                                service?.updateConfig(automationConfig.copy(targetVolume = value))
                            }
                        }
                    }
                )
            }

            InfoCard()
        }
    }
}

@Composable
private fun HeroBanner(
    engineStatusLabel: String,
    serviceAvailable: Boolean,
    hasCustomShush: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatusBadge(
                    text = engineStatusLabel,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (serviceAvailable) stringResource(id = R.string.hero_status_on) else stringResource(id = R.string.hero_status_off),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (serviceAvailable) stringResource(id = R.string.hero_desc_on) else stringResource(id = R.string.hero_desc_off),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroInfoRow(
                        painter = painterResource(id = R.drawable.ic_live_monitor),
                        text = stringResource(id = R.string.hero_info_1)
                    )
                    HeroInfoRow(
                        painter = painterResource(id = R.drawable.ic_shush),
                        text = if (hasCustomShush) stringResource(id = R.string.hero_info_2_has_custom) else stringResource(id = R.string.hero_info_2_no_custom)
                    )
                }
            }

            Spacer(modifier = Modifier.width(24.dp))

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_sleepy_logo),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(88.dp)
                )
            }
        }
    }
}

@Composable
private fun HeroInfoRow(
    painter: Painter,
    text: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            content()
        }
    }
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun SliderSetting(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun InfoCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(id = R.string.how_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(id = R.string.how_bullets),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionBanner(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        contentColor = MaterialTheme.colorScheme.primary,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
        }
    }
}
