package com.sigverage.app.data

import android.content.Context
import androidx.room.Room
import com.sigverage.app.model.SignalReading
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for signal readings. Wraps the Room DAO and hides
 * Room from the rest of the codebase.
 *
 * The `get(context)` factory keeps the database instance singleton across
 * the app — including from inside the foreground service.
 */
class SignalRepository(private val dao: SignalReadingDao) {

    fun observeReadings(): Flow<List<SignalReading>> = dao.observeAll()
    fun observeCount(): Flow<Int> = dao.observeCount()

    suspend fun add(reading: SignalReading): Long = dao.insert(reading)
    suspend fun delete(id: Long) = dao.deleteById(id)
    suspend fun delete(reading: SignalReading) = dao.delete(reading)
    suspend fun deleteAll() = dao.deleteAll()

    /**
     * Hard-delete every reading whose timestamp precedes [thresholdMillis].
     * Returns the count deleted — primarily used so the UI can show feedback
     * like "Removed 23 old readings" when the user changes retention.
     */
    suspend fun deleteOlderThan(thresholdMillis: Long): Int =
        dao.deleteOlderThan(thresholdMillis)

    companion object {
        private const val DB_NAME = "signals.db"

        @Volatile
        private var INSTANCE: SignalRepository? = null

        fun get(context: Context): SignalRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context.applicationContext).also { INSTANCE = it }
            }

        private fun build(appContext: Context): SignalRepository {
            val db = Room.databaseBuilder(
                appContext,
                SignalDatabase::class.java,
                DB_NAME
            )
                .fallbackToDestructiveMigration(dropAllTables = true) // v1 only; drop everything on schema mismatch and add proper migrations later
                .build()
            return SignalRepository(db.signalReadingDao())
        }
    }
}
