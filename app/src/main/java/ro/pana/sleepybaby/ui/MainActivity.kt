package ro.pana.sleepybaby.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import ro.pana.sleepybaby.data.SettingsRepository
import ro.pana.sleepybaby.core.ai.OnDeviceCryClassifier
import ro.pana.sleepybaby.service.SleepyBabyService
import ro.pana.sleepybaby.ui.theme.SleepyBabyTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var sleepyBabyService: SleepyBabyService? by mutableStateOf(null)
    private var bound = false
    private lateinit var settingsRepository: SettingsRepository
    private var hasAudioPermission by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (!isGranted) {
            Toast.makeText(
                this,
                getString(ro.pana.sleepybaby.R.string.microphone_permission_required),
                Toast.LENGTH_LONG
            ).show()
            lifecycleScope.launch {
                settingsRepository.updateEnabled(false)
            }
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op, foreground notification updates show rationale via system UI */ }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as SleepyBabyService.SleepyBabyBinder
            sleepyBabyService = binder.getService()
            bound = true
            lifecycleScope.launch {
                val config = settingsRepository.automationConfig.first()
                sleepyBabyService?.updateConfig(config)

                when (sleepyBabyService?.initializeClassifier()) {
                    OnDeviceCryClassifier.Backend.UNINITIALIZED, null -> {
                        Toast.makeText(
                            this@MainActivity,
                            getString(ro.pana.sleepybaby.R.string.detector_unavailable),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    else -> Unit
                }

                val shouldEnable = settingsRepository.isEnabled.first()
                if (hasAudioPermission && shouldEnable) {
                    sleepyBabyService?.startDetection()
                } else {
                    sleepyBabyService?.stopDetection()
                }
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            bound = false
            sleepyBabyService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        settingsRepository = SettingsRepository(this)

        checkAudioPermission()
        checkNotificationPermission()

        setContent {
            SleepyBabyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SleepyBabyScreen(
                        service = sleepyBabyService,
                        settingsRepository = settingsRepository,
                        hasAudioPermission = hasAudioPermission
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
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
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
}
