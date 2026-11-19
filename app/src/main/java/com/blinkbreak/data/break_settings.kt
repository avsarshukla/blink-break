package com.blinkbreak.data

data class BreakSettings(
    val intervalMinutes: Int,
    val breakDurationSeconds: Int,
    val isEnabled: Boolean,
    val breaksTakenCount: Int,
    val nextBreakAtEpochMillis: Long
) {
    val intervalMillis: Long get() = intervalMinutes.toLong() * MILLIS_PER_MINUTE
    val breakDurationMillis: Long get() = breakDurationSeconds.toLong() * MILLIS_PER_SECOND
    val hasScheduledBreak: Boolean get() = nextBreakAtEpochMillis > 0L

    companion object {
        const val MILLIS_PER_SECOND: Long = 1_000L
        const val MILLIS_PER_MINUTE: Long = 60_000L
        const val MIN_INTERVAL_MINUTES: Int = 1
        const val MAX_INTERVAL_MINUTES: Int = 60
        const val DEFAULT_INTERVAL_MINUTES: Int = 20
        const val MIN_BREAK_DURATION_SECONDS: Int = 20
        const val MAX_BREAK_DURATION_SECONDS: Int = 60
        const val DEFAULT_BREAK_DURATION_SECONDS: Int = 20

        val Default: BreakSettings = BreakSettings(
            intervalMinutes = DEFAULT_INTERVAL_MINUTES,
            breakDurationSeconds = DEFAULT_BREAK_DURATION_SECONDS,
            isEnabled = false,
            breaksTakenCount = 0,
            nextBreakAtEpochMillis = 0L
        )
    }
}
