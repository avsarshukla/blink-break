package com.blinkbreak.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BreakAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TRIGGER_BREAK) return
        BreakTimerService.showBreak(context)
    }

    companion object {
        const val ACTION_TRIGGER_BREAK: String = "com.blinkbreak.action.TRIGGER_BREAK"
    }
}
