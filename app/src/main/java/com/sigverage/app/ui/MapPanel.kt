package com.sigverage.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import android.graphics.BitmapFactory
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import com.sigverage.app.R
import com.sigverage.app.coverage.CoverageFilterSheet
import com.sigverage.app.coverage.CoverageGridOverlay
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
 * The network filter UI (the eight Material 3 `FilterChip`s) lives in
 * [CoverageFilterSheet], a Material 3 `BottomSheetScaffold` we host at
 * the bottom of the screen. Collapsed = thin handle + 1-line summary
 * ("Filters: 5G, LTE (3 of 8 active)"); expanded = handle + summary +
 * chip strip wrapping onto multiple rows. Live preview works because
 * the sheet is non-modal and the map stays visible above it.
 */
@Composable
fun MapPanel(
    readings: List<SignalReading>,
    lastFix: FixSample?,
    coverageFilter: Set<NetworkType>,
    onToggleFilter: (NetworkType) -> Unit,
    operatorFilter: Set<String> = emptySet(),
    onToggleOperatorFilter: (String) -> Unit = {},
    focusEvents: Flow<Pair<Double, Double>> = emptyFlow(),
) {
    val context = LocalContext.current

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
        MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply {
            val indicator = ContextCompat.getDrawable(context, R.drawable.ic_my_location_indicator)
            if (indicator != null) {
                val bitmap = android.graphics.Bitmap.createBitmap(
                    indicator.intrinsicWidth.coerceAtLeast(1),
                    indicator.intrinsicHeight.coerceAtLeast(1),
                    android.graphics.Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bitmap)
                indicator.setBounds(0, 0, canvas.width, canvas.height)
                indicator.draw(canvas)
                setPersonIcon(bitmap)
            }
            enableMyLocation()
        }
    }
    val coverageOverlay = remember { CoverageGridOverlay() }

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

    // The BottomSheetScaffold (and its embedded FilterChips) lives in
    // [CoverageFilterSheet] - it consumes the same `mapView` we just
    // configured, mounts it inside the `content` slot, and shows the
    // chip strip in the `sheetContent` slot with a 72 dp peek height.
    CoverageFilterSheet(
        mapView = mapView,
        readings = readings,
        coverageFilter = coverageFilter,
        onToggleFilter = onToggleFilter,
        operatorFilter = operatorFilter,
        onToggleOperatorFilter = onToggleOperatorFilter,
    )
}