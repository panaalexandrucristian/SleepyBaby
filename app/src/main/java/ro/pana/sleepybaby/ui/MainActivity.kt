package ro.pana.sleepybaby.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import ro.pana.sleepybaby.data.SettingsRepository
import ro.pana.sleepybaby.service.AndroidDetectionServiceLauncher
import ro.pana.sleepybaby.service.SleepyBabyService
import ro.pana.sleepybaby.ui.theme.SleepyBabyTheme
import ro.pana.sleepybaby.ui.viewmodel.SleepyBabyEffect
import ro.pana.sleepybaby.ui.viewmodel.SleepyBabyViewModel
import ro.pana.sleepybaby.ui.viewmodel.SleepyBabyViewModelFactory
import ro.pana.sleepybaby.R

class MainActivity : ComponentActivity() {

    companion object {
        private const val ACTION_SHORTCUT_MONITOR = "ro.pana.sleepybaby.action.START_MONITOR"
        private const val MONITOR_SHORTCUT_ID = "monitor_shortcut"
    }

    private val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    private val detectionServiceLauncher by lazy { AndroidDetectionServiceLauncher(this) }
    private val viewModel: SleepyBabyViewModel by viewModels {
        SleepyBabyViewModelFactory(application, settingsRepository, detectionServiceLauncher)
    }

    private var sleepyBabyService: SleepyBabyService? = null
    private var bound = false
    private var hasAudioPermission = false
    private var screenBrightness = 1f

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (!isGranted) {
            Toast.makeText(
                this,
                getString(R.string.microphone_permission_required),
                Toast.LENGTH_LONG
            ).show()
        }
        viewModel.onAudioPermissionChanged(isGranted)
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op, foreground notification updates show rationale via system UI */ }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as SleepyBabyService.SleepyBabyBinder
            sleepyBabyService = binder.getService()
            bound = true
            sleepyBabyService?.let { viewModel.onControllerConnected(it) }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            bound = false
            sleepyBabyService = null
            viewModel.onControllerDisconnected()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        screenBrightness = currentWindowBrightness()
        setWindowBrightness(screenBrightness)
        checkAudioPermission()
        checkNotificationPermission()

        viewModel.setInitialBrightness(screenBrightness)
        viewModel.onAudioPermissionChanged(hasAudioPermission)

        handleShortcutIntent(intent)

        setContent {
            SleepyBabyTheme {
                val uiState by viewModel.uiState.collectAsState()
                val context = LocalContext.current
                val appContext: Context = remember(context) { context.applicationContext }

                LaunchedEffect(uiState.brightness) {
                    screenBrightness = uiState.brightness
                    setWindowBrightness(screenBrightness)
                }

                LaunchedEffect(Unit) {
                    viewModel.effects.collect { effect ->
                        when (effect) {
                            is SleepyBabyEffect.Toast ->
                                Toast.makeText(
                                    context,
                                    context.getString(effect.messageRes),
                                    Toast.LENGTH_SHORT
                                ).show()

                            is SleepyBabyEffect.ToastText ->
                                Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()

                            is SleepyBabyEffect.ShortcutAvailability ->
                                updateShortcutAvailability(appContext, effect.enabled)
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SleepyBabyScreen(
                        state = uiState,
                        onStartMonitoring = { viewModel.onStartMonitoringRequested() },
                        onStopMonitoring = { viewModel.onStopMonitoringRequested() },
                        onMonitoringToggle = viewModel::onMonitoringToggleChanged,
                        onTargetVolumeChanged = viewModel::onTargetVolumeChanged,
                        onBrightnessChanged = viewModel::onBrightnessChanged,
                        onRecordShush = viewModel::onRecordShushRequested,
                        onPreviewToggle = viewModel::onPreviewToggleRequested,
                        onTutorialSkip = viewModel::onTutorialSkipped,
                        onTutorialDone = viewModel::onTutorialFinished,
                        onTutorialReplay = viewModel::onTutorialReplayRequested
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, SleepyBabyService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (bound) {
            unbindService(serviceConnection)
            bound = false
            sleepyBabyService = null
            viewModel.onControllerDisconnected()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleShortcutIntent(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
            setWindowBrightness(screenBrightness)
        }
    }

    private fun checkAudioPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
                hasAudioPermission = true
                viewModel.onAudioPermissionChanged(true)
            }

            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun checkNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        }
    }

    private fun currentWindowBrightness(): Float {
        val rawValue = window.attributes.screenBrightness
        return if (rawValue in 0f..1f) rawValue else 1f
    }

    private fun setWindowBrightness(value: Float) {
        val clamped = value.coerceIn(0.1f, 1f)
        val params = window.attributes
        params.screenBrightness = clamped
        window.attributes = params
    }

    private fun handleShortcutIntent(intent: Intent?) {
        if (intent?.action == ACTION_SHORTCUT_MONITOR) {
            viewModel.onShortcutStartRequested()
            intent.action = null
            setIntent(intent)
        }
    }

    private fun updateShortcutAvailability(appContext: Context, enabled: Boolean) {
        if (enabled) {
            val shortcutIntent = Intent(appContext, MainActivity::class.java).apply {
                action = ACTION_SHORTCUT_MONITOR
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val shortcut = ShortcutInfoCompat.Builder(appContext, MONITOR_SHORTCUT_ID)
                .setShortLabel(appContext.getString(R.string.shortcut_monitor_short))
                .setLongLabel(appContext.getString(R.string.shortcut_monitor_long))
                .setIcon(IconCompat.createWithResource(appContext, R.drawable.ic_live_monitor))
                .setIntent(shortcutIntent)
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(appContext, shortcut)
        } else {
            ShortcutManagerCompat.removeDynamicShortcuts(appContext, listOf(MONITOR_SHORTCUT_ID))
        }
    }
}
