package com.sigverage.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.sigverage.app.model.NetworkType
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

    @Query("SELECT COUNT(*) FROM signal_readings")
    fun observeCount(): Flow<Int>

    @Insert
    suspend fun insert(reading: SignalReading): Long

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

    @Delete
    suspend fun delete(reading: SignalReading)
}

@Database(
    entities = [SignalReading::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SignalDatabase : RoomDatabase() {
    abstract fun signalReadingDao(): SignalReadingDao
}
