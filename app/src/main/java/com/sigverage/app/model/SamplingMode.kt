package com.sigverage.app.model

/**
 * How aggressively the app samples location while recording, trading GPS
 * power draw against fix frequency and accuracy.
 *
 *  - [Auto] (default): adapts to the device's power state at runtime -
 *                     behaves like [PowerSaver] when Battery Saver is on or
 *                     the battery is low, otherwise like [Balanced].
 *  - [PowerSaver]:    longest interval + coarsest distance filter; the radio
 *                     wakes least often, so the lowest battery use.
 *  - [Balanced]:      a sensible middle ground for everyday recording.
 *  - [HighAccuracy]:  shortest interval + finest distance filter; the most
 *                     detailed coverage map at the cost of higher battery use.
 *
 * [intervalMs] is the fastest update period requested and [minDistanceMeters]
 * is the movement threshold below which no new fix is delivered (so a
 * stationary device stops waking the radio). Both are consumed by
 * [com.sigverage.app.location.LocationTracker.stream]; the matching
 * power/accuracy quality hint is derived there.
 *
 * [Auto] carries [Balanced]-equivalent values only as a safety fallback -
 * [com.sigverage.app.location.LocationTracker] always resolves it to a concrete
 * mode from live battery state before those values are read.
 *
 * Persisted by [com.sigverage.app.data.PreferencesStore] as the enum name,
 * read back through [fromString] so a missing/unknown stored value falls back
 * to [Default] rather than crashing.
 */
enum class SamplingMode(
    val intervalMs: Long,
    val minDistanceMeters: Float,
) {
    Auto(intervalMs = 5_000L, minDistanceMeters = 10f),
    PowerSaver(intervalMs = 20_000L, minDistanceMeters = 25f),
    Balanced(intervalMs = 5_000L, minDistanceMeters = 10f),
    HighAccuracy(intervalMs = 2_000L, minDistanceMeters = 5f);

    companion object {
        /** Default sampling mode when the user hasn't picked one yet. */
        val Default: SamplingMode = Auto

        /** Battery percentage at or below which [Auto] drops to [PowerSaver]. */
        const val AUTO_LOW_BATTERY_PERCENT = 20

        /** Parse a stored string back to a [SamplingMode], falling back to [Default]. */
        fun fromString(s: String?): SamplingMode =
            entries.firstOrNull { it.name == s } ?: Default
    }
}
