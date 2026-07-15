package com.signalspotter.app.cellular

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellSignalStrength
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.annotation.RequiresPermission
import com.signalspotter.app.model.NetworkType
import com.signalspotter.app.model.SignalReading

/**
 * Reads the device's current cellular state.
 *
 * All access to the radio is best-effort. We pick the registered cell with the
 * strongest dBm estimate and classify it into a [NetworkType]. Where APIs
 * differ across versions we gate with `Build.VERSION.SDK_INT` instead of
 * letting the class verifier fail on older devices.
 *
 * 5G NSA enDC note: relying on `dataNetworkType == NETWORK_TYPE_NR` is
 * unreliable because NSA anchors to an LTE control plane. We instead read
 * `TelephonyDisplayInfo.overrideNetworkType == OVERRIDE_NETWORK_TYPE_NR_NSA`
 * on API 30+; on older devices we conservatively report LTE.
 */
@SuppressLint("MissingPermission")
class CellularScanner(private val context: Context) {

    private val telephonyManager: TelephonyManager by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }

    /**
     * Snapshot current cellular info. Requires ACCESS_FINE_LOCATION
     * (Android gated reading CellInfo on location access since API 24).
     */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun snapshot(
        provider: String,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float
    ): SignalReading {
        val infos: List<CellInfo> = runCatching {
            telephonyManager.allCellInfo.orEmpty()
        }.getOrDefault(emptyList())

        val primary: CellInfo? = infos
            .filter { it.isRegistered }
            .maxByOrNull { signalDbmOf(it) ?: Int.MIN_VALUE }

        val voiceType: Int = runCatching { telephonyManager.voiceNetworkType }
            .getOrDefault(TelephonyManager.NETWORK_TYPE_UNKNOWN)
        val dataType: Int = runCatching { telephonyManager.dataNetworkType }
            .getOrDefault(TelephonyManager.NETWORK_TYPE_UNKNOWN)

        val networkType = classify(primary, voiceType, dataType)
        val operator = runCatching {
            telephonyManager.networkOperatorName.takeIf { it.isNotBlank() }
        }.getOrNull()

        // LTE-specific signal decomposition (RSRP/RSRQ/SNR are most useful for
        // assessing LTE/NR quality).
        val rsrp = if (primary is android.telephony.CellInfoLte) {
            primary.cellSignalStrength.rsrp.takeIf { it != Int.MAX_VALUE }
        } else null
        val rsrq = if (primary is android.telephony.CellInfoLte) {
            primary.cellSignalStrength.rsrq.takeIf { it != Int.MAX_VALUE }
        } else null
        val rssnr = if (primary is android.telephony.CellInfoLte) {
            primary.cellSignalStrength.rssnr.takeIf { it != Int.MAX_VALUE }
        } else null

        return SignalReading(
            timestamp = System.currentTimeMillis(),
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
            provider = provider,
            networkType = networkType,
            signalDbm = primary?.let { signalDbmOf(it) },
            rsrpDbm = rsrp,
            rsrqDb = rsrq,
            snrDb = rssnr,
            mcc = primary?.let { extractMcc(it) },
            mnc = primary?.let { extractMnc(it) },
            cellId = primary?.let { extractCellId(it) },
            operatorName = operator
        )
    }

    /**
     * Map a CellInfo to a NetworkType. The CellInfoTdscdma subtype only
     * exists from API 29, so we gate accordingly to keep the class verifier
     * happy on lower API levels.
     */
    private fun classify(primary: CellInfo?, voiceType: Int, dataType: Int): NetworkType {
        if (primary == null) return NetworkType.Unknown
        return when (primary) {
            is android.telephony.CellInfoLte ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && isNrNsa()) {
                    NetworkType.NR_NSA
                } else {
                    NetworkType.LTE
                }
            is android.telephony.CellInfoNr -> NetworkType.FiveG
            is android.telephony.CellInfoWcdma -> NetworkType.HSPA
            is android.telephony.CellInfoGsm -> when (dataType) {
                TelephonyManager.NETWORK_TYPE_EDGE -> NetworkType.EDGE
                TelephonyManager.NETWORK_TYPE_GPRS -> NetworkType.GSM
                else -> NetworkType.GSM
            }
            is android.telephony.CellInfoCdma -> NetworkType.CDMA
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    primary is android.telephony.CellInfoTdscdma
                ) {
                    NetworkType.HSPA
                } else {
                    NetworkType.Unknown
                }
            }
        }
    }

    /** 5G NSA enDC. Only meaningful on API 30+. */
    private fun isNrNsa(): Boolean = runCatching {
        telephonyManager.displayInfo?.overrideNetworkType ==
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA
    }.getOrDefault(false)

    /** Unified dBm estimate across all cell types (CellSignalStrength.dbm, API 23+). */
    private fun signalDbmOf(info: CellInfo): Int? {
        val s: CellSignalStrength = when (info) {
            is android.telephony.CellInfoLte -> info.cellSignalStrength
            is android.telephony.CellInfoNr -> info.cellSignalStrength
            is android.telephony.CellInfoWcdma -> info.cellSignalStrength
            is android.telephony.CellInfoGsm -> info.cellSignalStrength
            is android.telephony.CellInfoCdma -> info.cellSignalStrength
            else -> return tdscdmaStrength(info)
        }
        // Ignore uninitialised values reported by some OEMs.
        return s.dbm.takeIf { it != Int.MAX_VALUE && it > -150 && it < 0 }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun tdscdmaStrength(info: CellInfo): CellSignalStrength? =
        (info as? android.telephony.CellInfoTdscdma)?.cellSignalStrength

    private fun extractMcc(info: CellInfo): Int? = when (info) {
        is android.telephony.CellInfoLte -> info.cellIdentity.mcc
            ?.takeIf { it != Int.MAX_VALUE && it >= 0 }
        is android.telephony.CellInfoNr -> info.cellIdentity.mccString?.toIntOrNull()
        is android.telephony.CellInfoWcdma -> info.cellIdentity.mcc
            ?.takeIf { it != Int.MAX_VALUE && it >= 0 }
        is android.telephony.CellInfoGsm -> info.cellIdentity.mcc
            ?.takeIf { it != Int.MAX_VALUE && it >= 0 }
        else -> null
    }

    private fun extractMnc(info: CellInfo): Int? = when (info) {
        is android.telephony.CellInfoLte -> info.cellIdentity.mnc
            ?.takeIf { it != Int.MAX_VALUE && it >= 0 }
        is android.telephony.CellInfoNr -> info.cellIdentity.mncString?.toIntOrNull()
        is android.telephony.CellInfoWcdma -> info.cellIdentity.mnc
            ?.takeIf { it != Int.MAX_VALUE && it >= 0 }
        is android.telephony.CellInfoGsm -> info.cellIdentity.mnc
            ?.takeIf { it != Int.MAX_VALUE && it >= 0 }
        else -> null
    }

    private fun extractCellId(info: CellInfo): Long? = when (info) {
        is android.telephony.CellInfoLte -> info.cellIdentity.ci.toLong()
        is android.telephony.CellInfoNr -> info.cellIdentity.nci
        is android.telephony.CellInfoWcdma -> info.cellIdentity.ci.toLong()
        is android.telephony.CellInfoGsm -> info.cellIdentity.cid.toLong()
        is android.telephony.CellInfoCdma -> info.cellIdentity.basestationId.toLong()
        else -> null
    }
}
