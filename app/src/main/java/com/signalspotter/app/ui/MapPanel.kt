package com.signalspotter.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.signalspotter.app.R
import com.signalspotter.app.location.FixSample
import com.signalspotter.app.model.NetworkType
import com.signalspotter.app.model.SignalReading
import com.signalspotter.app.ui.theme.NetworkColors
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * Wraps osmdroid's MapView in Compose, draws colour-coded markers for each
 * reading, adds a "you-are-here" overlay, and exposes the legend for the
 * colour-to-network mapping.
 *
 * Marker bitmap generation is cached in a `mutableMapOf` keyed by
 * `(networkType, signalBucket)` so recomposition doesn't thrash the heap.
 */
@Composable
fun MapPanel(
    readings: List<SignalReading>,
    lastFix: FixSample?,
    onMarkerClick: (SignalReading) -> Unit
) {
    val context = LocalContext.current

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
            controller.setZoom(14.0)
            controller.setCenter(GeoPoint(37.7749, -122.4194))
        }
    }
    val locationOverlay = remember {
        MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply {
            enableMyLocation()
        }
    }
    val markerCache = remember { mutableMapOf<String, Drawable>() }

    DisposableEffect(Unit) {
        mapView.overlays.add(locationOverlay)
        mapView.onResume()
        onDispose {
            mapView.overlays.remove(locationOverlay)
            locationOverlay.disableMyLocation()
            mapView.onPause()
            mapView.onDetach()
        }
    }

    LaunchedEffect(lastFix?.latitude, lastFix?.longitude) {
        lastFix?.let { fix ->
            mapView.controller.animateTo(GeoPoint(fix.latitude, fix.longitude))
        }
    }

    LaunchedEffect(readings) {
        // Remove only the markers WE added (those whose relatedObject is a
        // SignalReading). The MyLocationNewOverlay's bubble is untouched.
        val toRemove = mapView.overlays
            .filterIsInstance<Marker>()
            .filter { it.relatedObject is SignalReading }
        mapView.overlays.removeAll(toRemove)
        readings.forEach { reading ->
            val bucket = signalStrengthBucket(reading.signalDbm)
            val key = "${reading.networkType.name}|$bucket"
            val drawable = markerCache.getOrPut(key) {
                buildMarkerBitmap(context, reading.networkType, bucket)
            }
            val marker = Marker(mapView).apply {
                position = GeoPoint(reading.latitude, reading.longitude)
                title = reading.networkType.label
                snippet = formatSnippet(reading)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = drawable
                relatedObject = reading
                setOnMarkerClickListener { _, _ ->
                    onMarkerClick(reading); true
                }
            }
            mapView.overlays.add(marker)
        }
        mapView.invalidate()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        if (readings.isEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 4.dp
            ) {
                Text(
                    text = stringResource(R.string.empty_map_hint),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            Legend(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            )
        }
    }
}

@Composable
private fun Legend(modifier: Modifier = Modifier) {
    val entries = listOf(
        NetworkType.FiveG to R.string.legend_5g,
        NetworkType.NR_NSA to R.string.legend_5g,
        NetworkType.LTE to R.string.legend_lte,
        NetworkType.HSPA to R.string.legend_hspa,
        NetworkType.GSM to R.string.legend_gsm,
        NetworkType.EDGE to R.string.legend_edge,
        NetworkType.CDMA to R.string.legend_cdma,
        NetworkType.Unknown to R.string.legend_unknown
    ).distinctBy { it.first }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.legend_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            entries.forEach { (type, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(NetworkColors[type] ?: Color.Gray, shape = RoundedCornerShape(50))
                    )
                    Text(
                        text = stringResource(label),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Text(
                text = stringResource(R.string.legend_size_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

/** Bucket of signal strength for marker sizing. */
private enum class SignalBucket { Strong, Ok, Weak, Unknown }

private fun signalStrengthBucket(dbm: Int?): SignalBucket = when {
    dbm == null -> SignalBucket.Unknown
    dbm >= -90 -> SignalBucket.Strong
    dbm >= -105 -> SignalBucket.Ok
    else -> SignalBucket.Weak
}

/** Generate a marker bitmap. Cached by `(networkType, bucket)`. */
private fun buildMarkerBitmap(
    context: android.content.Context,
    type: NetworkType,
    bucket: SignalBucket
): Drawable {
    val baseColor = NetworkColors[type]?.let { AColor.argb(
        (it.alpha * 255).toInt(), (it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt()
    ) } ?: AColor.GRAY

    val dp = context.resources.displayMetrics.density
    val outerR = when (bucket) {
        SignalBucket.Strong -> 22f * dp
        SignalBucket.Ok -> 16f * dp
        SignalBucket.Weak -> 11f * dp
        SignalBucket.Unknown -> 14f * dp
    }
    val sizePx = (outerR * 2 + 4f * dp).toInt()
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = sizePx / 2f
    val cy = sizePx / 2f

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = baseColor }
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AColor.argb(60, 0, 0, 0)
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
    }
    val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AColor.WHITE }

    canvas.drawCircle(cx, cy, outerR, fill)
    canvas.drawCircle(cx, cy, outerR, ring)
    canvas.drawCircle(cx, cy, outerR * 0.4f, core)

    return BitmapDrawable(context.resources, bitmap)
}

private fun formatSnippet(r: SignalReading): String {
    val dbm = r.signalDbm?.let { "$it dBm" } ?: "–"
    val op = r.operatorName ?: "–"
    val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
        .format(java.util.Date(r.timestamp))
    return "$op · $dbm · $time"
}
