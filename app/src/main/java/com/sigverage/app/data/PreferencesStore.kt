package com.sigverage.app.data

import android.content.Context
import android.content.SharedPreferences
import com.sigverage.app.model.ThemeMode

/**
 * On-device key-value store for user preferences.
 *
 * Uses SharedPreferences deliberately: the project already touches it from
 * `SigverageApp.onCreate` for osmdroid configuration, and adding
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

    /**
     * Material You palette opt-in. On Android 12+ the system derives a colour
     * scheme from the user's wallpaper; older devices fall back to the
     * static slate/sky palette regardless of this setting. Defaults to
     * [DEFAULT_DYNAMIC_COLOR_ENABLED] (= true) so users get Material You out
     * of the box on supported devices.
     */
    var dynamicColorEnabled: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC_COLOR_ENABLED, DEFAULT_DYNAMIC_COLOR_ENABLED)
        set(value) {
            prefs.edit().putBoolean(KEY_DYNAMIC_COLOR_ENABLED, value).apply()
        }

    companion object {
        private const val PREFS_NAME = "sigverage_prefs"
        private const val KEY_RETENTION_DAYS = "retention_days"

        /** `0` = forever. The user opts in by changing the policy. */
        const val DEFAULT_RETENTION_DAYS = 0

        /** Material You palette is on by default. */
        const val DEFAULT_DYNAMIC_COLOR_ENABLED = true

        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DYNAMIC_COLOR_ENABLED = "dynamic_color_enabled"
    }
}
