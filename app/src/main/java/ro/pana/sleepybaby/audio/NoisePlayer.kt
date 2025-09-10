package ro.pana.sleepybaby.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import android.os.Looper
import kotlinx.coroutines.*
import android.media.AudioAttributes
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

    private var currentVolume = 0f
    private var targetVolume = 1f
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    init {
        setupPlayer()
    }

    private fun handlePlaybackEnded() {
        val targetLoops = desiredLoopCount
        if (targetLoops <= 0) {
            return
        }
        completedLoopCount++
        Log.d("NoisePlayer", "Completed loop $completedLoopCount/$targetLoops")
        val player = exoPlayer ?: return
        if (completedLoopCount < targetLoops) {
            player.seekTo(0)
            player.play()
        } else {
            loopCompletionDeferred?.takeIf { !it.isCompleted }?.complete(Unit)
            resetLoopTracking()
        }
    }

    private fun resetLoopTracking() {
        loopCompletionDeferred?.takeIf { !it.isCompleted }?.cancel()
        desiredLoopCount = 0
        completedLoopCount = 0
        loopCompletionDeferred = null
    }

    private fun setupPlayer() {
        exoPlayer = ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            volume = 0f
            setAudioAttributes(audioAttributes, true)
            setHandleAudioBecomingNoisy(true)
        }.also { player ->
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        handlePlaybackEnded()
                    }
                }
            }
            playerListener = listener
            player.addListener(listener)
        }
    }

    /**
     * Play a track with fade-in
     * @param trackUri URI of the track (e.g., "asset:///shhh_loop.mp3")
     * @param targetVolume Target volume (0.0 - 1.0)
     * @param fadeInMs Fade-in duration in milliseconds
     */
    suspend fun play(
        trackUri: String,
        targetVolume: Float = 1f,
        fadeInMs: Long = 0L
    ) = withContext(Dispatchers.Main) {
        resetLoopTracking()
        val player = exoPlayer ?: return@withContext

        try {
            this@NoisePlayer.targetVolume = targetVolume.coerceIn(0f, 1f)

            // Convert asset URI if needed
            if (trackUri.startsWith("asset:///")) {
                ensureAssetExists(trackUri.removePrefix("asset:///"))
            } else if (trackUri.startsWith("file://")) {
                ensureFileExists(Uri.parse(trackUri))
            }
            val uri = Uri.parse(trackUri)

            val mediaItem = MediaItem.fromUri(uri)
            player.setMediaItem(mediaItem)
            player.repeatMode = Player.REPEAT_MODE_OFF
            player.prepare()
            player.play()

            if (fadeInMs > 0) {
                fadeIn(fadeInMs)
            } else {
                player.volume = this@NoisePlayer.targetVolume
                currentVolume = this@NoisePlayer.targetVolume
            }

            Log.d("NoisePlayer", "Started playing: $trackUri")
        } catch (e: Exception) {
            Log.e("NoisePlayer", "Failed to play track: $trackUri", e)
        }
    }

    /**
     * Play a track a fixed number of times.
     */
    suspend fun playLoops(
        trackUri: String,
        loopCount: Int,
        targetVolume: Float = 1f,
        fadeInMs: Long = 0L
    ) = withContext(Dispatchers.Main) {
        val player = exoPlayer ?: return@withContext

        if (loopCount <= 0) {
            Log.w("NoisePlayer", "Requested loop count $loopCount; nothing to play")
            return@withContext
        }

        resetLoopTracking()
        desiredLoopCount = loopCount
        loopCompletionDeferred = CompletableDeferred()

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
            player.prepare()
            player.play()

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
        }
    }

    /**
     * Fade out to silence
     * @param durationMs Fade duration in milliseconds
     */
    fun fadeOut(durationMs: Long) {
        fadeJob?.cancel()
        fadeJob = coroutineScope.launch {
            val player = exoPlayer ?: return@launch
            val steps = (durationMs / 50).coerceAtLeast(1) // Update every 50ms
            val volumeStep = currentVolume / steps

            repeat(steps.toInt()) {
                if (!isActive) return@launch

                currentVolume -= volumeStep
                player.volume = currentVolume.coerceAtLeast(0f)
                delay(50)
            }

            // Ensure we reach silence
            player.volume = 0f
            currentVolume = 0f
        }
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
     * Pause playback
     */
    fun pause() {
        runOnPlayerThread {
            exoPlayer?.pause()
            Log.d("NoisePlayer", "Playback paused")
        }
    }

    /**
     * Resume playback
     */
    fun resume() {
        runOnPlayerThread {
            exoPlayer?.play()
            Log.d("NoisePlayer", "Playback resumed")
        }
    }

    /**
     * Check if currently playing
     */
    val isPlaying: Boolean
        get() = exoPlayer?.isPlaying == true

    /**
     * Get current volume
     */
    val volume: Float
        get() = currentVolume

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
