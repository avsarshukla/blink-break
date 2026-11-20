package com.blinkbreak.ui.home

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.blinkbreak.R
import com.blinkbreak.databinding.FragmentHomeBinding
import com.blinkbreak.service.BreakTimerService
import com.google.android.material.slider.Slider
import kotlinx.coroutines.launch
import java.util.Locale

class HomeFragment : Fragment() {

    private var viewBinding: FragmentHomeBinding? = null
    private val binding: FragmentHomeBinding get() = requireNotNull(viewBinding)

    private val viewModel: HomeViewModel by viewModels {
        HomeViewModel.createFactory(requireContext())
    }

    private val notificationPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.onEvent(HomeEvent.RefreshPermissions)
        }

    private var isBindingState: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val fragmentBinding: FragmentHomeBinding = FragmentHomeBinding.inflate(inflater, container, false)
        viewBinding = fragmentBinding
        return fragmentBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupControls()
        setupPermissionButtons()
        observeUiState()
        observeEffects()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onEvent(HomeEvent.RefreshPermissions)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewBinding = null
    }

    private fun setupControls() {
        binding.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            if (isBindingState) return@setOnCheckedChangeListener
            viewModel.onEvent(HomeEvent.SetEnabled(isChecked))
        }
        binding.sliderInterval.addOnChangeListener { _, value, fromUser ->
            if (fromUser) binding.textIntervalValue.text =
                getString(R.string.label_interval_value, value.toInt())
        }
        binding.sliderInterval.addOnSliderTouchListener(createSliderTouchListener())
        binding.sliderBreakDuration.addOnChangeListener { _, value, fromUser ->
            if (fromUser) binding.textBreakDurationValue.text =
                getString(R.string.label_break_duration_value, value.toInt())
        }
        binding.sliderBreakDuration.addOnSliderTouchListener(createSliderTouchListener())
        binding.buttonResetDefaults.setOnClickListener {
            viewModel.onEvent(HomeEvent.ResetToDefaults)
        }
    }

    private fun createSliderTouchListener(): Slider.OnSliderTouchListener =
        object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) = Unit
            override fun onStopTrackingTouch(slider: Slider) {
                when (slider.id) {
                    R.id.slider_interval -> viewModel.onEvent(
                        HomeEvent.SetIntervalMinutes(slider.value.toInt())
                    )
                    R.id.slider_break_duration -> viewModel.onEvent(
                        HomeEvent.SetBreakDurationSeconds(slider.value.toInt())
                    )
                }
            }
        }

    private fun setupPermissionButtons() {
        binding.buttonOverlayPermission.setOnClickListener {
            openSettingsIntent(settingsIntent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }
        binding.buttonBatteryOptimization.setOnClickListener {
            openSettingsIntent(settingsIntent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS))
        }
        binding.buttonExactAlarms.setOnClickListener {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@setOnClickListener
            openSettingsIntent(settingsIntent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
        }
        binding.buttonNotifications.setOnClickListener {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@setOnClickListener
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun settingsIntent(action: String): Intent = Intent(
        action,
        Uri.parse("package:${requireContext().packageName}")
    )

    private fun openSettingsIntent(intent: Intent) {
        try {
            startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun observeEffects() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.effects.collect { effect -> handleEffect(effect) }
            }
        }
    }

    private fun handleEffect(effect: HomeEffect) {
        when (effect) {
            HomeEffect.StartTimerService -> BreakTimerService.start(requireContext())
            HomeEffect.StopTimerService -> BreakTimerService.stop(requireContext())
        }
    }

    private fun render(state: HomeUiState) {
        isBindingState = true
        binding.switchEnabled.isChecked = state.isEnabled
        isBindingState = false
        binding.sliderInterval.value = state.intervalMinutes.toFloat()
        binding.sliderBreakDuration.value = state.breakDurationSeconds.toFloat()
        binding.textIntervalValue.text = getString(R.string.label_interval_value, state.intervalMinutes)
        binding.textBreakDurationValue.text =
            getString(R.string.label_break_duration_value, state.breakDurationSeconds)
        binding.textBreaksTaken.text = getString(R.string.label_breaks_taken, state.breaksTakenCount)
        binding.textNextBreak.text = if (state.hasScheduledBreak && state.isEnabled) {
            getString(R.string.label_next_break, formatDuration(state.remainingMillis))
        } else {
            getString(R.string.label_next_break_idle)
        }
        renderPermissionButtons(state)
    }

    private fun renderPermissionButtons(state: HomeUiState) {
        binding.buttonOverlayPermission.isEnabled = !state.hasOverlayPermission
        binding.buttonOverlayPermission.setText(
            if (state.hasOverlayPermission) R.string.button_overlay_done else R.string.button_overlay
        )
        binding.buttonBatteryOptimization.isEnabled = !state.ignoresBatteryOptimizations
        binding.buttonBatteryOptimization.setText(
            if (state.ignoresBatteryOptimizations) R.string.button_battery_done else R.string.button_battery
        )
        binding.buttonExactAlarms.isEnabled = !state.canScheduleExactAlarms
        binding.buttonExactAlarms.setText(
            if (state.canScheduleExactAlarms) R.string.button_exact_alarm_done else R.string.button_exact_alarm
        )
        binding.buttonNotifications.isEnabled = !state.hasNotificationPermission
        binding.buttonNotifications.setText(
            if (state.hasNotificationPermission) R.string.button_notifications_done else R.string.button_notifications
        )
    }

    private fun formatDuration(millis: Long): String {
        val totalSeconds: Long = millis / 1_000L
        val minutes: Long = totalSeconds / 60L
        val seconds: Long = totalSeconds % 60L
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
