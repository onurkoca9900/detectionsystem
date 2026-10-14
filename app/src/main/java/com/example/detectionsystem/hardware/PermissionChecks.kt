package com.example.detectionsystem.hardware

object PermissionChecks {
    fun areAllGranted(
        requiredPermissions: Array<String>,
        requestResults: Map<String, Boolean>,
        isAlreadyGranted: (String) -> Boolean
    ): Boolean = requiredPermissions.all { permission ->
        requestResults[permission] ?: isAlreadyGranted(permission)
    }
}
