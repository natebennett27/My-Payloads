package com.trio.today.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "trio_settings")

/**
 * The app's entire settings surface.
 *
 * Kept deliberately tiny. Every configuration screen is a procrastination trap
 * (§3, Stage 2), so anything that can be a sensible default is a default.
 */
class SettingsStore(private val context: Context) {

    val hasSeenReliabilitySetup: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SEEN_RELIABILITY_SETUP] ?: false }

    val soundEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SOUND] ?: true }

    val hapticsEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_HAPTICS] ?: true }

    /** Epoch day of the last completed rollover, so it runs exactly once per day. */
    val lastRolloverDay: Flow<Long> =
        context.dataStore.data.map { it[KEY_LAST_ROLLOVER_DAY] ?: 0L }

    suspend fun setSeenReliabilitySetup(seen: Boolean) = edit { it[KEY_SEEN_RELIABILITY_SETUP] = seen }

    suspend fun setSoundEnabled(enabled: Boolean) = edit { it[KEY_SOUND] = enabled }

    suspend fun setHapticsEnabled(enabled: Boolean) = edit { it[KEY_HAPTICS] = enabled }

    suspend fun setLastRolloverDay(epochDay: Long) = edit { it[KEY_LAST_ROLLOVER_DAY] = epochDay }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private companion object {
        val KEY_SEEN_RELIABILITY_SETUP = booleanPreferencesKey("seen_reliability_setup")
        val KEY_SOUND = booleanPreferencesKey("sound_enabled")
        val KEY_HAPTICS = booleanPreferencesKey("haptics_enabled")
        val KEY_LAST_ROLLOVER_DAY = longPreferencesKey("last_rollover_day")
    }
}
