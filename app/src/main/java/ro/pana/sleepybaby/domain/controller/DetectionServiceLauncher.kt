package ro.pana.sleepybaby.domain.controller

/**
 * Abstraction responsible for starting or stopping the foreground service
 * that hosts the cry detection engine.
 */
interface DetectionServiceLauncher {
    fun start()
    fun stop()
}
