package com.sleepybaby.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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

    private fun setupPlayer() {
        exoPlayer = ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            setAudioAttributes(audioAttributes, true)
            setHandleAudioBecomingNoisy(true)
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
    suspend fun fadeIn(durationMs: Long) {
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
    suspend fun fadeOut(durationMs: Long) {
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
        exoPlayer?.stop()
        currentVolume = 0f
        Log.d("NoisePlayer", "Playback stopped")
    }

    /**
     * Pause playback
     */
    fun pause() {
        exoPlayer?.pause()
        Log.d("NoisePlayer", "Playback paused")
    }

    /**
     * Resume playback
     */
    fun resume() {
        exoPlayer?.play()
        Log.d("NoisePlayer", "Playback resumed")
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
        exoPlayer?.release()
        exoPlayer = null
        coroutineScope.cancel()
        Log.d("NoisePlayer", "Resources released")
    }
}
