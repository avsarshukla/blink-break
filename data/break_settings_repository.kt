package com.blinkbreak.data

import kotlinx.coroutines.flow.Flow

interface BreakSettingsRepository {
    val settings: Flow<BreakSettings>
    suspend fun readSettings(): BreakSettings
    suspend fun setIntervalMinutes(minutes: Int)
    suspend fun setBreakDurationSeconds(seconds: Int)
    suspend fun setEnabled(isEnabled: Boolean)
    suspend fun incrementBreaksTakenCount()
    suspend fun setNextBreakAtEpochMillis(epochMillis: Long)
    suspend fun resetToDefaults()
}
