package com.signalspotter.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * On-device key-value store for user preferences.
 *
 * Uses SharedPreferences deliberately: the project already touches it from
 * `SignalSpotterApp.onCreate` for osmdroid configuration, and adding
 * DataStore as a dependency just to persist a single Int is over-engineering.
 *
 * The only setting today is the retention policy. New prefs can be added as
 * keyed properties on this class with no migration cost.
 */
class PreferencesStore(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Retention period in days.
     *   `0` — keep every reading forever (the safest default; opt-in expiry).
     *   `> 0` — readings older than this many days are auto-purged.
     */
    var retentionDays: Int
        get() = prefs.getInt(KEY_RETENTION_DAYS, DEFAULT_RETENTION_DAYS)
        set(value) {
            prefs.edit().putInt(KEY_RETENTION_DAYS, value.coerceAtLeast(0)).apply()
        }

    companion object {
        private const val PREFS_NAME = "signal_spotter_prefs"
        private const val KEY_RETENTION_DAYS = "retention_days"

        /** `0` = forever. The user opts in by changing the policy. */
        const val DEFAULT_RETENTION_DAYS = 0
    }
}
