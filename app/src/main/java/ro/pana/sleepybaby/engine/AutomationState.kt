package ro.pana.sleepybaby.engine

/**
 * States of the cry detection automation engine
 */
sealed class AutomationState {
    object Listening : AutomationState()
    data class CryingPending(val consecutiveCrySeconds: Int) : AutomationState()
    object Playing : AutomationState()
    data class FadingOut(val remainingMs: Long) : AutomationState()
    object Stopped : AutomationState()

    override fun toString(): String = when (this) {
        is Listening -> "Listening"
        is CryingPending -> "Crying Detected (${consecutiveCrySeconds}s)"
        is Playing -> "Playing"
        is FadingOut -> "Fading Out (${remainingMs}ms)"
        is Stopped -> "Stopped"
    }
}
