package com.signalspotter.app.data

import android.content.Context
import android.content.SharedPreferences
import com.signalspotter.app.model.ThemeMode

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

    /**
     * Light/dark theme mode. Defaults to [ThemeMode.Default] (= System) so new
     * users immediately get Material You / system-driven dark mode without any
     * UI interaction.
     */
    var themeMode: ThemeMode
        get() = ThemeMode.fromString(prefs.getString(KEY_THEME_MODE, null))
        set(value) {
            prefs.edit().putString(KEY_THEME_MODE, value.name).apply()
        }

    companion object {
        private const val PREFS_NAME = "signal_spotter_prefs"
        private const val KEY_RETENTION_DAYS = "retention_days"

        /** `0` = forever. The user opts in by changing the policy. */
        const val DEFAULT_RETENTION_DAYS = 0

        private const val KEY_THEME_MODE = "theme_mode"
    }
}
