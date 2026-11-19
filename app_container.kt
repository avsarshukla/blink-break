package com.blinkbreak

import android.content.Context
import com.blinkbreak.core.SystemTimeProvider
import com.blinkbreak.core.TimeProvider
import com.blinkbreak.data.BreakSettingsRepository
import com.blinkbreak.data.DataStoreBreakSettingsRepository

class AppContainer(context: Context) {
    private val applicationContext: Context = context.applicationContext
    val timeProvider: TimeProvider = SystemTimeProvider()
    val settingsRepository: BreakSettingsRepository = DataStoreBreakSettingsRepository(applicationContext)
}
