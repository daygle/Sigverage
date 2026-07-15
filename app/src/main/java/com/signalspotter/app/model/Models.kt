package com.signalspotter.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cellular technology classification surfaced to the UI.
 * NR_NSA is "5G Non-Standalone" — LTE master + NR secondary, reported as LTE by
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
    Unknown("Other", "?")
}

/**
 * One recorded reading: a GPS fix plus a cellular snapshot and metadata.
 *
 * Persisted by Room. `signalDbm` is the unified dBm estimate (mapped from
 * CellSignalStrength.dbm, API 23+, so API 26+ is safe). rsrp/rsrq/rssnr are
 * filled when the primary cell is LTE; they are null otherwise.
 */
@Entity(tableName = "signal_readings")
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
    val operatorName: String?
)
