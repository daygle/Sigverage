package com.signalspotter.app.model

/**
 * User preference for the app's light/dark theme.
 *
 *  - [System] (default): follows the OS via Compose's `isSystemInDarkTheme()`.
 *  - [Light]:           always light.
 *  - [Dark]:            always dark.
 *
 * The value is persisted by [com.signalspotter.app.data.PreferencesStore] as
 * the enum name in SharedPreferences. Reading the value goes through
 * [fromString] so a missing/unparseable stored value falls back to
 * [Default] rather than crashing.
 *
 * Lives in the `model/` package so both the data layer (which writes it)
 * and the ui layer (which consumes it) can depend on it without crossing
 * layer boundaries.
 */
enum class ThemeMode {
    System,
    Light,
    Dark;

    companion object {
        /** Default theme when the user hasn't picked yet. */
        val Default: ThemeMode = System

        /** Parse a stored string back to a [ThemeMode], falling back to [Default]. */
        fun fromString(s: String?): ThemeMode =
            entries.firstOrNull { it.name == s } ?: Default
    }
}
