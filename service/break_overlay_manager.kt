package com.blinkbreak.service

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.blinkbreak.R
import com.blinkbreak.core.TimeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BreakOverlayManager(
    private val context: Context,
    private val timeProvider: TimeProvider,
    private val coroutineScope: CoroutineScope
) {
    private val windowManager: WindowManager = context.getSystemService(WindowManager::class.java)
    private var overlayView: View? = null
    private var countdownJob: Job? = null
    private var isNotifyingListener: Boolean = true

    fun isOverlayVisible(): Boolean = overlayView != null

    fun showBreakOverlay(durationMillis: Long, onFinished: () -> Unit) {
        if (isOverlayVisible()) return
        if (!Settings.canDrawOverlays(context)) {
            onFinished()
            return
        }
        val view: View = createOverlayView() ?: run {
            onFinished()
            return
        }
        if (!attachOverlayView(view)) {
            onFinished()
            return
        }
        overlayView = view
        isNotifyingListener = true
        view.setOnClickListener { hideBreakOverlay() }
        countdownJob = coroutineScope.launch {
            try {
                runCountdown(view, durationMillis)
            } finally {
                removeOverlayView()
                if (isNotifyingListener) onFinished()
            }
        }
    }

    fun hideBreakOverlay() {
        countdownJob?.cancel()
    }

    fun dismissSilently() {
        isNotifyingListener = false
        countdownJob?.cancel()
        countdownJob = null
        removeOverlayView()
    }

    private suspend fun runCountdown(view: View, durationMillis: Long) {
        val endElapsedRealtimeMillis: Long = timeProvider.elapsedRealtimeMillis() + durationMillis
        val countdownTextView: TextView = view.findViewById(R.id.text_break_countdown)
        while (true) {
            val remainingMillis: Long = endElapsedRealtimeMillis - timeProvider.elapsedRealtimeMillis()
            if (remainingMillis <= 0L) break
            val remainingSeconds: Int = ((remainingMillis + 999L) / 1_000L).toInt()
            countdownTextView.text = context.getString(R.string.break_overlay_countdown, remainingSeconds)
            delay(COUNTDOWN_TICK_MILLIS)
        }
    }

    private fun createOverlayView(): View? = try {
        val themedContext: Context = ContextThemeWrapper(context, R.style.Theme_BlinkBreak)
        LayoutInflater.from(themedContext).inflate(R.layout.view_break_overlay, null)
    } catch (error: RuntimeException) {
        null
    }

    private fun attachOverlayView(view: View): Boolean = try {
        windowManager.addView(view, createLayoutParams())
        true
    } catch (error: RuntimeException) {
        false
    }

    private fun removeOverlayView() {
        val view: View = overlayView ?: return
        overlayView = null
        countdownJob = null
        if (!view.isAttachedToWindow) return
        try {
            windowManager.removeView(view)
        } catch (error: RuntimeException) {
            // The window was already detached by the system; nothing to clean up.
        }
    }

    private fun createLayoutParams(): WindowManager.LayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    )

    private companion object {
        const val COUNTDOWN_TICK_MILLIS: Long = 250L
    }
}
