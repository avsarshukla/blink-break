package com.blinkbreak.util

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

data class PermissionState(
    val hasOverlayPermission: Boolean,
    val ignoresBatteryOptimizations: Boolean,
    val canScheduleExactAlarms: Boolean,
    val hasNotificationPermission: Boolean
)

class PermissionChecker(private val context: Context) {

    fun readState(): PermissionState = PermissionState(
        hasOverlayPermission = hasOverlayPermission(),
        ignoresBatteryOptimizations = ignoresBatteryOptimizations(),
        canScheduleExactAlarms = canScheduleExactAlarms(),
        hasNotificationPermission = hasNotificationPermission()
    )

    private fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(context)

    private fun ignoresBatteryOptimizations(): Boolean =
        context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)

    private fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        val result: Int = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        return result == PackageManager.PERMISSION_GRANTED
    }
}
