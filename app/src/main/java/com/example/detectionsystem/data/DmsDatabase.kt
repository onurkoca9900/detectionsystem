package com.example.detectionsystem.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "driving_sessions")
data class DrivingSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dateTimestamp: Long = System.currentTimeMillis(),
    val durationSeconds: Long,
    val yawnCount: Int,
    val sleepWarningCount: Int
)

@Dao
interface DrivingDao {
    @Insert
    suspend fun insertSession(session: DrivingSession)
    @Query("SELECT * FROM driving_sessions ORDER BY dateTimestamp DESC")
    fun getAllSessions(): Flow<List<DrivingSession>>
}

@Database(entities = [DrivingSession::class], version = 1, exportSchema = false)
abstract class DmsDatabase : RoomDatabase() {
    abstract fun drivingDao(): DrivingDao
    companion object {
        @Volatile
        private var INSTANCE: DmsDatabase? = null
        fun getDatabase(context: Context): DmsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DmsDatabase::class.java,
                    "dms_history_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}