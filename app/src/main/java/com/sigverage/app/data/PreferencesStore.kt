package com.sigverage.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.sigverage.app.model.DateFormat
import com.sigverage.app.model.NetworkType
import com.sigverage.app.model.SamplingMode
import com.sigverage.app.model.ThemeMode
import com.sigverage.app.model.TimeFormat

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
     *   `0` - keep every reading forever (the safest default; opt-in expiry).
     *   `> 0` - readings older than this many days are auto-purged.
     */
    var retentionDays: Int
        get() = prefs.getInt(KEY_RETENTION_DAYS, DEFAULT_RETENTION_DAYS)
        set(value) {
            prefs.edit { putInt(KEY_RETENTION_DAYS, value.coerceAtLeast(0)) }
        }

    /**
     * Light/dark theme mode. Defaults to [ThemeMode.Default] (= System) so new
     * users immediately get Material You / system-driven dark mode without any
     * UI interaction.
     */
    var themeMode: ThemeMode
        get() = ThemeMode.fromString(prefs.getString(KEY_THEME_MODE, null))
        set(value) {
            prefs.edit { putString(KEY_THEME_MODE, value.name) }
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
            prefs.edit { putBoolean(KEY_DYNAMIC_COLOR_ENABLED, value) }
        }

    /**
     * Whether the first-launch permission-onboarding screen has been shown
     * (or skipped). Default `false` so every fresh install starts on the
     * onboarding screen; flips to `true` after the user reaches the final
     * step or taps Skip.
     */
    var onboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, DEFAULT_ONBOARDING_COMPLETED)
        set(value) {
            prefs.edit { putBoolean(KEY_ONBOARDING_COMPLETED, value) }
        }

    /**
     * Whether the foreground sampling service should start automatically
     * whenever the app enters the foreground (i.e. `MainScreen` enters
     * composition after onboarding completes). Default `false` because this
     * is an opt-in power-user feature: it posts a persistent notification
     * and keeps the GPS radio hot until the user explicitly pauses
     * recording from the AppBar Play/Pause button.
     *
     * The actual start is performed by `MainScreen`'s
     * `LaunchedEffect(autoRecordEnabled, onboardingCompleted)`; this
     * preference is only the persisted bit. If the required location
     * permissions aren't granted the LaunchedEffect surfaces a one-shot
     * snackbar explaining how to fix it instead of crashing the FGS
     * promotion (Android 14 raises `SecurityException` if the typed FGS
     * permission isn't held).
     */
    var autoRecordEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RECORD_ENABLED, DEFAULT_AUTO_RECORD_ENABLED)
        set(value) {
            prefs.edit { putBoolean(KEY_AUTO_RECORD_ENABLED, value) }
        }

    /**
     * Location sampling mode - the battery-vs-accuracy trade-off applied while
     * recording. Defaults to [SamplingMode.Default] (= Auto). Consumed by the
     * foreground [com.sigverage.app.service.SamplingService] when it opens the
     * location stream.
     */
    var samplingMode: SamplingMode
        get() = SamplingMode.fromString(prefs.getString(KEY_SAMPLING_MODE, null))
        set(value) {
            prefs.edit { putString(KEY_SAMPLING_MODE, value.name) }
        }

    /**
     * Default set of network types the coverage map loads with. Persisted as
     * a set of [NetworkType.name]s. When the key was never set, defaults to
     * *all* networks (the historical behaviour). An explicitly-empty stored
     * set would blank the map on every open, so it is treated as "all" too;
     * the Settings UI additionally prevents removing the last network.
     *
     * Unknown names (e.g. an enum constant removed in a future version) are
     * dropped on read rather than crashing.
     */
    var defaultNetworkFilter: Set<NetworkType>
        get() {
            val stored = prefs.getStringSet(KEY_DEFAULT_NETWORK_FILTER, null)
                ?: return NetworkType.entries.toSet()
            val parsed = stored.mapNotNullTo(HashSet()) { name ->
                NetworkType.entries.firstOrNull { it.name == name }
            }
            return parsed.ifEmpty { NetworkType.entries.toSet() }
        }
        set(value) {
            prefs.edit { putStringSet(KEY_DEFAULT_NETWORK_FILTER, value.mapTo(HashSet()) { it.name }) }
        }

    /**
     * Default set of operator names the coverage map loads with. Persisted as
     * a set of raw operator strings. Empty (the default) means "show all
     * operators", matching the live map's operator-filter semantics.
     */
    var defaultOperatorFilter: Set<String>
        get() = prefs.getStringSet(KEY_DEFAULT_OPERATOR_FILTER, emptySet())?.toSet() ?: emptySet()
        set(value) {
            prefs.edit { putStringSet(KEY_DEFAULT_OPERATOR_FILTER, value.toSet()) }
        }

    /** Preferred time format for UI display. */
    var timeFormat: TimeFormat
        get() = TimeFormat.fromString(prefs.getString(KEY_TIME_FORMAT, null))
        set(value) {
            prefs.edit { putString(KEY_TIME_FORMAT, value.name) }
        }

    /** Preferred date format for UI display. */
    var dateFormat: DateFormat
        get() = DateFormat.fromString(prefs.getString(KEY_DATE_FORMAT, null))
        set(value) {
            prefs.edit { putString(KEY_DATE_FORMAT, value.name) }
        }

    /**
     * Per-network colour overrides, as ARGB ints keyed by [NetworkType].
     *
     * Only networks the user has explicitly recoloured appear in the map; a
     * missing entry means "use the built-in default". Kept as raw ints (not
     * Compose `Color`) so this data-layer class stays free of UI dependencies -
     * the caller merges these with the default palette. Persisted individually
     * under [KEY_NETWORK_COLOR_PREFIX] so a single edit is one key write and a
     * reset is a single key removal.
     */
    val networkColorOverrides: Map<NetworkType, Int>
        get() {
            val overrides = HashMap<NetworkType, Int>()
            for (type in NetworkType.entries) {
                val key = keyForNetworkColor(type)
                if (prefs.contains(key)) overrides[type] = prefs.getInt(key, 0)
            }
            return overrides
        }

    /** Override [type]'s colour with [argb] (an ARGB int). */
    fun setNetworkColor(type: NetworkType, argb: Int) {
        prefs.edit { putInt(keyForNetworkColor(type), argb) }
    }

    /** Drop [type]'s override so it reverts to the built-in default. */
    fun clearNetworkColor(type: NetworkType) {
        prefs.edit { remove(keyForNetworkColor(type)) }
    }

    /** Drop every network colour override, reverting the whole palette. */
    fun clearAllNetworkColors() {
        prefs.edit {
            for (type in NetworkType.entries) remove(keyForNetworkColor(type))
        }
    }

    private fun keyForNetworkColor(type: NetworkType): String =
        "$KEY_NETWORK_COLOR_PREFIX${type.name}"

    companion object {
        private const val PREFS_NAME = "sigverage_prefs"
        private const val KEY_RETENTION_DAYS = "retention_days"

        /** `0` = forever. The user opts in by changing the policy. */
        const val DEFAULT_RETENTION_DAYS = 0

        /** Material You palette is on by default. */
        const val DEFAULT_DYNAMIC_COLOR_ENABLED = true

        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DYNAMIC_COLOR_ENABLED = "dynamic_color_enabled"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed_v1"
        private const val KEY_AUTO_RECORD_ENABLED = "auto_record_enabled_v1"
        private const val KEY_SAMPLING_MODE = "sampling_mode"
        private const val KEY_DEFAULT_NETWORK_FILTER = "default_network_filter"
        private const val KEY_DEFAULT_OPERATOR_FILTER = "default_operator_filter"
        private const val KEY_TIME_FORMAT = "time_format"
        private const val KEY_DATE_FORMAT = "date_format"

        /** Prefix for per-network colour overrides, e.g. `network_color_LTE`. */
        private const val KEY_NETWORK_COLOR_PREFIX = "network_color_"

        /** First launch defaults to showing the onboarding screen. */
        const val DEFAULT_ONBOARDING_COMPLETED = false

        /** Auto-record is opt-in - power users only. */
        const val DEFAULT_AUTO_RECORD_ENABLED = false
    }
}
