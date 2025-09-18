package ro.pana.sleepybaby.analytics

import com.google.firebase.analytics.ktx.logEvent
import com.google.firebase.ktx.Firebase
import com.google.firebase.analytics.ktx.analytics

/**
 * Lightweight wrapper over Firebase Analytics that keeps event logging centralized.
 */
object AnalyticsLogger {

    fun logEvent(name: String, params: Map<String, Any?> = emptyMap()) {
        Firebase.analytics.logEvent(name) {
            params.forEach { (key, value) ->
                when (value) {
                    null -> Unit
                    is String -> param(key, value)
                    is Int -> param(key, value.toLong())
                    is Long -> param(key, value)
                    is Double -> param(key, value)
                    is Float -> param(key, value.toDouble())
                    is Boolean -> param(key, if (value) 1L else 0L)
                    else -> param(key, value.toString())
                }
            }
        }
    }
}

object AnalyticsEvents {
    const val MONITOR_START = "monitor_start"
    const val MONITOR_STOP = "monitor_stop"
    const val SHUSH_RECORD_SUCCESS = "shush_record_success"
    const val SHUSH_RECORD_FAILURE = "shush_record_failure"
    const val SHUSH_PREVIEW_PLAY = "shush_preview_play"
    const val SHUSH_PREVIEW_STOP = "shush_preview_stop"
    const val TUTORIAL_SKIP = "tutorial_skip"
    const val TUTORIAL_COMPLETE = "tutorial_complete"
    const val TUTORIAL_RESTART = "tutorial_restart"
    const val BRIGHTNESS_CHANGED = "brightness_changed"
    const val AUDIO_PERMISSION_GRANTED = "audio_permission_granted"
    const val AUDIO_PERMISSION_DENIED = "audio_permission_denied"
    const val AUDIO_PERMISSION_PROMPT = "audio_permission_prompt"
    const val NOTIFICATION_PERMISSION_PROMPT = "notification_permission_prompt"
    const val NOTIFICATION_PERMISSION_GRANTED = "notification_permission_granted"
    const val NOTIFICATION_PERMISSION_DENIED = "notification_permission_denied"
}
