package com.sleepybaby.engine

/**
 * Configuration for the cry detection automation engine
 */
data class AutomationConfig(
    val cryThresholdSeconds: Int = 3,
    val silenceThresholdSeconds: Int = 10,
    val fadeInMs: Long = 10000,
    val fadeOutMs: Long = 5000,
    val targetVolume: Float = 0.7f,
    val trackId: String = "asset:///shhh_loop.mp3",
    val samplePeriodMs: Long = 1000
)