package com.signalspotter.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
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
     * Cold-ish stream that listens to all enabled providers and emits a
     * [FixSample] whenever a new location is available.
     */
    fun stream(intervalMs: Long = 2_500L): Flow<FixSample> = callbackFlow {
        if (!hasFineLocation()) {
            close()
            return@callbackFlow
        }
        val providers = runCatching { manager.getProviders(true) }.getOrDefault(emptyList())
        if (providers.isEmpty()) {
            close()
            return@callbackFlow
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location.toFix())
            }

            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit

            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        providers.forEach { provider ->
            // GPS gives more accurate updates; network is a fallback that we
            // poll less aggressively to save battery.
            val period = if (provider == LocationManager.GPS_PROVIDER) intervalMs else intervalMs * 4
            runCatching {
                manager.requestLocationUpdates(
                    provider,
                    period,
                    /* minDistance = */ 0f,
                    listener,
                    Looper.getMainLooper()
                )
            }
        }
        awaitClose { manager.removeUpdates(listener) }
    }

    private fun Location.toFix(): FixSample = FixSample(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else Float.NaN,
        provider = provider ?: "unknown",
        timestamp = time
    )
}
