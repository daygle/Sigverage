package com.sigverage.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.sigverage.app.model.DaysOfWeekConverter
import com.sigverage.app.model.NetworkType
import com.sigverage.app.model.RecordingSchedule
import com.sigverage.app.model.SignalReading
import kotlinx.coroutines.flow.Flow

class Converters {
    @TypeConverter
    fun fromNetworkType(value: NetworkType): String = value.name

    @TypeConverter
    fun toNetworkType(value: String): NetworkType =
        runCatching { NetworkType.valueOf(value) }.getOrDefault(NetworkType.Unknown)
}

@Dao
interface SignalReadingDao {

    @Query("SELECT * FROM signal_readings ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<SignalReading>>

    @Insert
    suspend fun insert(reading: SignalReading): Long

    @Insert
    suspend fun insertAll(readings: List<SignalReading>)

    @Query("DELETE FROM signal_readings WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM signal_readings")
    suspend fun deleteAll(): Int

    /**
     * Hard-delete every reading whose `timestamp` is older than [threshold].
     * Returns the row count actually deleted (Room surfaces this for
     * `@Query DELETE … ` statements since 2.4).
     */
    @Query("DELETE FROM signal_readings WHERE timestamp < :threshold")
    suspend fun deleteOlderThan(threshold: Long): Int

    /**
     * Returns true if at least one reading falls within the geographic
     * bounding box defined by [northLat], [westLng], [southLat], [eastLng].
     * Used by smart sampling to avoid redundant recordings in the same
     * coverage tile.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM signal_readings WHERE latitude BETWEEN :southLat AND :northLat AND longitude BETWEEN :westLng AND :eastLng LIMIT 1)")
    suspend fun existsInBounds(northLat: Double, westLng: Double, southLat: Double, eastLng: Double): Boolean
}

/**
 * DAO for [RecordingSchedule] CRUD operations.
 */
@Dao
interface ScheduleDao {

    @Query("SELECT * FROM recording_schedules ORDER BY startHour, startMinute")
    fun observeAll(): Flow<List<RecordingSchedule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(schedule: RecordingSchedule): Long

    @Query("DELETE FROM recording_schedules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM recording_schedules WHERE enabled = 1")
    suspend fun getEnabled(): List<RecordingSchedule>

    @Query("SELECT * FROM recording_schedules WHERE id = :id")
    suspend fun getById(id: Long): RecordingSchedule?
}

@Database(
    entities = [SignalReading::class, RecordingSchedule::class],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Converters::class, DaysOfWeekConverter::class)
abstract class SignalDatabase : RoomDatabase() {
    abstract fun signalReadingDao(): SignalReadingDao
    abstract fun scheduleDao(): ScheduleDao
}
