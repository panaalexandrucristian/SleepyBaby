package ro.pana.sleepybaby.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import ro.pana.sleepybaby.audio.ShushRecorder
import ro.pana.sleepybaby.domain.controller.SleepyBabyController
import ro.pana.sleepybaby.engine.AutomationConfig
import ro.pana.sleepybaby.engine.AutomationState
import ro.pana.sleepybaby.engine.CryDetectionEngine
import ro.pana.sleepybaby.ui.MainActivity

/**
 * Foreground service that runs the cry detection engine
 */
class SleepyBabyService : Service(), SleepyBabyController {

    private lateinit var cryDetectionEngine: CryDetectionEngine
    private lateinit var shushRecorder: ShushRecorder
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var inForeground = false
    private var shushPreviewPlayer: ExoPlayer? = null
    private val shushPreviewListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) {
                stopShushPreview()
            }
        }
    }

    private val binder = SleepyBabyBinder()

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "sleepy_baby_service"
        const val ACTION_START_DETECTION = "start_detection"
        const val ACTION_STOP_DETECTION = "stop_detection"
        const val ACTION_RESTART_DETECTION = "restart_detection"
        @Volatile
        private var foregroundActive: Boolean = false
        @Volatile
        private var serviceCreated: Boolean = false

        fun isForegroundActive(): Boolean = foregroundActive
        fun isServiceCreated(): Boolean = serviceCreated
    }

    inner class SleepyBabyBinder : Binder() {
        fun getService(): SleepyBabyService = this@SleepyBabyService
    }

    override fun onCreate() {
        super.onCreate()

        serviceCreated = true
        createNotificationChannel()
        cryDetectionEngine = CryDetectionEngine(this)
        shushRecorder = ShushRecorder(this)

        Log.i("SleepyBabyService", "Detector ready (energy heuristic)")

        // Monitor state changes for notification updates
        serviceScope.launch {
            cryDetectionEngine.state.collect { state ->
                updateNotification(state)
            }
        }

        Log.d("SleepyBabyService", "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DETECTION -> {
                ensureForeground()
                cryDetectionEngine.start()
            }
            ACTION_RESTART_DETECTION -> {
                ensureForeground()
                serviceScope.launch {
                    try {
                        cryDetectionEngine.stop()
                    } catch (t: Throwable) {
                        Log.w("SleepyBabyService", "Restart stop failed: ${t.message}")
                    }
                    try {
                        cryDetectionEngine.start()
                        Log.i("SleepyBabyService", "Detection restarted from notification")
                    } catch (t: Throwable) {
                        Log.e("SleepyBabyService", "Failed to restart detection", t)
                    }
                }
            }
            ACTION_STOP_DETECTION -> {
                cryDetectionEngine.stop()
                exitForeground()
                stopSelf()
            }
            else -> {
                ensureForeground()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        stopShushPreview()
        cryDetectionEngine.release()
        serviceScope.cancel()
        inForeground = false
        foregroundActive = false
        serviceCreated = false
        Log.d("SleepyBabyService", "Service destroyed")
    }

    private fun ensureForeground() {
        val notification = createNotification(AutomationState.Listening)

        if (!inForeground) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            inForeground = true
            foregroundActive = true
        } else {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun exitForeground() {
        if (!inForeground) return

        stopForeground(STOP_FOREGROUND_REMOVE)
        inForeground = false
        foregroundActive = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(ro.pana.sleepybaby.R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(ro.pana.sleepybaby.R.string.notif_channel_desc)
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(state: AutomationState): Notification {
        val stopIntent = Intent(this, SleepyBabyService::class.java).apply {
            action = ACTION_STOP_DETECTION
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val restartIntent = Intent(this, SleepyBabyService::class.java).apply {
            action = ACTION_RESTART_DETECTION
        }
        val restartPendingIntent = PendingIntent.getService(
            this,
            1,
            restartIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            2,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = when (state) {
            is AutomationState.Listening -> getString(ro.pana.sleepybaby.R.string.notif_listening)
            is AutomationState.CryingPending -> getString(ro.pana.sleepybaby.R.string.notif_pending, state.consecutiveCrySeconds)
            is AutomationState.Playing -> getString(ro.pana.sleepybaby.R.string.notif_playing)
            is AutomationState.FadingOut -> getString(ro.pana.sleepybaby.R.string.notif_fading)
            is AutomationState.Stopped -> getString(ro.pana.sleepybaby.R.string.notif_stopped)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(ro.pana.sleepybaby.R.string.notif_title))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentPendingIntent)
            .addAction(
                android.R.drawable.ic_media_play,
                getString(ro.pana.sleepybaby.R.string.notif_action_restart),
                restartPendingIntent
            )
            .addAction(
                android.R.drawable.ic_media_pause,
                getString(ro.pana.sleepybaby.R.string.notif_action_stop),
                stopPendingIntent
            )
            .build()
    }

    private fun updateNotification(state: AutomationState) {
        if (!inForeground) return
        val notification = createNotification(state)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    // Public API for bound clients

    override val engineState: StateFlow<AutomationState>
        get() = cryDetectionEngine.state

    override fun startDetection() {
        ensureForeground()
        cryDetectionEngine.start()
    }

    override fun stopDetection() {
        cryDetectionEngine.stop()
        exitForeground()
    }

    override suspend fun recordShushSample(): String? {
        stopShushPreview()
        cryDetectionEngine.stop()
        return shushRecorder.record()?.toString()
    }

    fun currentShushSample(): String? = shushRecorder.recordingUri()?.toString()

    override suspend fun playShushPreview(): Boolean = withContext(Dispatchers.Main) {
        val uri = shushRecorder.recordingUri() ?: return@withContext false

        try {
            stopShushPreview()
            val player = ExoPlayer.Builder(this@SleepyBabyService).build().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true
                )
                repeatMode = Player.REPEAT_MODE_OFF
                addListener(shushPreviewListener)
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
                play()
            }
            shushPreviewPlayer = player
            true
        } catch (e: Exception) {
            Log.e("SleepyBabyService", "Failed to play shush preview", e)
            stopShushPreview()
            false
        }
    }

    override fun stopShushPreview() {
        val player = shushPreviewPlayer ?: return
        player.removeListener(shushPreviewListener)
        try {
            player.stop()
        } catch (_: IllegalStateException) {
        }
        player.release()
        shushPreviewPlayer = null
    }

    override fun isShushPreviewPlaying(): Boolean = shushPreviewPlayer?.isPlaying == true

    override fun updateConfig(config: AutomationConfig) {
        cryDetectionEngine.updateConfig(config)
    }
}
