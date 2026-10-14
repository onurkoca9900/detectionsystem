package com.example.detectionsystem

import com.example.detectionsystem.vision.EyeClosureTracker
import com.example.detectionsystem.vision.EyeAspectRatioCalculator
import com.example.detectionsystem.vision.LandmarkPoint
import com.example.detectionsystem.vision.VisionThresholds
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionRulesTest {
    @Test
    fun eyeClosureMustBeContinuousForTwoSeconds() {
        val tracker = EyeClosureTracker()

        assertFalse(tracker.update(0.20f, 1_000L))
        assertFalse(tracker.update(0.20f, 2_000L))
        assertFalse(tracker.update(0.20f, 2_999L))
        assertTrue(tracker.update(0.20f, 3_000L))
    }

    @Test
    fun openingEyesResetsTheClosureTimer() {
        val tracker = EyeClosureTracker()

        assertFalse(tracker.update(0.20f, 1_000L))
        assertFalse(tracker.update(VisionThresholds.EYE_CLOSED_EAR, 2_000L))
        assertFalse(tracker.update(0.20f, 3_500L))
        assertFalse(tracker.update(0.20f, 4_500L))
        assertTrue(tracker.update(0.20f, 5_500L))
    }

    @Test
    fun eyeThresholdAndHeadYawBoundariesAreExplicit() {
        assertFalse(VisionThresholds.isEyeClosed(VisionThresholds.EYE_CLOSED_EAR))
        assertTrue(VisionThresholds.isEyeClosed(VisionThresholds.EYE_CLOSED_EAR - 0.001f))
        assertFalse(VisionThresholds.isHeadDistracted(VisionThresholds.HEAD_YAW_RATIO_MIN))
        assertFalse(VisionThresholds.isHeadDistracted(VisionThresholds.HEAD_YAW_RATIO_MAX))
        assertTrue(VisionThresholds.isHeadDistracted(VisionThresholds.HEAD_YAW_RATIO_MIN - 0.001f))
        assertTrue(VisionThresholds.isHeadDistracted(VisionThresholds.HEAD_YAW_RATIO_MAX + 0.001f))
    }

    @Test
    fun aCameraPauseDoesNotCountAsObservedEyeClosure() {
        val tracker = EyeClosureTracker()
        assertFalse(tracker.update(0.20f, 1_000L))
        assertFalse(tracker.update(0.20f, 8_000L))
        assertFalse(tracker.update(0.20f, 9_000L))
        assertTrue(tracker.update(0.20f, 10_000L))
    }

    @Test
    fun eyeAspectRatioSeparatesOpenAndClosedLandmarkShapesAndIgnoresScale() {
        val openEye = listOf(
            LandmarkPoint(0f, 0.5f), LandmarkPoint(0.25f, 0.3f), LandmarkPoint(0.75f, 0.3f),
            LandmarkPoint(1f, 0.5f), LandmarkPoint(0.75f, 0.7f), LandmarkPoint(0.25f, 0.7f)
        )
        val closedEye = openEye.mapIndexed { index, point ->
            when (index) {
                1, 2 -> point.copy(y = 0.45f)
                4, 5 -> point.copy(y = 0.55f)
                else -> point
            }
        }

        val openEar = EyeAspectRatioCalculator.calculate(openEye)
        val closedEar = EyeAspectRatioCalculator.calculate(closedEye)
        val scaledOpenEye = openEye.map { point -> LandmarkPoint(point.x * 3f, point.y * 3f) }

        assertTrue(openEar > VisionThresholds.EYE_CLOSED_EAR)
        assertTrue(closedEar < VisionThresholds.EYE_CLOSED_EAR)
        assertTrue(kotlin.math.abs(openEar - EyeAspectRatioCalculator.calculate(scaledOpenEye)) < 0.0001f)
    }
}
