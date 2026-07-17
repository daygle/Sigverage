package com.sigverage.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** A single GPS/network fix in a domain-friendly shape. */
data class FixSample(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val provider: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Pure-platform LocationManager wrapper (no Google Play Services), since we
 * want a small APK and we only need coarse fixes: enough to position a
 * recorded reading on a map.
 *
 * `stream(intervalMs)` produces a hot Flow of fixes. `lastKnown()` returns
 * the freshest cached fix synchronously, useful for one-shot capture.
 *
 * Battery: fixes are the app's dominant power cost, so `stream()` registers a
 * **single** provider - fused on API 31+, else GPS, else network - rather than
 * every enabled provider at once, tags the request as balanced-power, and adds
 * a minimum-distance filter so a stationary device stops waking the radio.
 * `lastKnown()` reads only cached fixes and never powers the radio.
 */
@SuppressLint("MissingPermission")
class LocationTracker(private val context: Context) {

    private val manager: LocationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    fun hasFineLocation(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    /** Most-recent cached fix, or null if no permission / no providers. */
    fun lastKnown(): FixSample? {
        if (!hasFineLocation()) return null
        val providers = runCatching { manager.getProviders(true) }.getOrDefault(emptyList())
        return providers.asSequence()
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.let { it.toFix() }
    }

    /**
     * Cold-ish stream that listens to a single, battery-efficient provider and
     * emits a [FixSample] whenever a new location is available.
     *
     * [intervalMs] is the desired (fastest) update period. The request is
     * tagged [LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY] and
     * carries a [MIN_UPDATE_DISTANCE_M] filter so a still device stops
     * generating redundant fixes even if the caller keeps the stream open.
     */
    fun stream(intervalMs: Long = 2_500L): Flow<FixSample> = callbackFlow {
        if (!hasFineLocation()) {
            close()
            return@callbackFlow
        }
        val provider = bestProvider()
        if (provider == null) {
            close()
            return@callbackFlow
        }
        val request = LocationRequestCompat.Builder(intervalMs)
            .setQuality(LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY)
            .setMinUpdateIntervalMillis(intervalMs)
            .setMinUpdateDistanceMeters(MIN_UPDATE_DISTANCE_M)
            .build()
        val listener = LocationListenerCompat { location -> trySend(location.toFix()) }
        runCatching {
            LocationManagerCompat.requestLocationUpdates(
                manager,
                provider,
                request,
                ContextCompat.getMainExecutor(context),
                listener,
            )
        }
        awaitClose { LocationManagerCompat.removeUpdates(manager, listener) }
    }

    /**
     * The single most battery-efficient enabled provider: fused (API 31+) →
     * GPS → network. The fused provider blends GPS, Wi-Fi and sensors at the
     * lowest power cost; GPS is the accurate fallback needed to bin readings
     * into ~38 m coverage tiles; network is the last resort. Registering one
     * provider instead of all of them avoids powering several positioning
     * subsystems simultaneously.
     */
    private fun bestProvider(): String? {
        val enabled = runCatching { manager.getProviders(true) }.getOrDefault(emptyList())
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                LocationManager.FUSED_PROVIDER in enabled -> LocationManager.FUSED_PROVIDER
            LocationManager.GPS_PROVIDER in enabled -> LocationManager.GPS_PROVIDER
            LocationManager.NETWORK_PROVIDER in enabled -> LocationManager.NETWORK_PROVIDER
            else -> enabled.firstOrNull()
        }
    }

    private fun Location.toFix(): FixSample = FixSample(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else Float.NaN,
        provider = provider ?: "unknown",
        timestamp = time
    )

    private companion object {
        /**
         * Minimum distance between successive fixes. A stationary device
         * produces no updates below this threshold, so the radio idles instead
         * of re-reporting the same spot. Kept well under the ~38 m coverage
         * tile size so tile crossings are never missed while moving.
         */
        const val MIN_UPDATE_DISTANCE_M = 10f
    }
}
