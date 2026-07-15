package com.signalspotter.app.data

import android.content.Context
import androidx.room.Room
import com.signalspotter.app.model.SignalReading
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
                .fallbackToDestructiveMigration() // v1 only; add proper migrations later
                .build()
            return SignalRepository(db.signalReadingDao())
        }
    }
}
