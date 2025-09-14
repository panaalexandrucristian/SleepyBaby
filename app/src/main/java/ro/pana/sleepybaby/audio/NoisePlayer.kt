package ro.pana.sleepybaby.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import android.os.Looper
import kotlinx.coroutines.*
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Multi-use noise player with smooth fade capabilities using Media3 ExoPlayer
 */
class NoisePlayer(private val context: Context) {

    private var exoPlayer: ExoPlayer? = null
    private var fadeJob: Job? = null
    private var loopCompletionDeferred: CompletableDeferred<Unit>? = null
    private var desiredLoopCount: Int = 0
    private var completedLoopCount: Int = 0
    private var playerListener: Player.Listener? = null
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loopFadeJob: Job? = null
    private var activeLoopCount: Int = 0
    private var pendingFadeOutMs: Long = 0L
    private var needsFadeOutSchedule = false
    private var trackDurationMs: Long = C.TIME_UNSET

    private var currentVolume = 0f
    private var targetVolume = 1f
    private val mediaAudioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.CONTENT_TYPE_MUSIC)
        .build()

    init {
        setupPlayer()
    }

    private fun handlePlaybackEnded() {
        val targetLoops = desiredLoopCount
        if (targetLoops <= 0) {
            Log.d("NoisePlayer", "Playback ended (single run)")
            resetLoopTracking()
            return
        }

        completedLoopCount++
        Log.d("NoisePlayer", "Completed loop $completedLoopCount/$targetLoops (ENDED)")

        if (completedLoopCount >= targetLoops) {
            loopCompletionDeferred?.takeIf { !it.isCompleted }?.complete(Unit)
            resetLoopTracking()
        } else {
            val player = exoPlayer ?: return
            needsFadeOutSchedule = pendingFadeOutMs > 0L
            activeLoopCount = 1
            if (needsFadeOutSchedule) {
                Log.d("NoisePlayer", "Preparing fade-out for next loop")
                maybeScheduleFadeOut()
            }
            if (targetVolume > 0f) {
                player.volume = targetVolume
                currentVolume = targetVolume
            }
            player.seekTo(0)
            player.play()
        }
    }

    private fun maybeScheduleFadeOut() {
        if (!needsFadeOutSchedule) {
            Log.v("NoisePlayer", "Fade-out not requested; skipping schedule")
            return
        }
        if (pendingFadeOutMs <= 0L) {
            Log.v("NoisePlayer", "Fade-out duration <= 0; skipping schedule")
            needsFadeOutSchedule = false
            return
        }
        val duration = trackDurationMs
        if (duration <= 0L || duration == C.TIME_UNSET) {
            Log.w("NoisePlayer", "Cannot schedule fade-out; track duration unknown ($duration)")
            return
        }
        val loops = activeLoopCount
        if (loops <= 0) {
            Log.v("NoisePlayer", "No active loops; skipping fade-out schedule")
            return
        }

        loopFadeJob?.cancel()
        val totalDuration = duration * loops
        val fadeDuration = pendingFadeOutMs.coerceAtMost(totalDuration)
        val delayMs = (totalDuration - fadeDuration).coerceAtLeast(0L)
        Log.d(
            "NoisePlayer",
            "Scheduling fade-out after ${delayMs}ms (fade=${fadeDuration}ms, loops=$loops, trackMs=$duration)"
        )

        loopFadeJob = coroutineScope.launch {
            if (delayMs > 0L) delay(delayMs)
            fadeOut(fadeDuration)
        }.also { job ->
            job.invokeOnCompletion { loopFadeJob = null }
        }

        needsFadeOutSchedule = false
    }

    private fun resetLoopTracking() {
        loopCompletionDeferred?.takeIf { !it.isCompleted }?.cancel()
        loopFadeJob?.cancel()
        loopFadeJob = null
        desiredLoopCount = 0
        completedLoopCount = 0
        loopCompletionDeferred = null
        activeLoopCount = 0
        pendingFadeOutMs = 0L
        needsFadeOutSchedule = false
        trackDurationMs = C.TIME_UNSET
        exoPlayer?.repeatMode = Player.REPEAT_MODE_OFF
    }

    private fun setupPlayer() {
        exoPlayer = ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            volume = 0f
            setAudioAttributes(mediaAudioAttributes, true)
            setHandleAudioBecomingNoisy(true)
        }.also { player ->
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            val duration = this@NoisePlayer.exoPlayer?.duration ?: C.TIME_UNSET
                            if (duration != C.TIME_UNSET && duration > 0) {
                                trackDurationMs = duration
                                Log.d("NoisePlayer", "Player ready with duration=${duration}ms; evaluating fade-out")
                                maybeScheduleFadeOut()
                            } else {
                                Log.w("NoisePlayer", "Player ready but duration unavailable ($duration)")
                            }
                        }
                        Player.STATE_ENDED -> handlePlaybackEnded()
                    }
                }
            }
            playerListener = listener
            player.addListener(listener)
        }
    }

    /**
     * Play a track a fixed number of times.
     */
    suspend fun playLoops(
        trackUri: String,
        loopCount: Int,
        targetVolume: Float = 1f,
        fadeInMs: Long = 0L,
        fadeOutMs: Long = 0L
    ) = withContext(Dispatchers.Main) {
        val player = exoPlayer ?: return@withContext

        if (loopCount <= 0) {
            Log.w("NoisePlayer", "Requested loop count $loopCount; nothing to play")
            return@withContext
        }

        resetLoopTracking()
        desiredLoopCount = loopCount
        loopCompletionDeferred = CompletableDeferred()
        pendingFadeOutMs = fadeOutMs.coerceAtLeast(0L)
        needsFadeOutSchedule = pendingFadeOutMs > 0L

        try {
            this@NoisePlayer.targetVolume = targetVolume.coerceIn(0f, 1f)

            if (trackUri.startsWith("asset:///")) {
                ensureAssetExists(trackUri.removePrefix("asset:///"))
            } else if (trackUri.startsWith("file://")) {
                ensureFileExists(Uri.parse(trackUri))
            }
            val uri = Uri.parse(trackUri)

            val mediaItem = MediaItem.fromUri(uri)
            player.setMediaItem(mediaItem)
            player.repeatMode = Player.REPEAT_MODE_OFF
            activeLoopCount = 1
            player.prepare()
            player.play()
            maybeScheduleFadeOut()

            if (fadeInMs > 0) {
                fadeIn(fadeInMs)
            } else {
                player.volume = this@NoisePlayer.targetVolume
                currentVolume = this@NoisePlayer.targetVolume
            }

            Log.d(
                "NoisePlayer",
                "Started looped playback ($loopCount loops): $trackUri"
            )

            loopCompletionDeferred?.await()
        } catch (e: Exception) {
            loopCompletionDeferred?.cancel()
            resetLoopTracking()
            Log.e("NoisePlayer", "Failed to play loops for track: $trackUri", e)
        }
    }

    private fun ensureAssetExists(assetPath: String) {
        try {
            context.assets.open(assetPath).use { /* no-op */ }
        } catch (e: FileNotFoundException) {
            throw e
        } catch (e: IOException) {
            throw FileNotFoundException("Failed to load asset $assetPath: ${e.message}")
        }
    }

    private fun ensureFileExists(uri: Uri) {
        val path = uri.path ?: throw FileNotFoundException("Invalid file uri: $uri")
        val file = File(path)
        if (!file.exists()) {
            throw FileNotFoundException("File not found: $path")
        }
    }

    /**
     * Fade in to target volume
     * @param durationMs Fade duration in milliseconds
     */
    private fun fadeIn(durationMs: Long) {
        fadeJob?.cancel()
        fadeJob = coroutineScope.launch {
            val player = exoPlayer ?: return@launch
            val steps = (durationMs / 50).coerceAtLeast(1) // Update every 50ms
            val volumeStep = targetVolume / steps

            currentVolume = 0f
            player.volume = 0f

            repeat(steps.toInt()) {
                if (!isActive) return@launch

                currentVolume += volumeStep
                player.volume = currentVolume.coerceIn(0f, targetVolume)
                delay(50)
            }

            // Ensure we reach exact target
            player.volume = targetVolume
            currentVolume = targetVolume
        }.also { job -> job.invokeOnCompletion { fadeJob = null } }
    }

    /**
     * Fade out to silence
     * @param durationMs Fade duration in milliseconds
     */
    suspend fun fadeOut(durationMs: Long) = withContext(Dispatchers.Main) {
        fadeJob?.cancel()
        val player = exoPlayer ?: return@withContext

        if (durationMs <= 0L) {
            Log.d("NoisePlayer", "Fade-out requested with <=0 duration; muting immediately")
            player.volume = 0f
            currentVolume = 0f
            return@withContext
        }

        val steps = (durationMs / 50L).coerceAtLeast(1L) // Update every 50ms
        val startVolume = currentVolume.coerceIn(0f, 1f)
        val volumeStep = startVolume / steps
        Log.d(
            "NoisePlayer",
            "Starting fade-out over ${durationMs}ms (steps=$steps, startVolume=${"%.3f".format(startVolume)})"
        )

        repeat(steps.toInt()) {
            if (!isActive) return@withContext

            currentVolume = (currentVolume - volumeStep).coerceAtLeast(0f)
            player.volume = currentVolume
            delay(50)
        }

        player.volume = 0f
        currentVolume = 0f
        Log.d("NoisePlayer", "Fade-out complete; volume muted")
    }

    /**
     * Stop playback immediately
     */
    fun stop() {
        fadeJob?.cancel()
        runOnPlayerThread {
            loopCompletionDeferred?.takeIf { !it.isCompleted }?.complete(Unit)
            resetLoopTracking()
            exoPlayer?.stop()
            currentVolume = 0f
            Log.d("NoisePlayer", "Playback stopped")
        }
    }

    /**
     * Set volume immediately (no fade)
     */
    fun setVolume(volume: Float) {
        val clampedVolume = volume.coerceIn(0f, 1f)
        exoPlayer?.volume = clampedVolume
        currentVolume = clampedVolume
        targetVolume = clampedVolume
    }

    /**
     * Release resources
     */
    fun release() {
        fadeJob?.cancel()
        val releaseJob = runOnPlayerThread {
            loopCompletionDeferred?.takeIf { !it.isCompleted }?.complete(Unit)
            resetLoopTracking()
            exoPlayer?.let { player ->
                playerListener?.let { player.removeListener(it) }
            }
            exoPlayer?.release()
            exoPlayer = null
            Log.d("NoisePlayer", "Resources released")
        }
        if (releaseJob != null) {
            runBlocking { releaseJob.join() }
        }
        coroutineScope.cancel()
    }

    private fun runOnPlayerThread(block: () -> Unit): Job? {
        return if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
            null
        } else {
            coroutineScope.launch {
                block()
            }
        }
    }
}
