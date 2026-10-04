package com.example.detectionsystem.data

/** Mutable-per-session counters represented immutably for safe UI observation. */
data class DriveSessionMetrics(
    val durationSeconds: Long = 0L,
    val yawnCount: Int = 0,
    val sleepWarningCount: Int = 0
) {
    val hasActivity: Boolean
        get() = durationSeconds > 0L || yawnCount > 0 || sleepWarningCount > 0

    fun cleared(): DriveSessionMetrics = DriveSessionMetrics()
}
