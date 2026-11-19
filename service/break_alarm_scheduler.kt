package com.blinkbreak.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.blinkbreak.core.TimeProvider

class BreakAlarmScheduler(
    private val context: Context,
    private val timeProvider: TimeProvider
) {
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    fun scheduleNextBreak(intervalMillis: Long) {
        val triggerAtElapsedRealtimeMillis: Long = timeProvider.elapsedRealtimeMillis() + intervalMillis
        scheduleAt(triggerAtElapsedRealtimeMillis)
    }

    fun scheduleAt(triggerAtElapsedRealtimeMillis: Long) {
        val pendingIntent: PendingIntent = createPendingIntent()
        if (canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtElapsedRealtimeMillis,
                pendingIntent
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtElapsedRealtimeMillis,
                pendingIntent
            )
        }
    }

    fun cancel() {
        alarmManager.cancel(createPendingIntent())
    }

    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun createPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE_BREAK_ALARM,
        Intent(context, BreakAlarmReceiver::class.java).setAction(BreakAlarmReceiver.ACTION_TRIGGER_BREAK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private companion object {
        const val REQUEST_CODE_BREAK_ALARM: Int = 1_001
    }
}
