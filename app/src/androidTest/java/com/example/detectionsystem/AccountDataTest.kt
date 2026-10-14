package com.example.detectionsystem

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.detectionsystem.data.AccountContactStore
import com.example.detectionsystem.data.DmsDatabase
import com.example.detectionsystem.data.DrivingSession
import com.example.detectionsystem.data.EmergencyContact
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AccountDataTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun historyIsIsolatedByAccount() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, DmsDatabase::class.java).build()
        try {
            val dao = database.drivingDao()
            dao.insertSession(DrivingSession(durationSeconds = 30L, yawnCount = 1, sleepWarningCount = 0, userId = "alice"))
            dao.insertSession(DrivingSession(durationSeconds = 60L, yawnCount = 2, sleepWarningCount = 1, userId = "bob"))
            assertEquals(listOf("alice"), dao.getSessionsForUser("alice").first().map { it.userId })
            assertEquals(listOf("bob"), dao.getSessionsForUser("bob").first().map { it.userId })
            assertTrue(dao.getSessionsForUser("charlie").first().isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun legacyMigrationPreservesRecordsWithoutExposingThemToAnAccount() = runBlocking {
        val name = "migration-test-${UUID.randomUUID()}"
        val path = context.getDatabasePath(name)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { legacy ->
            legacy.execSQL("CREATE TABLE driving_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, dateTimestamp INTEGER NOT NULL, durationSeconds INTEGER NOT NULL, yawnCount INTEGER NOT NULL, sleepWarningCount INTEGER NOT NULL)")
            legacy.execSQL("INSERT INTO driving_sessions VALUES (1, 1000, 120, 2, 1)")
            legacy.version = 1
        }
        val database = Room.databaseBuilder(context, DmsDatabase::class.java, name)
            .addMigrations(DmsDatabase.MIGRATION_1_2).build()
        try {
            assertTrue(database.drivingDao().getSessionsForUser("alice").first().isEmpty())
            assertTrue(database.drivingDao().getSessionsForUser("").first().isEmpty())
            database.openHelper.writableDatabase.query("SELECT durationSeconds, userId FROM driving_sessions WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(120L, cursor.getLong(0))
                assertEquals("", cursor.getString(1))
            }
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun emergencyContactsAreIsolatedByAccount() {
        val name = "contacts-test-${UUID.randomUUID()}"
        val store = AccountContactStore(context.getSharedPreferences(name, Context.MODE_PRIVATE))
        try {
            store.save("alice", EmergencyContact("+90111", "Alice contact"))
            store.save("bob", EmergencyContact("+90222", "Bob contact"))
            assertEquals("+90111", store.load("alice").phone)
            assertEquals("+90222", store.load("bob").phone)
            assertEquals(EmergencyContact(), store.load("charlie"))
        } finally {
            context.deleteSharedPreferences(name)
        }
    }
}
