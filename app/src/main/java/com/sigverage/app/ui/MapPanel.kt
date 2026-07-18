package com.sigverage.app.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.sigverage.app.R
import com.sigverage.app.coverage.CoverageGridOverlay
import com.sigverage.app.coverage.CoverageMapScreen
import com.sigverage.app.coverage.aggregate
import com.sigverage.app.location.FixSample
import com.sigverage.app.model.NetworkType
import com.sigverage.app.model.SignalReading
import com.sigverage.app.ui.theme.rememberNetworkColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * Map view with the coverage grid overlay.
 *
 * Storage zoom and visible map zoom are **fully decoupled**:
 *
 *  - The coverage grid is binned at the fixed
 *    [CoverageGridOverlay.DEFAULT_STORAGE_ZOOM] = 20 (~38 m tiles at the
 *    equator, ~50 m at mid-latitudes).
 *  - The visible map's `controller.zoomLevel` is whatever the user
 *    pinches/pans to, independent of the storage zoom. When the user
 *    pans, the same tile stats re-render at new screen positions; we do
 *    NOT need a MapListener to mirror zoom changes.
 *
 * Re-aggregation only fires when the *data* shape changes
 * (`readings`-change). Filter toggles don't need re-aggregation; they
 * just change the in-memory `allowed` set; map zoom/pan stays cheap.
 *
 * The map's controls all float directly on the map surface in
 * [CoverageMapScreen]: a top filter bar (quick network toggles + a
 * "Filters" pill that opens the full network/operator sheet), a
 * bottom-centre "Capture here" FAB (plus a pause button while sampling),
 * and bottom-right zoom/recenter FABs. The quick network toggles sit on
 * the map with no scrim, so toggling one previews the coverage grid live.
 */
