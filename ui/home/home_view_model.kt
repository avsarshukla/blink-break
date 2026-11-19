package com.blinkbreak.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.blinkbreak.BlinkBreakApplication
import com.blinkbreak.core.TimeProvider
import com.blinkbreak.data.BreakSettings
import com.blinkbreak.data.BreakSettingsRepository
import com.blinkbreak.util.PermissionChecker
import com.blinkbreak.util.PermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val settingsRepository: BreakSettingsRepository,
    private val permissionChecker: PermissionChecker,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val permissionState: MutableStateFlow<PermissionState> =
        MutableStateFlow(permissionChecker.readState())

    private val effectsFlow: MutableSharedFlow<HomeEffect> = MutableSharedFlow(extraBufferCapacity = 1)
    val effects: SharedFlow<HomeEffect> = effectsFlow.asSharedFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        settingsRepository.settings,
        permissionState,
        createTickerFlow()
    ) { settings: BreakSettings, permissions: PermissionState, _: Unit ->
        createUiState(settings, permissions)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = HomeUiState()
    )

    fun onEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.SetIntervalMinutes -> viewModelScope.launch {
                settingsRepository.setIntervalMinutes(event.minutes)
                emitStartEffectIfEnabled()
            }
            is HomeEvent.SetBreakDurationSeconds -> viewModelScope.launch {
                settingsRepository.setBreakDurationSeconds(event.seconds)
                emitStartEffectIfEnabled()
            }
            is HomeEvent.SetEnabled -> viewModelScope.launch {
                settingsRepository.setEnabled(event.isEnabled)
                val effect: HomeEffect =
                    if (event.isEnabled) HomeEffect.StartTimerService else HomeEffect.StopTimerService
                effectsFlow.emit(effect)
            }
            HomeEvent.ResetToDefaults -> viewModelScope.launch {
                settingsRepository.resetToDefaults()
                emitStartEffectIfEnabled()
            }
            HomeEvent.RefreshPermissions -> permissionState.value = permissionChecker.readState()
        }
    }

    private suspend fun emitStartEffectIfEnabled() {
        val settings: BreakSettings = settingsRepository.readSettings()
        if (settings.isEnabled) effectsFlow.emit(HomeEffect.StartTimerService)
    }

    private fun createUiState(settings: BreakSettings, permissions: PermissionState): HomeUiState =
        HomeUiState(
            intervalMinutes = settings.intervalMinutes,
            breakDurationSeconds = settings.breakDurationSeconds,
            isEnabled = settings.isEnabled,
            breaksTakenCount = settings.breaksTakenCount,
            remainingMillis = calculateRemainingMillis(settings),
            hasScheduledBreak = settings.hasScheduledBreak,
            hasOverlayPermission = permissions.hasOverlayPermission,
            ignoresBatteryOptimizations = permissions.ignoresBatteryOptimizations,
            canScheduleExactAlarms = permissions.canScheduleExactAlarms,
            hasNotificationPermission = permissions.hasNotificationPermission
        )

    private fun calculateRemainingMillis(settings: BreakSettings): Long {
        if (!settings.hasScheduledBreak) return 0L
        return (settings.nextBreakAtEpochMillis - timeProvider.currentEpochMillis()).coerceAtLeast(0L)
    }

    private fun createTickerFlow(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(TICK_INTERVAL_MILLIS)
        }
    }

    companion object {
        private const val TICK_INTERVAL_MILLIS: Long = 1_000L
        private const val SUBSCRIPTION_TIMEOUT_MILLIS: Long = 5_000L

        fun createFactory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val applicationContext: Context = context.applicationContext
                val application: BlinkBreakApplication = applicationContext as BlinkBreakApplication
                HomeViewModel(
                    settingsRepository = application.container.settingsRepository,
                    permissionChecker = PermissionChecker(applicationContext),
                    timeProvider = application.container.timeProvider
                )
            }
        }
    }
}
