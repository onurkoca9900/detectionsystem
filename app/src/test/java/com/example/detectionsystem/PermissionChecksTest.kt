package com.example.detectionsystem

import com.example.detectionsystem.hardware.PermissionChecks
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionChecksTest {
    private val required = arrayOf("scan", "connect")

    @Test
    fun grantingOnlyTheMissingPermissionKeepsAnExistingGrant() {
        assertTrue(PermissionChecks.areAllGranted(required, mapOf("scan" to true)) { it == "connect" })
    }

    @Test
    fun aDeniedPermissionStopsScanning() {
        assertFalse(PermissionChecks.areAllGranted(required, mapOf("scan" to false)) { true })
    }

    @Test
    fun aMissingPermissionIsNotAssumedGranted() {
        assertFalse(PermissionChecks.areAllGranted(required, emptyMap()) { it == "connect" })
    }
}
