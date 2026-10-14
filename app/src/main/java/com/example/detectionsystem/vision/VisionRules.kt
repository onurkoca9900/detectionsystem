package com.example.detectionsystem.vision

/** Thresholds used by the current face-landmark heuristic. */
object VisionThresholds {
    const val EYE_CLOSED_EAR = 0.21f
    const val HEAD_YAW_RATIO_MIN = 0.35f
    const val HEAD_YAW_RATIO_MAX = 2.85f
    const val EYE_CLOSED_DURATION_MS = 2_000L

    fun isEyeClosed(ear: Float): Boolean = ear < EYE_CLOSED_EAR

    fun isHeadDistracted(yawRatio: Float): Boolean =
        !yawRatio.isFinite() || yawRatio < HEAD_YAW_RATIO_MIN || yawRatio > HEAD_YAW_RATIO_MAX
}

data class LandmarkPoint(val x: Float, val y: Float)

object EyeAspectRatioCalculator {
    fun calculate(points: List<LandmarkPoint>): Float {
        require(points.size == 6) { "An eye requires six ordered landmarks." }
        if (points.any { !it.x.isFinite() || !it.y.isFinite() }) return 0f

        val vertical1 = distance(points[1], points[5])
        val vertical2 = distance(points[2], points[4])
        val horizontal = distance(points[0], points[3])
        if (horizontal <= MIN_HORIZONTAL_DISTANCE) return 0f
        return (vertical1 + vertical2) / (2f * horizontal)
    }

    private fun distance(first: LandmarkPoint, second: LandmarkPoint): Float {
        val dx = first.x - second.x
        val dy = first.y - second.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private const val MIN_HORIZONTAL_DISTANCE = 0.0001f
}

/** Requires one continuous closure interval; an open-eye sample clears the timer. */
class EyeClosureTracker(
    private val closedDurationMs: Long = VisionThresholds.EYE_CLOSED_DURATION_MS,
    private val maxSampleGapMs: Long = 1_000L
) {
    private var closedStartTimeMs: Long? = null
    private var lastSampleTimeMs: Long? = null

    fun update(ear: Float, timeMs: Long): Boolean {
        val previousSampleTime = lastSampleTimeMs
        if (previousSampleTime != null && timeMs - previousSampleTime !in 0L..maxSampleGapMs) reset()
        lastSampleTimeMs = timeMs
        if (!VisionThresholds.isEyeClosed(ear)) {
            reset()
            return false
        }

        val startTime = closedStartTimeMs ?: timeMs.also { closedStartTimeMs = it }
        return timeMs - startTime >= closedDurationMs
    }

    fun reset() {
        closedStartTimeMs = null
        lastSampleTimeMs = null
    }
}
