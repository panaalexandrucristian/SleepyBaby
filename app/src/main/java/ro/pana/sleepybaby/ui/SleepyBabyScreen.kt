package ro.pana.sleepybaby.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ro.pana.sleepybaby.R
import ro.pana.sleepybaby.engine.AutomationState
import ro.pana.sleepybaby.ui.viewmodel.SleepyBabyUiState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepyBabyScreen(
    state: SleepyBabyUiState,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
    onMonitoringToggle: (Boolean) -> Unit,
    onCryThresholdChanged: (Int) -> Unit,
    onSilenceThresholdChanged: (Int) -> Unit,
    onTargetVolumeChanged: (Float) -> Unit,
    onBrightnessChanged: (Float) -> Unit,
    onRecordShush: () -> Unit,
    onPreviewToggle: () -> Unit,
    onTutorialSkip: () -> Unit,
    onTutorialDone: () -> Unit,
    onTutorialReplay: () -> Unit
) {
    val engineStatusLabel = when (state.engineState) {
        is AutomationState.Listening -> stringResource(id = R.string.state_listening)
        is AutomationState.CryingPending -> stringResource(id = R.string.state_pending)
        is AutomationState.Playing -> stringResource(id = R.string.state_playing)
        is AutomationState.FadingOut -> stringResource(id = R.string.state_fading)
        is AutomationState.Stopped -> stringResource(id = R.string.state_stopped)
    }

    val targetEngineStatusColor = when (state.engineState) {
        is AutomationState.Stopped -> MaterialTheme.colorScheme.onSurfaceVariant
        is AutomationState.Listening -> MaterialTheme.colorScheme.primary
        is AutomationState.CryingPending -> MaterialTheme.colorScheme.secondary
        is AutomationState.Playing -> MaterialTheme.colorScheme.primaryContainer
        is AutomationState.FadingOut -> MaterialTheme.colorScheme.secondary
    }
    val engineStatusColor by animateColorAsState(targetValue = targetEngineStatusColor, label = "engineStatusColor")

    if (state.tutorialVisible) {
        SleepyBabyTutorialDialog(
            onSkip = onTutorialSkip,
            onFinished = onTutorialDone
        )
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
                serviceAvailable = state.serviceConnected && state.hasAudioPermission,
                hasCustomShush = state.hasCustomShush
            )

            AnimatedVisibility(visible = !state.hasAudioPermission) {
                PermissionBanner(
                    title = stringResource(id = R.string.microphone_permission_required),
                    description = stringResource(id = R.string.microphone_permission_instructions)
                )
            }

            SectionCard(
                title = stringResource(id = R.string.monitor_title),
                subtitle = if (state.serviceConnected && state.hasAudioPermission) {
                    stringResource(id = R.string.monitor_subtitle_on)
                } else {
                    stringResource(id = R.string.monitor_subtitle_off)
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatusBadge(text = engineStatusLabel, color = engineStatusColor)

                    Text(
                        text = if (state.hasCustomShush) {
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
                                text = if (state.hasAudioPermission) {
                                    stringResource(id = R.string.monitor_toggle_support_on)
                                } else {
                                    stringResource(id = R.string.monitor_toggle_support_off)
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = state.isMonitoringEnabled && state.monitorControlsEnabled,
                                enabled = state.monitorControlsEnabled,
                                onCheckedChange = onMonitoringToggle
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onStartMonitoring,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = state.monitorControlsEnabled && (!state.isMonitoringEnabled || state.engineState is AutomationState.Stopped)
                        ) {
                            Text(text = stringResource(id = R.string.monitor_btn_start))
                        }

                        OutlinedButton(
                            onClick = onStopMonitoring,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = state.monitorControlsEnabled && state.isMonitoringEnabled && state.engineState !is AutomationState.Stopped
                        ) {
                            Text(text = stringResource(id = R.string.monitor_btn_stop))
                        }
                    }
                }
            }

            SectionCard(
                title = stringResource(id = R.string.brightness_title),
                subtitle = stringResource(id = R.string.brightness_subtitle)
            ) {
                val brightnessLabel = stringResource(
                    id = R.string.brightness_value,
                    (state.brightness * 100).roundToInt()
                )

                SliderSetting(
                    title = brightnessLabel,
                    value = state.brightness,
                    valueRange = 0.1f..1f,
                    steps = 0,
                    onValueChange = onBrightnessChanged
                )

                Text(
                    text = stringResource(id = R.string.brightness_helper),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionCard(
                title = stringResource(id = R.string.shush_recording_title),
                subtitle = stringResource(id = R.string.shush_recording_description)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onRecordShush,
                        enabled = state.hasAudioPermission && !state.isRecordingShush && !state.isPlayingShushPreview,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (state.isRecordingShush) {
                                stringResource(id = R.string.shush_recording_in_progress)
                            } else {
                                stringResource(id = R.string.shush_record_button)
                            }
                        )
                    }

                    AnimatedVisibility(visible = state.hasCustomShush) {
                        OutlinedButton(
                            onClick = onPreviewToggle,
                            enabled = state.monitorControlsEnabled && !state.isRecordingShush,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (state.isPlayingShushPreview) {
                                    stringResource(id = R.string.shush_preview_button_stop)
                                } else {
                                    stringResource(id = R.string.shush_preview_button_play)
                                }
                            )
                        }
                    }

                    AnimatedVisibility(visible = state.shushCountdownSeconds != null) {
                        StatusBadge(
                            text = stringResource(
                                id = R.string.shush_recording_countdown,
                                state.shushCountdownSeconds ?: 0
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = if (state.hasCustomShush) {
                            stringResource(id = R.string.shush_record_available)
                        } else {
                            stringResource(id = R.string.shush_record_missing)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.hasCustomShush) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    AnimatedVisibility(visible = state.shushStatusMessage != null) {
                        state.shushStatusMessage?.let { statusRes ->
                            Text(
                                text = stringResource(id = statusRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            SectionCard(
                title = stringResource(id = R.string.params_title),
                subtitle = stringResource(id = R.string.params_subtitle)
            ) {
                SliderSetting(
                    title = stringResource(id = R.string.param_cry_threshold, state.automationConfig.cryThresholdSeconds),
                    value = state.automationConfig.cryThresholdSeconds.toFloat(),
                    valueRange = 1f..10f,
                    steps = 8,
                    onValueChange = { value -> onCryThresholdChanged(value.roundToInt()) }
                )

                SliderSetting(
                    title = stringResource(id = R.string.param_silence_threshold, state.automationConfig.silenceThresholdSeconds),
                    value = state.automationConfig.silenceThresholdSeconds.toFloat(),
                    valueRange = 5f..30f,
                    steps = 24,
                    onValueChange = { value -> onSilenceThresholdChanged(value.roundToInt()) }
                )

                SliderSetting(
                    title = stringResource(id = R.string.param_target_volume, (state.automationConfig.targetVolume * 100).toInt()),
                    value = state.automationConfig.targetVolume,
                    valueRange = 0.1f..1f,
                    steps = 0,
                    onValueChange = onTargetVolumeChanged
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            InfoCard(onRestartTutorial = onTutorialReplay)
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
                Crossfade(targetState = serviceAvailable, label = "heroStatus") { available ->
                    Text(
                        text = if (available) stringResource(id = R.string.hero_status_on) else stringResource(id = R.string.hero_status_off),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Crossfade(targetState = serviceAvailable, label = "heroDesc") { available ->
                    Text(
                        text = if (available) stringResource(id = R.string.hero_desc_on) else stringResource(id = R.string.hero_desc_off),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroInfoRow(
                        painter = painterResource(id = R.drawable.ic_live_monitor),
                        text = stringResource(id = R.string.hero_info_1)
                    )
                    Crossfade(targetState = hasCustomShush, label = "heroCustomInfo") { custom ->
                        HeroInfoRow(
                            painter = painterResource(id = R.drawable.ic_shush),
                            text = if (custom) stringResource(id = R.string.hero_info_2_has_custom) else stringResource(id = R.string.hero_info_2_no_custom)
                        )
                    }
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
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
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
private fun InfoCard(onRestartTutorial: () -> Unit) {
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

            FilledTonalButton(onClick = onRestartTutorial) {
                Text(text = stringResource(id = R.string.tutorial_restart_button))
            }
        }
    }
}

private data class TutorialStep(val title: String, val description: String)

@Composable
private fun SleepyBabyTutorialDialog(
    onSkip: () -> Unit,
    onFinished: () -> Unit
) {
    val steps = listOf(
        TutorialStep(
            title = stringResource(id = R.string.tutorial_step_record_title),
            description = stringResource(id = R.string.tutorial_step_record_body)
        ),
        TutorialStep(
            title = stringResource(id = R.string.tutorial_step_permission_title),
            description = stringResource(id = R.string.tutorial_step_permission_body)
        ),
        TutorialStep(
            title = stringResource(id = R.string.tutorial_step_monitor_title),
            description = stringResource(id = R.string.tutorial_step_monitor_body)
        )
    )

    var stepIndex by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        stepIndex = 0
    }

    val currentStep = steps[stepIndex]
    val progress = (stepIndex + 1f) / steps.size

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
            shadowElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.tutorial_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                    TextButton(onClick = onSkip) {
                        Text(text = stringResource(id = R.string.tutorial_skip))
                    }
                }

                Text(
                    text = stringResource(
                        id = R.string.tutorial_progress,
                        stepIndex + 1,
                        steps.size
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LinearProgressIndicator(
                    progress = { progress },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = currentStep.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = currentStep.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (stepIndex > 0) {
                        TextButton(onClick = { stepIndex-- }) {
                            Text(text = stringResource(id = R.string.tutorial_back))
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Button(
                        onClick = {
                            if (stepIndex == steps.lastIndex) {
                                onFinished()
                            } else {
                                stepIndex++
                            }
                        }
                    ) {
                        Text(
                            text = if (stepIndex == steps.lastIndex) {
                                stringResource(id = R.string.tutorial_done)
                            } else {
                                stringResource(id = R.string.tutorial_next)
                            }
                        )
                    }
                }
            }
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
