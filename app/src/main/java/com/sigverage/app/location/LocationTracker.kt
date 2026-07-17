package com.sigverage.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import com.sigverage.app.model.SamplingMode
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
 * every enabled provider at once, and its interval, distance filter and
 * power/accuracy quality all come from the user-selected [SamplingMode] so a
 * stationary device stops waking the radio. `lastKnown()` reads only cached
 * fixes and never powers the radio.
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
     * [mode] drives the power/accuracy trade-off: its interval, minimum-update
     * distance and the quality hint below all scale together so a stationary
     * device stops generating redundant fixes even if the caller keeps the
     * stream open.
     */
    fun stream(mode: SamplingMode = SamplingMode.Default): Flow<FixSample> = callbackFlow {
        if (!hasFineLocation()) {
            close()
            return@callbackFlow
        }
        val provider = bestProvider()
        if (provider == null) {
            close()
            return@callbackFlow
        }
        val effective = resolve(mode)
        val request = LocationRequestCompat.Builder(effective.intervalMs)
            .setQuality(qualityFor(effective))
            .setMinUpdateIntervalMillis(effective.intervalMs)
            .setMinUpdateDistanceMeters(effective.minDistanceMeters)
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

    /**
     * Resolve [SamplingMode.Auto] to a concrete mode from live power state:
     * [SamplingMode.PowerSaver] when the system is in Battery Saver or the
     * battery is at/below [SamplingMode.AUTO_LOW_BATTERY_PERCENT], else
     * [SamplingMode.Balanced]. Any non-Auto mode is returned unchanged.
     */
    private fun resolve(mode: SamplingMode): SamplingMode {
        if (mode != SamplingMode.Auto) return mode
        val saver = runCatching {
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode
        }.getOrDefault(false)
        val level = runCatching {
            (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        }.getOrDefault(-1)
        val lowBattery = level in 0..SamplingMode.AUTO_LOW_BATTERY_PERCENT
        return if (saver || lowBattery) SamplingMode.PowerSaver else SamplingMode.Balanced
    }

    /** Map a [SamplingMode] to the matching LocationRequest power/accuracy hint. */
    private fun qualityFor(mode: SamplingMode): Int = when (mode) {
        SamplingMode.PowerSaver -> LocationRequestCompat.QUALITY_LOW_POWER
        SamplingMode.HighAccuracy -> LocationRequestCompat.QUALITY_HIGH_ACCURACY
        // Auto never reaches here (resolved above); treat as Balanced if it does.
        SamplingMode.Balanced, SamplingMode.Auto ->
            LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY
    }

    private fun Location.toFix(): FixSample = FixSample(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else Float.NaN,
        provider = provider ?: "unknown",
        timestamp = time
    )
}
