package com.signalspotter.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.signalspotter.app.R
import com.signalspotter.app.coverage.CoverageFilterChips
import com.signalspotter.app.coverage.CoverageGridOverlay
import com.signalspotter.app.coverage.aggregate
import com.signalspotter.app.location.FixSample
import com.signalspotter.app.model.NetworkType
import com.signalspotter.app.model.SignalReading
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * Map view with the coverage grid overlay and filter chips.
 *
 *  - **Pins are gone.** The MapPanel now draws a single `CoverageGridOverlay`
 *    that paints one sliding-pane-sized quad per tile using the HSL hybrid
 *    encoding (hue = dominant network, alpha = signal strength).
 *  - **Zooms adaptively.** A `MapListener` tracks the current zoom and the
 *    overlay's `storageZoom` is clamped into a useful range, so the grid
 *    feels "alive" at every zoom the user lands on.
 *  - **Filter chips drive the overlay directly.** Toggle visibility in memory
 *    — no re-query against Room needed for snappy UX.
 *
 *  The `CoverageGridOverlay` obeys the contract documented in `CoverageGrid.kt`:
 *  zero allocations inside `draw()`, viewport + pixel-rect culling.
 */
@Composable
fun MapPanel(
    readings: List<SignalReading>,
    lastFix: FixSample?,
    coverageFilter: Set<NetworkType>,
    onToggleFilter: (NetworkType) -> Unit,
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
    val coverageOverlay = remember { CoverageGridOverlay() }

    /** Mirrored from `MapListener.onZoom` so a `LaunchedEffect` can react. */
    val zoom = remember { mutableStateOf(mapView.zoomLevelDouble) }

    DisposableEffect(Unit) {
        // Order matters: the coverage grid is drawn first (below), so the
        // "you-are-here" dot from `MyLocationNewOverlay` sits clearly on top.
        mapView.overlays.add(coverageOverlay)
        mapView.overlays.add(locationOverlay)
        mapView.onResume()

        val listener = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean = false
            override fun onZoom(event: ZoomEvent?): Boolean {
                zoom.value = event?.zoomLevel ?: mapView.zoomLevelDouble
                return false
            }
        }
        mapView.addMapListener(listener)

        onDispose {
            mapView.removeMapListener(listener)
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

    // Re-aggregate on either readings-change or zoom-change. Storage zoom
    // adapts to the current map zoom so the grid feels at home at every
    // zoom level (clamped to a sensible band so we don't blow GC at z=22).
    LaunchedEffect(readings, zoom.value) {
        val storageZoom = zoom.value.toInt().coerceIn(
            CoverageGridOverlay.MIN_STORAGE_ZOOM,
            CoverageGridOverlay.MAX_STORAGE_ZOOM
        )
        coverageOverlay.storageZoom = storageZoom
        coverageOverlay.setStats(aggregate(readings, storageZoom))
        mapView.invalidate()
    }

    // Filter toggles don't need re-aggregation; just change visibility and
    // invalidate the canvas.
    LaunchedEffect(coverageFilter) {
        coverageOverlay.setAllowed(coverageFilter)
        mapView.invalidate()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        CoverageFilterChips(
            selected = coverageFilter,
            onToggle = onToggleFilter,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp),
        )

        if (readings.isEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 4.dp,
            ) {
                Text(
                    text = stringResource(R.string.coverage_empty_hint),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
