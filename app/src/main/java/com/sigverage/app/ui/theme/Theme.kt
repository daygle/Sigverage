package com.sigverage.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.sigverage.app.model.NetworkType
import com.sigverage.app.model.ThemeMode

/* ---- Slate/sky palette used as the static fallback when dynamic
        Material You colours are unavailable (Android <12). ---- */

private val Sky500 = Color(0xFF0EA5E9)
private val Sky400 = Color(0xFF38BDF8)
private val Sky700 = Color(0xFF0369A1)
private val SignalGreen = Color(0xFF22C55E)
private val SignalAmber = Color(0xFFF59E0B)
private val SignalRed = Color(0xFFEF4444)
private val Slate900 = Color(0xFF0F172A)
private val Slate800 = Color(0xFF1E293B)
private val Slate700 = Color(0xFF334155)
private val Slate100 = Color(0xFFF1F5F9)
private val Slate50 = Color(0xFFFAFAFC)

/**
 * Marker colours for the static slate/sky fallback palette. Use this only
 * from non-Compose contexts (DAOs, ViewModels, plain helpers) where we
 * can't observe the live `ColorScheme`. UI code should always go through
 * [rememberNetworkColors] instead so the marker palette tracks the
 * user's chosen theme.
 *
 * Identity mapping (stable across themes):
 *   5G    → palette's primary          (often blue/cool)
 *   NR_NSA→ palette's primaryContainer (softer version)
 *   LTE   → palette's tertiary         (often warm/green)
 *   HSPA  → palette's tertiaryContainer
 *   GSM   → palette's secondary        (typically analogous to primary)
 *   EDGE  → palette's secondaryContainer
 *   CDMA  → palette's error            (red-ish, distinct category)
 *   Unknown→ palette's outline          (neutral, low-saturation)
 */
val NetworkColors: Map<NetworkType, Color> = mapOf(
    NetworkType.FiveG to Sky500,
    NetworkType.NR_NSA to Color(0xFFE0F2FE),       // primaryContainer in LightColors
    NetworkType.LTE to SignalGreen,
    NetworkType.HSPA to Color(0xFFA7F3D0),          // secondaryContainer-ish
    NetworkType.GSM to Color(0xFFA855F7),
    NetworkType.EDGE to Color(0xFF7C3AED),         // EDGE ≈ CDMA-deep-purple in static
    NetworkType.CDMA to SignalRed,
    NetworkType.Unknown to Color(0xFF94A3B8)
)

/**
 * Composable that returns the network palette tied to the live [ColorScheme].
 *
 * Each `NetworkType` is bound to a fixed slot in the scheme (primary,
 * primaryContainer, tertiary, …). When the user changes theme or
 * dynamic-colour toggle, the underlying scheme rebuilds and so do these
 * colours - but the *identity* of "5G is the primary slot" stays put, so
 * the legend is meaningful regardless of the active palette.
 *
 * Identity mapping (stable across themes - same as the static fallback):
 *   5G    → scheme.primary
 *   NR_NSA→ scheme.primaryContainer
 *   LTE   → scheme.tertiary
 *   HSPA  → scheme.tertiaryContainer
 *   GSM   → scheme.secondary
 *   EDGE  → scheme.secondaryContainer
 *   CDMA  → scheme.error
 *   Unknown→ scheme.outline
 */
@Composable
fun rememberNetworkColors(
    scheme: androidx.compose.material3.ColorScheme =
        androidx.compose.material3.MaterialTheme.colorScheme,
): Map<NetworkType, Color> = androidx.compose.runtime.remember(scheme) {
    mapOf(
        NetworkType.FiveG to scheme.primary,
        NetworkType.NR_NSA to scheme.primaryContainer,
        NetworkType.LTE to scheme.tertiary,
        NetworkType.HSPA to scheme.tertiaryContainer,
        NetworkType.GSM to scheme.secondary,
        NetworkType.EDGE to scheme.secondaryContainer,
        NetworkType.CDMA to scheme.error,
        NetworkType.Unknown to scheme.outline,
    )
}

private val LightColors = lightColorScheme(
    primary = Sky500,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Sky700,
    secondary = SignalGreen,
    onSecondary = Color.White,
    background = Slate50,
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Slate700,
    error = SignalRed
)

private val DarkColors = darkColorScheme(
    primary = Sky400,
    onPrimary = Slate900,
    primaryContainer = Slate700,
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = SignalGreen,
    onSecondary = Slate900,
    background = Slate900,
    onBackground = Slate100,
    surface = Slate800,
    onSurface = Slate100,
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFFCBD5F1),
    error = Color(0xFFFCA5A5)
)

/**
 * Top-level theme entry. Accepts a [themeMode] override from user prefs on
 * top of the dynamic (Material You) colour support that already exists.
 *
 * Resolution order:
 *  1. `themeMode` decides whether we're dark.
 *  2. `dynamicColor` + Android 12+ uses Material You palette.
 *  3. Otherwise, fall back to the static slate/sky palettes above.
 */
@Composable
fun SigverageTheme(
    themeMode: ThemeMode = ThemeMode.Default,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme: Boolean = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