@Composable
fun MapPanel(
    readings: List<SignalReading>,
    lastFix: FixSample?,
    coverageFilter: Set<NetworkType>,
    onToggleFilter: (NetworkType) -> Unit,
    isSampling: Boolean,
    onCapture: () -> Unit,
    onStopSampling: () -> Unit,
    operatorFilter: Set<String> = emptySet(),
    onToggleOperatorFilter: (String) -> Unit = {},
    focusEvents: Flow<Pair<Double, Double>> = emptyFlow(),
) {
    val context = LocalContext.current
    val msgNoFix = stringResource(R.string.map_recenter_no_fix)

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
            // Start at the storage zoom so the user opens to the granularity
            // they want by default; they can pinch from there.
            controller.setZoom(CoverageGridOverlay.DEFAULT_STORAGE_ZOOM.toDouble())
            controller.setCenter(GeoPoint(37.7749, -122.4194))
        }
    }
    val locationOverlay = remember {
        // Throttle the blue-dot provider. osmdroid's GpsMyLocationProvider
        // defaults to min-time 0 / min-distance 0 and polls GPS *and* network
        // as fast as the OS allows for as long as the map is visible - a real
        // drain even when not recording. Cap it so merely viewing the map
        // doesn't hammer the GPS radio. Must be set before enableMyLocation().
        val locationProvider = GpsMyLocationProvider(context).apply {
            locationUpdateMinTime = MAP_LOCATION_MIN_TIME_MS
            locationUpdateMinDistance = MAP_LOCATION_MIN_DISTANCE_M
        }
        MyLocationNewOverlay(locationProvider, mapView).apply {
            val indicator = ContextCompat.getDrawable(context, R.drawable.ic_my_location_indicator)
            if (indicator != null) {
                val bitmap = indicator.toBitmap(
                    width = indicator.intrinsicWidth.coerceAtLeast(1),
                    height = indicator.intrinsicHeight.coerceAtLeast(1),
                    config = android.graphics.Bitmap.Config.ARGB_8888
                )
                setPersonIcon(bitmap)
            }
            enableMyLocation()
        }
    }
    val coverageOverlay = remember { CoverageGridOverlay() }

    // Scale bar: a modern-map staple that lets the user gauge the real-world
    // size of each coverage tile at the current zoom. Nautical/imperial off;
    // metric matches the tile-size docs on CoverageGridOverlay.
    val scaleBarOverlay = remember {
        ScaleBarOverlay(mapView).apply {
            setAlignBottom(true)
            setScaleBarOffset(24, 24)
        }
    }
    // Compass overlay: shows map orientation and, on tap, resets north-up.
    val compassOverlay = remember {
        CompassOverlay(context, InternalCompassOrientationProvider(context), mapView).apply {
            enableCompass()
        }
    }

    // Marker palette tied to the live ColorScheme. Recomputes whenever the
    // user toggles light/dark or dynamic-colour, and we push it into the
    // osmdroid Overlay so the next paint uses the new colours.
    val networkColors = rememberNetworkColors()

    DisposableEffect(Unit) {
        // Coverage grid is drawn below the location overlay so the
        // "you-are-here" dot sits clearly on top.
        mapView.overlays.add(coverageOverlay)
        mapView.overlays.add(locationOverlay)
        mapView.overlays.add(scaleBarOverlay)
        mapView.overlays.add(compassOverlay)
        mapView.onResume()

        onDispose {
            compassOverlay.disableCompass()
            mapView.overlays.remove(compassOverlay)
            mapView.overlays.remove(scaleBarOverlay)
            mapView.overlays.remove(locationOverlay)
            mapView.overlays.remove(coverageOverlay)
            locationOverlay.disableMyLocation()
            mapView.onPause()
            mapView.onDetach()
        }
    }

    // Centre on the most recent fix whenever it changes.
    LaunchedEffect(lastFix?.latitude, lastFix?.longitude) {
        lastFix?.let { fix ->
            mapView.controller.animateTo(GeoPoint(fix.latitude, fix.longitude))
        }
    }

    // Centre on a user-requested coordinate (tap-row-jump-to-map). Channel-
    // backed so identical consecutive targets still re-animate; buffered so
    // a request fired while MapPanel isn't composed (user on List tab) gets
    // picked up the moment MapPanel enters composition. We deliberately do
    // NOT setZoom() before animateTo, to mirror the GPS-fix centering path
    // above - the user keeps whatever zoom they last pinched to.
    LaunchedEffect(Unit) {
        focusEvents.collect { (latitude, longitude) ->
            mapView.controller.animateTo(GeoPoint(latitude, longitude))
        }
    }

    // Theme/dynamic toggle -> swap palette into the overlay.
    LaunchedEffect(networkColors) {
        coverageOverlay.setPalette(networkColors)
        mapView.invalidate()
    }

    // Re-aggregate ONLY when the readings list shape changes. The storage
    // zoom is a single fixed constant (CoverageGridOverlay.DEFAULT_STORAGE_ZOOM),
    // so it isn't a recomposition key. Pan/zoom of the visible map doesn't
    // need re-aggregation either; draw() re-projects the same stats at new
    // screen positions for free.
    LaunchedEffect(readings) {
        coverageOverlay.setStats(
            aggregate(readings, CoverageGridOverlay.DEFAULT_STORAGE_ZOOM)
        )
        mapView.invalidate()
    }

    // Filter toggles don't need re-aggregation; just change visibility.
    LaunchedEffect(coverageFilter) {
        coverageOverlay.setAllowed(coverageFilter)
        mapView.invalidate()
    }

    LaunchedEffect(operatorFilter) {
        coverageOverlay.setAllowedOperators(operatorFilter)
        mapView.invalidate()
    }

    // [CoverageMapScreen] consumes the same `mapView` we just configured,
    // mounts it full-bleed, and overlays every control (filter bar,
    // capture/pause, zoom/recenter) directly on the map surface.
    CoverageMapScreen(
        mapView = mapView,
        readings = readings,
        coverageFilter = coverageFilter,
        onToggleFilter = onToggleFilter,
        operatorFilter = operatorFilter,
        onToggleOperatorFilter = onToggleOperatorFilter,
        isSampling = isSampling,
        onCapture = onCapture,
        onStopSampling = onStopSampling,
        onZoomIn = { mapView.controller.zoomIn() },
        onZoomOut = { mapView.controller.zoomOut() },
        onRecenter = {
            // Prefer the live blue-dot fix from the location overlay; fall
            // back to the last recorded fix so the button still works before
            // the overlay's provider has produced its first update.
            val target = locationOverlay.myLocation
                ?: lastFix?.let { GeoPoint(it.latitude, it.longitude) }
            if (target != null) {
                mapView.controller.animateTo(target)
            } else {
                Toast.makeText(
                    context,
                    msgNoFix,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        },
    )
}

/** Minimum time between blue-dot location updates on the map (ms). */
private const val MAP_LOCATION_MIN_TIME_MS = 5_000L

/** Minimum movement between blue-dot location updates on the map (m). */
private const val MAP_LOCATION_MIN_DISTANCE_M = 10f