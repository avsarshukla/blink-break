package com.blinkbreak.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.breakSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "break_settings")

private object PreferenceKeys {
    val INTERVAL_MINUTES: Preferences.Key<Int> = intPreferencesKey("interval_minutes")
    val BREAK_DURATION_SECONDS: Preferences.Key<Int> = intPreferencesKey("break_duration_seconds")
    val IS_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("is_enabled")
    val BREAKS_TAKEN_COUNT: Preferences.Key<Int> = intPreferencesKey("breaks_taken_count")
    val NEXT_BREAK_AT_EPOCH_MILLIS: Preferences.Key<Long> = longPreferencesKey("next_break_at_epoch_millis")
}

class DataStoreBreakSettingsRepository(
    private val context: Context
) : BreakSettingsRepository {

    override val settings: Flow<BreakSettings> = context.breakSettingsDataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences -> preferences.toBreakSettings() }

    override suspend fun readSettings(): BreakSettings = settings.first()

    override suspend fun setIntervalMinutes(minutes: Int) {
        val safeMinutes: Int = minutes.coerceIn(
            BreakSettings.MIN_INTERVAL_MINUTES,
            BreakSettings.MAX_INTERVAL_MINUTES
        )
        context.breakSettingsDataStore.edit { preferences ->
            preferences[PreferenceKeys.INTERVAL_MINUTES] = safeMinutes
        }
    }

    override suspend fun setBreakDurationSeconds(seconds: Int) {
        val safeSeconds: Int = seconds.coerceIn(
            BreakSettings.MIN_BREAK_DURATION_SECONDS,
            BreakSettings.MAX_BREAK_DURATION_SECONDS
        )
        context.breakSettingsDataStore.edit { preferences ->
            preferences[PreferenceKeys.BREAK_DURATION_SECONDS] = safeSeconds
        }
    }

    override suspend fun setEnabled(isEnabled: Boolean) {
        context.breakSettingsDataStore.edit { preferences ->
            preferences[PreferenceKeys.IS_ENABLED] = isEnabled
            if (!isEnabled) {
                preferences[PreferenceKeys.NEXT_BREAK_AT_EPOCH_MILLIS] = 0L
            }
        }
    }

    override suspend fun incrementBreaksTakenCount() {
        context.breakSettingsDataStore.edit { preferences ->
            val currentCount: Int = preferences[PreferenceKeys.BREAKS_TAKEN_COUNT] ?: 0
            preferences[PreferenceKeys.BREAKS_TAKEN_COUNT] = currentCount + 1
        }
    }

    override suspend fun setNextBreakAtEpochMillis(epochMillis: Long) {
        context.breakSettingsDataStore.edit { preferences ->
            preferences[PreferenceKeys.NEXT_BREAK_AT_EPOCH_MILLIS] = epochMillis
        }
    }

    override suspend fun resetToDefaults() {
        context.breakSettingsDataStore.edit { preferences ->
            preferences[PreferenceKeys.INTERVAL_MINUTES] = BreakSettings.DEFAULT_INTERVAL_MINUTES
            preferences[PreferenceKeys.BREAK_DURATION_SECONDS] = BreakSettings.DEFAULT_BREAK_DURATION_SECONDS
        }
    }
}

private fun Preferences.toBreakSettings(): BreakSettings = BreakSettings(
    intervalMinutes = this[PreferenceKeys.INTERVAL_MINUTES] ?: BreakSettings.DEFAULT_INTERVAL_MINUTES,
    breakDurationSeconds = this[PreferenceKeys.BREAK_DURATION_SECONDS] ?: BreakSettings.DEFAULT_BREAK_DURATION_SECONDS,
    isEnabled = this[PreferenceKeys.IS_ENABLED] ?: false,
    breaksTakenCount = this[PreferenceKeys.BREAKS_TAKEN_COUNT] ?: 0,
    nextBreakAtEpochMillis = this[PreferenceKeys.NEXT_BREAK_AT_EPOCH_MILLIS] ?: 0L
)
