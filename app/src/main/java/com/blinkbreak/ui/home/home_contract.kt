package com.blinkbreak.ui.home

import com.blinkbreak.data.BreakSettings

data class HomeUiState(
    val intervalMinutes: Int = BreakSettings.DEFAULT_INTERVAL_MINUTES,
    val breakDurationSeconds: Int = BreakSettings.DEFAULT_BREAK_DURATION_SECONDS,
    val isEnabled: Boolean = false,
    val breaksTakenCount: Int = 0,
    val remainingMillis: Long = 0L,
    val hasScheduledBreak: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val ignoresBatteryOptimizations: Boolean = false,
    val canScheduleExactAlarms: Boolean = true,
    val hasNotificationPermission: Boolean = true
)

sealed interface HomeEvent {
    data class SetIntervalMinutes(val minutes: Int) : HomeEvent
    data class SetBreakDurationSeconds(val seconds: Int) : HomeEvent
    data class SetEnabled(val isEnabled: Boolean) : HomeEvent
    data object ResetToDefaults : HomeEvent
    data object RefreshPermissions : HomeEvent
}

sealed interface HomeEffect {
    data object StartTimerService : HomeEffect
    data object StopTimerService : HomeEffect
}
