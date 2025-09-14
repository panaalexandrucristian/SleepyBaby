package ro.pana.sleepybaby.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import ro.pana.sleepybaby.engine.AutomationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Define DataStore as a top-level singleton delegate to avoid multiple instances
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sleepy_baby_settings")

/**
 * Repository for persisting SleepyBaby settings using DataStore
 */
class SettingsRepository(private val context: Context) {
    private val appContext = context.applicationContext

    companion object {
        private val CRY_THRESHOLD_SECONDS = intPreferencesKey("cry_threshold_seconds")
        private val SILENCE_THRESHOLD_SECONDS = intPreferencesKey("silence_threshold_seconds")
        private val FADE_IN_MS = longPreferencesKey("fade_in_ms")
        private val FADE_OUT_MS = longPreferencesKey("fade_out_ms")
        private val TARGET_VOLUME = floatPreferencesKey("target_volume")
        private val TRACK_ID = stringPreferencesKey("track_id")
        private val ENABLED = booleanPreferencesKey("enabled")
        private val TUTORIAL_COMPLETED = booleanPreferencesKey("tutorial_completed")
    }

    /**
     * Flow of automation configuration
     */
    val automationConfig: Flow<AutomationConfig> = appContext.dataStore.data.map { preferences ->
        AutomationConfig(
            cryThresholdSeconds = preferences[CRY_THRESHOLD_SECONDS] ?: 3,
            silenceThresholdSeconds = preferences[SILENCE_THRESHOLD_SECONDS] ?: 10,
            fadeInMs = preferences[FADE_IN_MS] ?: 10000L,
            fadeOutMs = preferences[FADE_OUT_MS] ?: 5000L,
            targetVolume = preferences[TARGET_VOLUME] ?: 0.7f,
            trackId = preferences[TRACK_ID] ?: "asset:///shhh_loop.mp3"
        )
    }

    /**
     * Flow of enabled state
     */
    val isEnabled: Flow<Boolean> = appContext.dataStore.data.map { preferences ->
        preferences[ENABLED] ?: false
    }

    /**
     * Flow indicating whether the onboarding tutorial was completed
     */
    val tutorialCompleted: Flow<Boolean> = appContext.dataStore.data.map { preferences ->
        preferences[TUTORIAL_COMPLETED] ?: false
    }

    /**
     * Update cry threshold seconds
     */
    suspend fun updateCryThreshold(seconds: Int) {
        appContext.dataStore.edit { preferences ->
            preferences[CRY_THRESHOLD_SECONDS] = seconds
        }
    }

    /**
     * Update silence threshold seconds
     */
    suspend fun updateSilenceThreshold(seconds: Int) {
        appContext.dataStore.edit { preferences ->
            preferences[SILENCE_THRESHOLD_SECONDS] = seconds
        }
    }

    /**
     * Update target volume
     */
    suspend fun updateTargetVolume(volume: Float) {
        appContext.dataStore.edit { preferences ->
            preferences[TARGET_VOLUME] = volume.coerceIn(0f, 1f)
        }
    }

    /**
     * Update playback track id
     */
    suspend fun updateTrackId(trackId: String) {
        appContext.dataStore.edit { preferences ->
            preferences[TRACK_ID] = trackId
        }
    }

    /**
     * Update fade durations
     */
    suspend fun updateFadeDurations(fadeInMs: Long, fadeOutMs: Long) {
        appContext.dataStore.edit { preferences ->
            preferences[FADE_IN_MS] = fadeInMs
            preferences[FADE_OUT_MS] = fadeOutMs
        }
    }

    /**
     * Update enabled state
     */
    suspend fun updateEnabled(enabled: Boolean) {
        appContext.dataStore.edit { preferences ->
            preferences[ENABLED] = enabled
        }
    }

    /**
     * Update the tutorial completion flag
     */
    suspend fun setTutorialCompleted(completed: Boolean) {
        appContext.dataStore.edit { preferences ->
            preferences[TUTORIAL_COMPLETED] = completed
        }
    }

    /**
     * Update full automation config
     */
    suspend fun updateAutomationConfig(config: AutomationConfig) {
        appContext.dataStore.edit { preferences ->
            preferences[CRY_THRESHOLD_SECONDS] = config.cryThresholdSeconds
            preferences[SILENCE_THRESHOLD_SECONDS] = config.silenceThresholdSeconds
            preferences[FADE_IN_MS] = config.fadeInMs
            preferences[FADE_OUT_MS] = config.fadeOutMs
            preferences[TARGET_VOLUME] = config.targetVolume
            preferences[TRACK_ID] = config.trackId
        }
    }
}
