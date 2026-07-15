package com.signalspotter.app.ui.theme

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
import com.signalspotter.app.model.NetworkType

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
 * Marker colours per cellular technology. These stay stable even with
 * dynamic (Material You) colors enabled, because the map is the source of
 * truth for network identity.
 */
val NetworkColors: Map<NetworkType, Color> = mapOf(
    NetworkType.FiveG to Color(0xFF22C55E),
    NetworkType.NR_NSA to Color(0xFF34D399),
    NetworkType.LTE to Sky500,
    NetworkType.HSPA to Color(0xFFEA580C),
    NetworkType.GSM to Color(0xFFA855F7),
    NetworkType.EDGE to SignalAmber,
    NetworkType.CDMA to Color(0xFF7C3AED),
    NetworkType.Unknown to Color(0xFF94A3B8)
)

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

@Composable
fun SignalSpotterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
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
