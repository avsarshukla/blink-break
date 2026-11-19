package com.blinkbreak.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.blinkbreak.BlinkBreakApplication
import com.blinkbreak.data.BreakSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action: String? = intent.action
        val isHandled: Boolean = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == ACTION_QUICKBOOT_POWERON
        if (!isHandled) return
        val pendingResult: PendingResult = goAsync()
        val application: BlinkBreakApplication = context.applicationContext as BlinkBreakApplication
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings: BreakSettings = application.container.settingsRepository.readSettings()
                if (settings.isEnabled) BreakTimerService.start(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val ACTION_QUICKBOOT_POWERON: String = "android.intent.action.QUICKBOOT_POWERON"
    }
}
