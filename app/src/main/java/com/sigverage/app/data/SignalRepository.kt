package com.sigverage.app.data

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sigverage.app.model.RecordingSchedule
import com.sigverage.app.model.SignalReading
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for signal readings and recording schedules.
 * Wraps the Room DAOs and hides Room from the rest of the codebase.
 *
 * The `get(context)` factory keeps the database instance singleton across
 * the app - including from inside the foreground service.
 */
class SignalRepository(
    private val dao: SignalReadingDao,
    private val scheduleDao: ScheduleDao,
) {

    fun observeReadings(): Flow<List<SignalReading>> = dao.observeAll()

    suspend fun add(reading: SignalReading): Long = dao.insert(reading)

    suspend fun addAll(readings: List<SignalReading>) = dao.insertAll(readings)

    /**
     * Check whether at least one reading already exists in the coverage tile
     * at [zoom] that contains ([lat], [lng]). Used by smart sampling in
     * [com.sigverage.app.service.SamplingService] to avoid redundant
     * recordings in the same ~50 m cell.
     */
    suspend fun hasReadingInTile(lat: Double, lng: Double, zoom: Int): Boolean {
        val tile = com.sigverage.app.coverage.latLngToTile(lat, lng, zoom)
        val bounds = com.sigverage.app.coverage.tileBounds(tile)
        return dao.existsInBounds(bounds.northLat, bounds.westLng, bounds.southLat, bounds.eastLng)
    }
    suspend fun delete(id: Long) = dao.deleteById(id)
    suspend fun deleteAll() = dao.deleteAll()

    /**
     * Hard-delete every reading whose timestamp precedes [thresholdMillis].
     * Returns the count deleted - primarily used so the UI can show feedback
     * like "Removed 23 old readings" when the user changes retention.
     */
    suspend fun deleteOlderThan(thresholdMillis: Long): Int =
        dao.deleteOlderThan(thresholdMillis)

    // ---- Schedule operations ----

    fun observeSchedules(): Flow<List<RecordingSchedule>> = scheduleDao.observeAll()

    /** Insert or update a schedule. Returns the row id. */
    suspend fun upsertSchedule(schedule: RecordingSchedule): Long =
        scheduleDao.upsert(schedule)

    suspend fun deleteSchedule(id: Long) = scheduleDao.deleteById(id)

    /** All enabled schedules, used by `ScheduleManager` to register alarms. */
    suspend fun getEnabledSchedules(): List<RecordingSchedule> =
        scheduleDao.getEnabled()

    suspend fun getScheduleById(id: Long): RecordingSchedule? =
        scheduleDao.getById(id)

    companion object {
        private const val DB_NAME = "signals.db"

        @Volatile
        private var INSTANCE: SignalRepository? = null

        fun get(context: Context): SignalRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context.applicationContext).also { INSTANCE = it }
            }

        /**
         * Migration from schema v1 (readings only) to v2 (readings + schedules).
         * Creates the new `recording_schedules` table.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS recording_schedules (
                       id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                       name TEXT NOT NULL,
                       daysOfWeek TEXT NOT NULL,
                       startHour INTEGER NOT NULL,
                       startMinute INTEGER NOT NULL,
                       endHour INTEGER NOT NULL,
                       endMinute INTEGER NOT NULL,
                       enabled INTEGER NOT NULL DEFAULT 1
                    )""",
                )
            }
        }

        /**
         * Migration from schema v2 to v3. Adds the composite coordinate index
         * backing the smart-sampling dedup lookup. The index name must match
         * the one Room derives from `@Index(value = ["latitude", "longitude"])`
         * on [SignalReading] (`index_<table>_<cols>`) or Room's schema
         * validation fails on open.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "`index_signal_readings_latitude_longitude` " +
                        "ON `signal_readings` (`latitude`, `longitude`)"
                )
            }
        }

        private fun build(appContext: Context): SignalRepository {
            val db = Room.databaseBuilder(
                appContext,
                SignalDatabase::class.java,
                DB_NAME
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
            return SignalRepository(db.signalReadingDao(), db.scheduleDao())
        }
    }
}
