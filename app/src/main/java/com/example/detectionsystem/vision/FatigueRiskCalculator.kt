package com.example.detectionsystem.vision

/** Pure scoring logic so the dashboard score can be tested independently from Android. */
object FatigueRiskCalculator {
    private const val BASE_RISK = 5
    private const val SECONDS_PER_DRIVING_RISK_POINT = 120L
    private const val MAX_DRIVING_TIME_RISK = 30
    private const val RISK_PER_YAWN = 4
    private const val MAX_YAWN_HISTORY_RISK = 20
    private const val RISK_PER_SLEEP_WARNING = 10
    private const val MAX_SLEEP_HISTORY_RISK = 30
    private const val DANGER_CUE_RISK = 70
    private const val YAWNING_CUE_RISK = 32
    private const val DISTRACTION_CUE_RISK = 25

    fun calculateTarget(
        drivingSeconds: Long,
        yawnCount: Int,
        sleepWarningCount: Int,
        isDanger: Boolean,
        isYawning: Boolean,
        isDistracted: Boolean
    ): Int {
        val drivingTimeRisk = (drivingSeconds.coerceAtLeast(0L) / SECONDS_PER_DRIVING_RISK_POINT)
            .toInt()
            .coerceAtMost(MAX_DRIVING_TIME_RISK)
        val yawnHistoryRisk = (yawnCount.coerceAtLeast(0) * RISK_PER_YAWN)
            .coerceAtMost(MAX_YAWN_HISTORY_RISK)
        val sleepHistoryRisk = (sleepWarningCount.coerceAtLeast(0) * RISK_PER_SLEEP_WARNING)
            .coerceAtMost(MAX_SLEEP_HISTORY_RISK)
        val liveCueRisk = if (isDanger) {
            DANGER_CUE_RISK
        } else {
            (if (isYawning) YAWNING_CUE_RISK else 0) +
                (if (isDistracted) DISTRACTION_CUE_RISK else 0)
        }

        return (BASE_RISK + drivingTimeRisk + yawnHistoryRisk + sleepHistoryRisk + liveCueRisk)
            .coerceIn(0, 100)
    }
}
