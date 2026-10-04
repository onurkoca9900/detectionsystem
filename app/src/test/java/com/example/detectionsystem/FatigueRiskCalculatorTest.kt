package com.example.detectionsystem

import com.example.detectionsystem.vision.FatigueRiskCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class FatigueRiskCalculatorTest {
    @Test
    fun safeDriverStartsAtBaselineRisk() {
        assertEquals(
            5,
            FatigueRiskCalculator.calculateTarget(0L, 0, 0, false, false, false)
        )
    }

    @Test
    fun activeFatigueCuesRaiseRiskAndClosedEyesAreCritical() {
        val yawningRisk = FatigueRiskCalculator.calculateTarget(0L, 0, 0, false, true, false)
        val distractedRisk = FatigueRiskCalculator.calculateTarget(0L, 0, 0, false, false, true)
        val dangerRisk = FatigueRiskCalculator.calculateTarget(0L, 0, 0, true, false, false)

        assertEquals(37, yawningRisk)
        assertEquals(30, distractedRisk)
        assertEquals(75, dangerRisk)
    }

    @Test
    fun driveHistoryAddsRiskAndIsCappedAtOneHundred() {
        val twoMinutes = FatigueRiskCalculator.calculateTarget(120L, 0, 0, false, false, false)
        val highHistory = FatigueRiskCalculator.calculateTarget(100_000L, 20, 10, true, true, true)

        assertEquals(6, twoMinutes)
        assertEquals(100, highHistory)
    }

    @Test
    fun negativeInputDoesNotLowerRiskBelowBaseline() {
        val risk = FatigueRiskCalculator.calculateTarget(-1L, -1, -1, false, false, false)

        assertEquals(5, risk)
    }
}
