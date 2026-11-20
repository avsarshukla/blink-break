package com.blinkbreak.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.blinkbreak.BlinkBreakApplication
import com.blinkbreak.R
import com.blinkbreak.core.TimeProvider
import com.blinkbreak.data.BreakSettings
import com.blinkbreak.data.BreakSettingsRepository
import com.blinkbreak.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BreakTimerService : Service() {

    private val serviceScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var timeProvider: TimeProvider
    private lateinit var settingsRepository: BreakSettingsRepository
    private lateinit var alarmScheduler: BreakAlarmScheduler
    private lateinit var overlayManager: BreakOverlayManager

    private val screenStateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> pauseTimer()
                Intent.ACTION_SCREEN_ON -> resumeTimer()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val container = (application as BlinkBreakApplication).container
        timeProvider = container.timeProvider
        settingsRepository = container.settingsRepository
        alarmScheduler = BreakAlarmScheduler(this, timeProvider)
        overlayManager = BreakOverlayManager(this, timeProvider, serviceScope)
        createNotificationChannel()
        startAsForegroundService()
        registerScreenStateReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> handleStopCommand()
            ACTION_SHOW_BREAK -> handleShowBreakCommand()
            ACTION_START, null -> handleStartCommand()
            else -> handleStartCommand()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterScreenStateReceiver()
        overlayManager.dismissSilently()
        alarmScheduler.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startAsForegroundService() {
        val foregroundServiceType: Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), foregroundServiceType)
    }

    private fun handleStartCommand() {
        serviceScope.launch {
            val settings: BreakSettings = settingsRepository.readSettings()
            if (settings.isEnabled) scheduleNextBreak() else stopServiceSafely()
        }
    }

    private fun handleStopCommand() {
        alarmScheduler.cancel()
        overlayManager.dismissSilently()
        serviceScope.launch { settingsRepository.setNextBreakAtEpochMillis(0L) }
        stopServiceSafely()
    }

    private fun handleShowBreakCommand() {
        if (overlayManager.isOverlayVisible()) return
        if (!isScreenInteractive()) return
        serviceScope.launch {
            val settings: BreakSettings = settingsRepository.readSettings()
            if (!settings.isEnabled) return@launch
            settingsRepository.setNextBreakAtEpochMillis(0L)
            overlayManager.showBreakOverlay(settings.breakDurationMillis) {
                serviceScope.launch { handleBreakFinished() }
            }
        }
    }

    private suspend fun handleBreakFinished() {
        settingsRepository.incrementBreaksTakenCount()
        val settings: BreakSettings = settingsRepository.readSettings()
        if (settings.isEnabled) scheduleNextBreak() else stopServiceSafely()
    }

    private suspend fun scheduleNextBreak() {
        val settings: BreakSettings = settingsRepository.readSettings()
        val nextBreakAtEpochMillis: Long = timeProvider.currentEpochMillis() + settings.intervalMillis
        settingsRepository.setNextBreakAtEpochMillis(nextBreakAtEpochMillis)
        alarmScheduler.scheduleNextBreak(settings.intervalMillis)
        updateNotification(settings)
    }

    private fun pauseTimer() {
        alarmScheduler.cancel()
        overlayManager.dismissSilently()
    }

    private fun resumeTimer() {
        serviceScope.launch {
            val settings: BreakSettings = settingsRepository.readSettings()
            if (settings.isEnabled) scheduleNextBreak()
        }
    }

    private fun stopServiceSafely() {
        alarmScheduler.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun isScreenInteractive(): Boolean =
        getSystemService(PowerManager::class.java).isInteractive

    private fun registerScreenStateReceiver() {
        val filter: IntentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(this, screenStateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun unregisterScreenStateReceiver() {
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (error: IllegalArgumentException) {
            // Receiver was never registered; nothing to unregister.
        }
    }

    private fun createNotificationChannel() {
        val channel: NotificationChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val contentIntent: PendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE_CONTENT_INTENT,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent: PendingIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_STOP_INTENT,
            Intent(this, BreakTimerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text_idle))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.notification_action_stop), stopIntent)
            .build()
    }

    private fun updateNotification(settings: BreakSettings) {
        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text_running, settings.intervalMinutes))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    REQUEST_CODE_CONTENT_INTENT,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_START: String = "com.blinkbreak.action.START"
        const val ACTION_STOP: String = "com.blinkbreak.action.STOP"
        const val ACTION_SHOW_BREAK: String = "com.blinkbreak.action.SHOW_BREAK"

        private const val NOTIFICATION_CHANNEL_ID: String = "break_timer_channel"
        private const val NOTIFICATION_ID: Int = 42
        private const val REQUEST_CODE_CONTENT_INTENT: Int = 2_001
        private const val REQUEST_CODE_STOP_INTENT: Int = 2_002

        fun start(context: Context) {
            val intent: Intent = Intent(context, BreakTimerService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent: Intent = Intent(context, BreakTimerService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }

        fun showBreak(context: Context) {
            val intent: Intent = Intent(context, BreakTimerService::class.java).setAction(ACTION_SHOW_BREAK)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
