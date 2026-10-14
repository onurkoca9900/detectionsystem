package com.example.detectionsystem.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "driving_sessions")
data class DrivingSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dateTimestamp: Long = System.currentTimeMillis(),
    val durationSeconds: Long,
    val yawnCount: Int,
    val sleepWarningCount: Int,
    @ColumnInfo(defaultValue = "''") val userId: String
)

@Dao
interface DrivingDao {
    @Insert
    suspend fun insertSession(session: DrivingSession)
    @Query("SELECT * FROM driving_sessions WHERE userId = :userId AND userId != '' ORDER BY dateTimestamp DESC")
    fun getSessionsForUser(userId: String): Flow<List<DrivingSession>>
}

@Database(entities = [DrivingSession::class], version = 2, exportSchema = false)
abstract class DmsDatabase : RoomDatabase() {
    abstract fun drivingDao(): DrivingDao
    companion object {
        // Preserve old device-wide records without assigning them to an unrelated account.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE driving_sessions ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile
        private var INSTANCE: DmsDatabase? = null
        fun getDatabase(context: Context): DmsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DmsDatabase::class.java,
                    "dms_history_database"
                ).addMigrations(MIGRATION_1_2).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
