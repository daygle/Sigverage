package com.sigverage.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
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
private val SignalOrange = Color(0xFFF97316)
private val SignalOrangeDark = Color(0xFFEA580C)
private val SignalRed = Color(0xFFEF4444)
private val Slate900 = Color(0xFF0F172A)
private val Slate800 = Color(0xFF1E293B)
private val Slate700 = Color(0xFF334155)
private val Slate100 = Color(0xFFF1F5F9)
private val Slate50 = Color(0xFFFAFAFC)

/**
 * Distinct colours for each cellular technology, chosen so every network
 * type gets its own immediately-recognisable hue:
 *
 *   5G        → blue
 *   4G (LTE)  → green
 *   3G (HSPA) → amber / yellow
 *   2G (GSM)  → orange
 *   EDGE      → deeper orange
 *   CDMA      → red
 *   Unknown   → neutral gray
 *
 * These are the **built-in defaults**. The user can override any network's
 * colour from Settings → Network Colours; the resolved palette (defaults with
 * user overrides applied) flows through [LocalNetworkColors] and is what
 * `rememberNetworkColors()` returns inside a Compose scope.
 *
 * Use this map directly only from non-Compose contexts (DAOs, plain helpers)
 * where we can't read the CompositionLocal, or as the fallback default.
 */
val NetworkColors: Map<NetworkType, Color> = mapOf(
    NetworkType.FiveG to Sky500,
    NetworkType.NR_NSA to Color(0xFFBAE6FD),
    NetworkType.LTE to SignalGreen,
    NetworkType.HSPA to SignalAmber,
    NetworkType.GSM to SignalOrange,
    NetworkType.EDGE to SignalOrangeDark,
    NetworkType.CDMA to SignalRed,
    NetworkType.Unknown to Slate700,
)

/**
 * The network palette in effect for the current Compose tree. Defaults to the
 * built-in [NetworkColors] and is overridden at the activity root with the
 * user's resolved palette (built-in defaults + any per-network overrides the
 * user saved in Settings → Network Colours). Reading it from a composable
 * recomposes that composable whenever the palette changes, so the map, legend,
 * chips and badges all repaint the instant a colour is edited.
 */
val LocalNetworkColors = compositionLocalOf { NetworkColors }

/**
 * Composable that returns the network palette — one colour per [NetworkType].
 *
 * Historically this was a fixed, hardcoded palette so every technology got its
 * own immediately-recognisable hue (5G blue, 4G green, 3G amber, 2G orange,
 * …). Those hues remain the **defaults**, but the user can now recolour any
 * network from Settings; this reads the resolved palette from
 * [LocalNetworkColors] rather than returning a constant.
 */
@Composable
fun rememberNetworkColors(): Map<NetworkType, Color> = LocalNetworkColors.current

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
    error = SignalRed,
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
    error = Color(0xFFFCA5A5),
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
    content: @Composable () -> Unit,
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
