package com.example.detectionsystem.data

import android.content.Context
import android.content.SharedPreferences

data class EmergencyContact(val phone: String = "", val name: String = "")

class AccountContactStore(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(
        context.getSharedPreferences("DMS_ACCOUNT_CONTACTS", Context.MODE_PRIVATE)
    )

    fun load(userId: String): EmergencyContact {
        require(userId.isNotBlank())
        return EmergencyContact(
            phone = preferences.getString("$userId:phone", "").orEmpty(),
            name = preferences.getString("$userId:name", "").orEmpty()
        )
    }

    fun save(userId: String, contact: EmergencyContact) {
        require(userId.isNotBlank())
        preferences.edit()
            .putString("$userId:phone", contact.phone)
            .putString("$userId:name", contact.name)
            .apply()
    }
}
