package com.sigverage.app.ui

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.sigverage.app.R
import com.sigverage.app.coverage.CoverageFilterChips
import com.sigverage.app.coverage.CoverageGridOverlay
import com.sigverage.app.coverage.aggregate
import com.sigverage.app.location.FixSample
import com.sigverage.app.model.NetworkType
import com.sigverage.app.model.SignalReading
import com.sigverage.app.ui.theme.rememberNetworkColors
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * Map view with the coverage grid overlay and filter chips.
 *
 * Storage zoom and visible map zoom are **fully decoupled**:
 *
 *  - `coverageZoom` (driven by the AppBar slider) chooses how finely
 *    readings are binned into Mercator tiles. The default is
 *    [CoverageGridOverlay.DEFAULT_STORAGE_ZOOM] = 19, ≈75 m per side.
 *  - The visible map's `controller.zoomLevel` is whatever the user
 *    pinches/pans to, independent of the storage zoom. When the user
 *    pans, the same tile stats re-render at new screen positions; we
 *    do NOT need a MapListener to mirror zoom changes.
 *
 *  Re-aggregation only fires when the *data* shape changes
 *  (`readings`-change or `coverageZoom`-change). Toggling a network chip
 *  just changes the in-memory `allowed` set; map zoom/pan stays cheap.
 */
@Composable
fun MapPanel(
    readings: List<SignalReading>,
    lastFix: FixSample?,
    coverageFilter: Set<NetworkType>,
    onToggleFilter: (NetworkType) -> Unit,
    coverageZoom: Int,
    onCoverageZoomChange: (Int) -> Unit,
) {
    val context = LocalContext.current

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
            // Start at the new default so the user opens to the granularity
            // they want by default; they can pinch from there.
            controller.setZoom(CoverageGridOverlay.DEFAULT_STORAGE_ZOOM.toDouble())
            controller.setCenter(GeoPoint(37.7749, -122.4194))
        }
    }
    val locationOverlay = remember {
        MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply {
            enableMyLocation()
        }
    }
    val coverageOverlay = remember { CoverageGridOverlay(initialStorageZoom = coverageZoom) }

    // Marker palette tied to the live ColorScheme. Recomputes whenever the
    // user toggles light/dark or dynamic-colour, and we push it into the
    // osmdroid Overlay so the next paint uses the new colours.
    val networkColors = rememberNetworkColors()

    DisposableEffect(Unit) {
        // Coverage grid is drawn below the location overlay so the
        // "you-are-here" dot sits clearly on top.
        mapView.overlays.add(coverageOverlay)
        mapView.overlays.add(locationOverlay)
        mapView.onResume()

        onDispose {
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

    // Slider → storage zoom, sync immediately.
    LaunchedEffect(coverageZoom) {
        coverageOverlay.storageZoom = coverageZoom
        mapView.invalidate()
    }

    // Theme/dynamic toggle → swap palette into the overlay.
    LaunchedEffect(networkColors) {
        coverageOverlay.setPalette(networkColors)
        mapView.invalidate()
    }

    // Re-aggregate ONLY on readings-change or slider-change. Pan/zoom of
    // the visible map doesn't need re-aggregation; draw() re-projects the
    // same stats at new screen positions for free.
    LaunchedEffect(readings, coverageZoom) {
        coverageOverlay.setStats(aggregate(readings, coverageZoom))
        mapView.invalidate()
    }

    // Filter toggles don't need re-aggregation; just change visibility.
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

    // Suppress unused-parameter warning on the callback when the parent
    // doesn't wire it through. (Future taps could open the same dialog
    // from the corner of the map.)
    @Suppress("UNUSED_EXPRESSION")
    onCoverageZoomChange
}
