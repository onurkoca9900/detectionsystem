package com.example.detectionsystem

import com.example.detectionsystem.data.DriveSessionMetrics
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveSessionMetricsTest {
    @Test
    fun clearedMetricsStartANewSessionWithoutPreviousCounts() {
        val previousDrive = DriveSessionMetrics(durationSeconds = 240L, yawnCount = 2, sleepWarningCount = 1)

        val newDrive = previousDrive.cleared()

        assertFalse(newDrive.hasActivity)
        assertTrue(newDrive.durationSeconds == 0L)
        assertTrue(newDrive.yawnCount == 0)
        assertTrue(newDrive.sleepWarningCount == 0)
    }
}
