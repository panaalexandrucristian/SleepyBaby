package ro.pana.sleepybaby.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import ro.pana.sleepybaby.domain.controller.DetectionServiceLauncher

/**
 * Android implementation that starts or stops the [SleepyBabyService]
 * using foreground service intents.
 */
class AndroidDetectionServiceLauncher(private val context: Context) : DetectionServiceLauncher {

    override fun start() {
        if (SleepyBabyService.isForegroundActive()) {
            return
        }
        val intent = Intent(context, SleepyBabyService::class.java).apply {
            action = SleepyBabyService.ACTION_START_DETECTION
        }
        ContextCompat.startForegroundService(context, intent)
    }

    override fun stop() {
        val intent = Intent(context, SleepyBabyService::class.java).apply {
            action = SleepyBabyService.ACTION_STOP_DETECTION
        }
        val serviceIsForeground = SleepyBabyService.isForegroundActive()
        val serviceCreated = SleepyBabyService.isServiceCreated()
        if (serviceIsForeground) {
            ContextCompat.startForegroundService(context, intent)
        } else if (serviceCreated) {
            context.startService(intent)
        } else {
            context.stopService(Intent(context, SleepyBabyService::class.java))
        }
    }
}
