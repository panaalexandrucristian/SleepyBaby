package ro.pana.sleepybaby.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import ro.pana.sleepybaby.audio.ShushRecorder
import ro.pana.sleepybaby.engine.AutomationConfig
import ro.pana.sleepybaby.engine.AutomationState
import ro.pana.sleepybaby.engine.CryDetectionEngine
import ro.pana.sleepybaby.core.ai.OnDeviceCryClassifier
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow

/**
 * Foreground service that runs the cry detection engine
 */
class SleepyBabyService : Service() {

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
    }

    inner class SleepyBabyBinder : Binder() {
        fun getService(): SleepyBabyService = this@SleepyBabyService
    }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        cryDetectionEngine = CryDetectionEngine(this)
        shushRecorder = ShushRecorder(this)

        serviceScope.launch {
            when (cryDetectionEngine.loadOnDeviceClassifier()) {
                OnDeviceCryClassifier.Backend.ENERGY ->
                    Log.i("SleepyBabyService", "Detector ready (energy heuristic)")
                OnDeviceCryClassifier.Backend.UNINITIALIZED ->
                    Log.e("SleepyBabyService", "Failed to initialize detector")
            }
        }

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
        Log.d("SleepyBabyService", "Service destroyed")
    }

    private fun ensureForeground() {
        if (inForeground) return

        val notification = createNotification(AutomationState.Listening)

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
    }

    private fun exitForeground() {
        if (!inForeground) return

        stopForeground(STOP_FOREGROUND_REMOVE)
        inForeground = false
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

    fun getEngineState(): StateFlow<AutomationState> = cryDetectionEngine.state

    fun startDetection() {
        ensureForeground()
        cryDetectionEngine.start()
    }

    fun stopDetection() {
        cryDetectionEngine.stop()
        exitForeground()
    }

    suspend fun initializeClassifier(): OnDeviceCryClassifier.Backend =
        cryDetectionEngine.loadOnDeviceClassifier()

    suspend fun recordShushSample(): String? {
        stopShushPreview()
        cryDetectionEngine.stop()
        return shushRecorder.record()?.toString()
    }

    fun currentShushSample(): String? = shushRecorder.recordingUri()?.toString()

    suspend fun playShushPreview(): Boolean = withContext(Dispatchers.Main) {
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

    fun stopShushPreview() {
        val player = shushPreviewPlayer ?: return
        player.removeListener(shushPreviewListener)
        try {
            player.stop()
        } catch (_: IllegalStateException) {
        }
        player.release()
        shushPreviewPlayer = null
    }

    fun isShushPreviewPlaying(): Boolean = shushPreviewPlayer?.isPlaying == true

    fun updateConfig(config: AutomationConfig) {
        cryDetectionEngine.updateConfig(config)
    }
}
