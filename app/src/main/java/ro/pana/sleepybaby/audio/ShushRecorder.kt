package ro.pana.sleepybaby.audio

import android.media.MediaRecorder
import android.net.Uri
import android.util.Log
import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Records a 10 second shushing sample for playback.
 */
class ShushRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null

    private fun outputFile(): File = File(context.filesDir, "shush_sample.m4a")

    fun hasRecording(): Boolean = outputFile().exists()

    fun recordingUri(): Uri? = outputFile().takeIf { it.exists() }?.let { Uri.fromFile(it) }

    suspend fun record(durationMs: Long = DEFAULT_DURATION_MS): Uri? {
        return withContext(Dispatchers.Main) {
            stopInternal()

            val file = outputFile()
            if (file.exists()) {
                file.delete()
            }
            file.parentFile?.mkdirs()

            val recorder = createRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
                setOutputFile(file.absolutePath)
            }

            try {
                recorder.prepare()
                recorder.start()
                this@ShushRecorder.recorder = recorder
                withContext(Dispatchers.IO) {
                    delay(durationMs)
                }
            } catch (e: Exception) {
                Log.e("ShushRecorder", "Failed to record shush sample", e)
                stopInternal()
                return@withContext null
            }

            stopInternal()
            if (file.exists()) {
                Uri.fromFile(file)
            } else {
                null
            }
        }
    }

    fun stop() {
        stopInternal()
    }

    @Suppress("DEPRECATION")
    private fun createRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
    }

    private fun stopInternal() {
        try {
            recorder?.run {
                try {
                    stop()
                } catch (_: IllegalStateException) {
                    // Ignored
                }
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.w("ShushRecorder", "Failed to stop recorder cleanly", e)
        } finally {
            recorder = null
        }
    }

    companion object {
        private const val DEFAULT_DURATION_MS = 10_000L
    }
}
