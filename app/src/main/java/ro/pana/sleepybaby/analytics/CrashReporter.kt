package ro.pana.sleepybaby.analytics

import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import ro.pana.sleepybaby.BuildConfig

/**
 * Centralized Crashlytics access so collection can be disabled per build.
 */
object CrashReporter {

    fun recordException(throwable: Throwable) {
        if (!BuildConfig.ENABLE_CRASHLYTICS) return
        Firebase.crashlytics.recordException(throwable)
    }

    fun log(message: String) {
        if (!BuildConfig.ENABLE_CRASHLYTICS) return
        Firebase.crashlytics.log(message)
    }
}
