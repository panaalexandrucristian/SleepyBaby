package ro.pana.sleepybaby.analytics

import com.google.firebase.ktx.Firebase
import com.google.firebase.perf.ktx.performance

object PerformanceTracer {

    inline fun <T> trace(name: String, block: () -> T): T {
        val trace = Firebase.performance.newTrace(name)
        trace.start()
        return try {
            block()
        } finally {
            trace.stop()
        }
    }

    suspend fun <T> traceSuspend(name: String, block: suspend () -> T): T {
        val trace = Firebase.performance.newTrace(name)
        trace.start()
        return try {
            block()
        } finally {
            trace.stop()
        }
    }
}
