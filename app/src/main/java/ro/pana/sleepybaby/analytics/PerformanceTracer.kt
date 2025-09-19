package ro.pana.sleepybaby.analytics

import com.google.firebase.perf.ktx.performance
import com.google.firebase.ktx.Firebase
import ro.pana.sleepybaby.BuildConfig

object PerformanceTracer {

    inline fun <T> trace(name: String, block: () -> T): T {
        if (!BuildConfig.ENABLE_PERFORMANCE_MONITORING) return block()
        val trace = Firebase.performance.newTrace(name)
        trace.start()
        return try {
            block()
        } finally {
            trace.stop()
        }
    }

    suspend fun <T> traceSuspend(name: String, block: suspend () -> T): T {
        if (!BuildConfig.ENABLE_PERFORMANCE_MONITORING) return block()
        val trace = Firebase.performance.newTrace(name)
        trace.start()
        return try {
            block()
        } finally {
            trace.stop()
        }
    }
}
