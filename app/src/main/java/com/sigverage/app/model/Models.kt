package com.sigverage.app.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

/**
 * Cellular technology classification surfaced to the UI.
 * NR_NSA is "5G Non-Standalone" - LTE master + NR secondary, reported as LTE by
 * the radio unless we read TelephonyDisplayInfo's override.
 */
enum class NetworkType(val label: String, val shortLabel: String) {
    FiveG("5G NR", "5G"),
    NR_NSA("5G NSA", "5G"),
    LTE("LTE (4G)", "LTE"),
    HSPA("HSPA (3G)", "3G"),
    GSM("GPRS (2G)", "2G"),
    EDGE("EDGE (2.5G)", "E"),
    CDMA("CDMA", "1X"),
    Unknown("No Service", "None")
}

/**
 * One recorded reading: a GPS fix plus a cellular snapshot and metadata.
 *
 * Persisted by Room. `signalDbm` is the unified dBm estimate (mapped from
 * CellSignalStrength.dbm, API 23+, so API 26+ is safe). rsrp/rsrq/rssnr are
 * filled when the primary cell is LTE; they are null otherwise.
 */
@Entity(
    tableName = "signal_readings",
    // Composite index on the coordinate columns so the smart-sampling
    // bounding-box lookup (SignalReadingDao.existsInBounds, run on every
    // fix while recording) is an index range-scan instead of a full table
    // scan as the readings table grows.
    indices = [Index(value = ["latitude", "longitude"])],
)
data class SignalReading(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val provider: String,
    val networkType: NetworkType,
    val signalDbm: Int?,
    val rsrpDbm: Int?,
    val rsrqDb: Int?,
    val snrDb: Int?,
    val mcc: Int?,
    val mnc: Int?,
    val cellId: Long?,
    val operatorName: String?,
)

/**
 * A user-defined recording schedule. Specifies which days of the week
 * and what time window the app should automatically start/stop sampling.
 *
 * [daysOfWeek] uses ISO-8601 day numbers: 1=Monday, 7=Sunday.
 * [startHour]/[startMinute] and [endHour]/[endMinute] define the
 * active window. When [enabled] is false the schedule is paused.
 */
@Entity(tableName = "recording_schedules")
data class RecordingSchedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val daysOfWeek: Set<Int>,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val enabled: Boolean = true,
)

/**
 * TypeConverter for [RecordingSchedule.daysOfWeek]: persists a Set<Int>
 * as a comma-separated string (e.g. "1,2,3,4,5" for Mon-Fri).
 */
class DaysOfWeekConverter {
    @TypeConverter
    fun fromSet(days: Set<Int>): String =
        days.asSequence().sorted().joinToString(",")

    @TypeConverter
    fun toSet(csv: String): Set<Int> {
        if (csv.isBlank()) return emptySet()
        return csv.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    }
}
