package com.blinkbreak.core

import android.os.SystemClock

interface TimeProvider {
    fun currentEpochMillis(): Long
    fun elapsedRealtimeMillis(): Long
}

class SystemTimeProvider : TimeProvider {
    override fun currentEpochMillis(): Long = System.currentTimeMillis()
    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
}
