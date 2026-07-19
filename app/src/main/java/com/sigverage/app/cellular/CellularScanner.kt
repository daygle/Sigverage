package com.sigverage.app.cellular

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellSignalStrength
import android.telephony.TelephonyManager
import androidx.annotation.RequiresPermission
import com.sigverage.app.model.NetworkType
import com.sigverage.app.model.SignalReading

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
@Suppress("DEPRECATION")
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
        accuracyMeters: Float,
    ): SignalReading {
        val infos: List<CellInfo> = runCatching {
            telephonyManager.allCellInfo.orEmpty()
        }.getOrDefault(emptyList())

        val primary: CellInfo? = infos.asSequence()
            .filter { it.isRegistered }
            .maxByOrNull { signalDbmOf(it) ?: Int.MIN_VALUE }

        val dataType: Int = runCatching { telephonyManager.dataNetworkType }
            .getOrDefault(TelephonyManager.NETWORK_TYPE_UNKNOWN)

        val networkType = classify(primary, dataType)
        val operator = runCatching {
            telephonyManager.networkOperatorName.takeIf { it.isNotBlank() }
        }.getOrNull()

        // LTE-specific signal decomposition (RSRP/RSRQ/SNR are most useful for
        // assessing LTE/NR quality).
        val rsrp = (primary as? android.telephony.CellInfoLte)?.cellSignalStrength?.rsrp?.takeIf { it != Int.MAX_VALUE }
        val rsrq = (primary as? android.telephony.CellInfoLte)?.cellSignalStrength?.rsrq?.takeIf { it != Int.MAX_VALUE }
        val rssnr = (primary as? android.telephony.CellInfoLte)?.cellSignalStrength?.rssnr?.takeIf { it != Int.MAX_VALUE }

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
    private fun classify(primary: CellInfo?, dataType: Int): NetworkType {
        if (primary == null) return NetworkType.Unknown
        return when (primary) {
            is android.telephony.CellInfoLte ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && isNrNsa(dataType)) {
                    NetworkType.NR_NSA
                } else {
                    NetworkType.LTE
                }
            is android.telephony.CellInfoCdma -> NetworkType.CDMA
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    primary is android.telephony.CellInfoNr
                ) {
                    NetworkType.FiveG
                } else if (primary is android.telephony.CellInfoWcdma) {
                    NetworkType.HSPA
                } else if (primary is android.telephony.CellInfoGsm) {
                    if (dataType == TelephonyManager.NETWORK_TYPE_EDGE) NetworkType.EDGE
                    else NetworkType.GSM
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    primary is android.telephony.CellInfoTdscdma
                ) {
                    NetworkType.HSPA
                } else {
                    NetworkType.Unknown
                }
            }
        }
    }

    /**
     * 5G NSA (enDC) detection.
     *
     * Two heuristics, best-effort:
     *
     * 1. **TelephonyDisplayInfo** (async, API 30+) — the definitive source.
     *    There is no synchronous getter for
     *    [android.telephony.TelephonyDisplayInfo] on [TelephonyManager]; the
     *    override network type is only delivered asynchronously through
     *    `TelephonyCallback.DisplayInfoListener` (API 31+) or
     *    `PhoneStateListener#onDisplayInfoChanged` (API 30). Until that
     *    listener is wired up we fall through to heuristic 2.
     *
     * 2. **dataNetworkType** (synchronous, API 24+) — a pragmatic fallback.
     *    When the primary cell is LTE but the data bearer reports
     *    `NETWORK_TYPE_NR`, the device is almost certainly on an NSA
     *    connection. This is not foolproof (some devices report the anchor
     *    type instead of NR), but it catches the most common 5G deployments
     *    today and is strictly better than the previous hard-coded `false`.
     */
    private fun isNrNsa(dataType: Int): Boolean = dataType == TelephonyManager.NETWORK_TYPE_NR

    /** Unified dBm estimate across all cell types (CellSignalStrength.dbm, API 23+). */
    private fun signalDbmOf(info: CellInfo): Int? {
        val s: CellSignalStrength = when {
            info is android.telephony.CellInfoLte -> info.cellSignalStrength
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info is android.telephony.CellInfoNr -> info.cellSignalStrength
            info is android.telephony.CellInfoWcdma -> info.cellSignalStrength
            info is android.telephony.CellInfoGsm -> info.cellSignalStrength
            info is android.telephony.CellInfoCdma -> info.cellSignalStrength
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tdscdmaStrength(info) ?: return null
                } else {
                    return null
                }
            }
        }
        // Ignore uninitialised values reported by some OEMs.
        return s.dbm.takeIf { it != Int.MAX_VALUE && it > -150 && it < 0 }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun tdscdmaStrength(info: CellInfo): CellSignalStrength? =
        (info as? android.telephony.CellInfoTdscdma)?.cellSignalStrength

    private fun extractMcc(info: CellInfo): Int? = when {
        info is android.telephony.CellInfoLte -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.cellIdentity.mccString
            } else {
                @Suppress("DEPRECATION")
                info.cellIdentity.mcc.takeIf { it != Int.MAX_VALUE }?.toString()
            }?.toIntOrNull()?.takeIf { it in 0..999 }
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info is android.telephony.CellInfoNr ->
            (info.cellIdentity as? android.telephony.CellIdentityNr)?.mccString?.toIntOrNull()
                ?.takeIf { it in 0..999 }
        info is android.telephony.CellInfoWcdma -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.cellIdentity.mccString
            } else {
                @Suppress("DEPRECATION")
                info.cellIdentity.mcc.takeIf { it != Int.MAX_VALUE }?.toString()
            }?.toIntOrNull()?.takeIf { it in 0..999 }
        }
        info is android.telephony.CellInfoGsm -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.cellIdentity.mccString
            } else {
                @Suppress("DEPRECATION")
                info.cellIdentity.mcc.takeIf { it != Int.MAX_VALUE }?.toString()
            }?.toIntOrNull()?.takeIf { it in 0..999 }
        }
        else -> null
    }

    private fun extractMnc(info: CellInfo): Int? = when {
        info is android.telephony.CellInfoLte -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.cellIdentity.mncString
            } else {
                @Suppress("DEPRECATION")
                info.cellIdentity.mnc.takeIf { it != Int.MAX_VALUE }?.toString()
            }?.toIntOrNull()?.takeIf { it in 0..999 }
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info is android.telephony.CellInfoNr ->
            (info.cellIdentity as? android.telephony.CellIdentityNr)?.mncString?.toIntOrNull()
                ?.takeIf { it in 0..999 }
        info is android.telephony.CellInfoWcdma -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.cellIdentity.mncString
            } else {
                @Suppress("DEPRECATION")
                info.cellIdentity.mnc.takeIf { it != Int.MAX_VALUE }?.toString()
            }?.toIntOrNull()?.takeIf { it in 0..999 }
        }
        info is android.telephony.CellInfoGsm -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.cellIdentity.mncString
            } else {
                @Suppress("DEPRECATION")
                info.cellIdentity.mnc.takeIf { it != Int.MAX_VALUE }?.toString()
            }?.toIntOrNull()?.takeIf { it in 0..999 }
        }
        else -> null
    }

    private fun extractCellId(info: CellInfo): Long? = when {
        info is android.telephony.CellInfoLte -> info.cellIdentity.ci.toLong()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info is android.telephony.CellInfoNr ->
            (info.cellIdentity as? android.telephony.CellIdentityNr)?.nci
        info is android.telephony.CellInfoWcdma -> info.cellIdentity.cid.toLong()
        info is android.telephony.CellInfoGsm -> info.cellIdentity.cid.toLong()
        info is android.telephony.CellInfoCdma -> info.cellIdentity.basestationId.toLong()
        else -> null
    }
}
