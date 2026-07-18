package com.sigverage.app.coverage

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.view.MotionEvent
import androidx.compose.ui.graphics.toArgb
import com.sigverage.app.model.NetworkType
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * osmdroid `Overlay` that paints the coverage grid.
 *
 * Each tile draws two visual layers:
 *
 *  1. **Dominant box** - the dominant network's full HSL-hybrid fill
 *     (hue = network colour, alpha = mean dBm bucket). This is the
 *     "first impression" channel; the user reads territory by hue.
 *
 *  2. **Corner slot grid** - a 2×4 fixed-layout grid in the bottom-right
 *     of each tile, with one slot per [NetworkType]. Each slot is empty
 *     unless that network has at least one reading in the tile *and* the
 *     user has the corresponding chip enabled. Slot positions are fixed so
 *     the legend is stable across the map: top row holds the four modern
 *     networks, bottom row holds fallbacks.
 *
 * Performance contract:
 *   - **Zero allocations inside `draw()`.** One `Paint` per role, a single
 *     `Rect`, two `GeoPoint`s and two `Point`s are reused every frame.
 *   - **Viewport culling**: tiles outside `projection.boundingBox` are
 *     rejected before any pixel work happens.
 *   - **Pixel-rect culling**: tiles whose screen rectangle is entirely
 *     off-screen are skipped after projection.
 *   - **Tile-size threshold**: corner grid is suppressed on tiles below
 *     ~22 dp either dimension (rare at zoom ≥ 16; pure safety net).
 */
class CoverageGridOverlay(
    initialStorageZoom: Int = DEFAULT_STORAGE_ZOOM,
) : Overlay() {

    var storageZoom: Int = initialStorageZoom
        set(value) {
            field = value.coerceIn(MIN_STORAGE_ZOOM, MAX_STORAGE_ZOOM)
        }

    private var stats: Map<TileId, CellStats> = emptyMap()
    private var allowed: Set<NetworkType> = NetworkType.entries.toSet()
    private var allowedOperators: Set<String> = emptySet() // empty = show all

    /** Currently selected (tapped) tile, highlighted in [draw]. */
    private var selectedTile: TileId? = null

    /**
     * Invoked when the user taps a drawn tile, with that tile's id and the
     * stats behind it. The host uses it to open a details sheet. Only tiles
     * that are actually painted (pass the network + operator filters) fire
     * this; a tap on empty map or a filtered-out tile fires
     * [onSelectionCleared] instead.
     */
    var onTileTap: ((TileId, CellStats) -> Unit)? = null

    /** Invoked when a tap lands on no drawn tile, so the host can dismiss. */
    var onSelectionCleared: (() -> Unit)? = null

    /**
     * Network → colour map. Defaults to the static [com.sigverage.app.ui.theme.NetworkColors]
     * fallback so the overlay still draws correctly if the host forgets to
     * inject the live palette. When the active `ColorScheme` changes (light/dark,
     * dynamic-colour toggle, future wallpaper-derived Material You), the host
     * should call this with the result of `rememberNetworkColors()`.
     */
    private var palette: Map<NetworkType, androidx.compose.ui.graphics.Color> =
        com.sigverage.app.ui.theme.NetworkColors

    // Pre-allocated scratch. Never reassigned in draw().
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(56, 0, 0, 0)
    }
    private val slotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    // Two-layer selection outline: a dark halo under a white stroke so the
    // highlight stays visible over any tile colour or map background.
    private val selectHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(160, 0, 0, 0)
    }
    private val selectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
    }
    private val tlGeo = GeoPoint(0.0, 0.0)
    private val brGeo = GeoPoint(0.0, 0.0)
    private val tlPt = Point()
    private val brPt = Point()
    private val tileRect = Rect()

    fun setStats(newStats: Map<TileId, CellStats>) {
        stats = newStats
    }

    fun setAllowed(newAllowed: Set<NetworkType>) {
        allowed = newAllowed
    }

    fun setAllowedOperators(newAllowed: Set<String>) {
        allowedOperators = newAllowed
    }

    /** Set (or clear, with `null`) the highlighted tile. */
    fun setSelectedTile(tile: TileId?) {
        selectedTile = tile
    }

    /** Inject the live palette produced by `rememberNetworkColors()`. */
    fun setPalette(newPalette: Map<NetworkType, androidx.compose.ui.graphics.Color>) {
        palette = newPalette
    }

    fun update(
        newStats: Map<TileId, CellStats>,
        newAllowed: Set<NetworkType>,
        newAllowedOperators: Set<String> = emptySet(),
        newPalette: Map<NetworkType, androidx.compose.ui.graphics.Color> = palette,
    ) {
        stats = newStats
        allowed = newAllowed
        allowedOperators = newAllowedOperators
        palette = newPalette
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || stats.isEmpty()) return
        val projection = mapView.projection
        val bb = projection.boundingBox
        val mapW = mapView.width
        val mapH = mapView.height
        if (mapW <= 0 || mapH <= 0) return

        val density = mapView.context.resources.displayMetrics.density
        strokePaint.strokeWidth = STROKE_WIDTH_DP * density
        selectHaloPaint.strokeWidth = SELECT_STROKE_WIDTH_DP * density + 2f * density
        selectPaint.strokeWidth = SELECT_STROKE_WIDTH_DP * density

        for ((tile, cellStats) in stats) {
            if (tile.zoom != storageZoom) continue

            // Skip tiles that have no readings from allowed operators.
            if (allowedOperators.isNotEmpty() && cellStats.operators.none { it in allowedOperators }) continue

            val pick = pickDominant(cellStats, allowed) ?: continue
            val bucket = bucketFor(pick.second.meanDbm)
            fillPaint.color = colorFor(pick.first, bucket, palette).toArgb()

            val bounds = tileBounds(tile)

            // Latitude cull.
            if (bounds.northLat < bb.latSouth || bounds.southLat > bb.latNorth) continue

            tlGeo.setCoords(bounds.northLat, bounds.westLng)
            brGeo.setCoords(bounds.southLat, bounds.eastLng)
            projection.toPixels(tlGeo, tlPt)
            projection.toPixels(brGeo, brPt)

            val left = tlPt.x
            val top = tlPt.y
            val right = brPt.x
            val bottom = brPt.y

            // Screen-rect cull.
            if (right < 0 || bottom < 0 || left > mapW || top > mapH) continue

            tileRect.set(left, top, right, bottom)
            canvas.drawRect(tileRect, fillPaint)
            canvas.drawRect(tileRect, strokePaint)

            // Layer 2: corner slot grid (Option 2 multi-network encoding).
            drawSlotGrid(canvas, density, tileRect, cellStats)

            // Layer 3: selection highlight for the tapped tile.
            if (tile == selectedTile) {
                canvas.drawRect(tileRect, selectHaloPaint)
                canvas.drawRect(tileRect, selectPaint)
            }
        }
    }

    /**
     * Tap-to-select: map the tap to its storage-zoom tile, and if that tile
     * is currently drawn (present in [stats] and passing the network +
     * operator filters), select it and notify [onTileTap]. A tap on empty
     * map or a filtered-out tile clears the selection via [onSelectionCleared].
     */
    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        if (stats.isEmpty()) return false
        val geo = mapView.projection.fromPixels(e.x.toInt(), e.y.toInt())
        val tile = latLngToTile(geo.latitude, geo.longitude, storageZoom)
        val cell = stats[tile]
        val hit = cell != null &&
            (allowedOperators.isEmpty() || cell.operators.any { it in allowedOperators }) &&
            pickDominant(cell, allowed) != null
        if (hit) {
            selectedTile = tile
            onTileTap?.invoke(tile, cell!!)
        } else {
            if (selectedTile == null) return false
            selectedTile = null
            onSelectionCleared?.invoke()
        }
        mapView.invalidate()
        return true
    }

    /**
     * Paint the 2×4 corner slot grid in the bottom-right of `tileRect`.
     *
     * Behaviour per slot:
     *   - **No aggregate present** → invisible.
     *   - **Aggregate present but network filtered out**  → invisible
     *     (must respect the chip strip; otherwise disabled networks
     *     would still appear, defeating the filter).
     *   - **Aggregate present and allowed** → filled circle, network's
     *     hue, alpha-modulated by mean dBm bucket.
     *
     * Skipped entirely when the tile is below [MIN_TILE_DP_FOR_GRID] in
     * either dimension - there is no room to render the grid cleanly.
     */
    private fun drawSlotGrid(
        canvas: Canvas,
        density: Float,
        tileRect: Rect,
        cellStats: CellStats,
    ) {
        val tileMinDp = minOf(tileRect.width(), tileRect.height()).toFloat() / density
        if (tileMinDp < MIN_TILE_DP_FOR_GRID) return

        val pitchPx = SLOT_PITCH_DP * density
        val slotRadiusPx = SLOT_RADIUS_DP * density
        val marginPx = SLOT_MARGIN_DP * density
        val gridWidthPx = SLOT_COLS * pitchPx - SLOT_GAP_DP * density
        val gridHeightPx = SLOT_ROWS * pitchPx - SLOT_GAP_DP * density
        val originX = tileRect.right - gridWidthPx - marginPx
        val originY = tileRect.bottom - gridHeightPx - marginPx

        for (slot in SLOT_LAYOUT) {
            val agg = cellStats.perNetwork[slot.type] ?: continue
            if (slot.type !in allowed) continue
            slotPaint.color = colorFor(slot.type, bucketFor(agg.meanDbm), palette).toArgb()
            val cx = originX + slot.col * pitchPx + slotRadiusPx
            val cy = originY + slot.row * pitchPx + slotRadiusPx
            canvas.drawCircle(cx, cy, slotRadiusPx, slotPaint)
        }
    }

    companion object {
        /** Fixed storage zoom - the single source of truth for coverage
         *  cell size. Z=20 in Web-Mercator gives ~38 m tiles at the equator
         *  and ~27 m east-west at 45° latitude. The user-facing label is
         *  "50 m cells" - a rounded mid-latitude figure that matches what
         *  users actually see in the field. **Exact** 50 m would require
         *  a fractional-zoom refactor (the `aggregate()` function and the
         *  `TileId` data class in [com.sigverage.app.coverage.CoverageModel]
         *  currently use `Int` zooms); this constant sits at the integer
         *  zoom whose cells are slightly finer than 50 m at typical
         *  mid-latitudes, which is the closest integer Mercator step ≤ 50 m.
         *  There is no longer a UI slider. */
        const val DEFAULT_STORAGE_ZOOM = 20

        /** Lower clamp for storage zoom. Reserved for a possible future
         *  re-introduction of a granularity control; no caller currently
         *  consumes this. Kept as part of the public API rather than
         *  deleted outright to preserve the option. */
        const val MIN_STORAGE_ZOOM = 12

        /** Upper clamp for storage zoom. Reserved for a possible future
         *  re-introduction of a granularity control; mirrors
         *  [DEFAULT_STORAGE_ZOOM] for now. Kept as part of the public
         *  API rather than deleted outright. */
        const val MAX_STORAGE_ZOOM = 20

        // ---- Slot grid geometry (private to companion) ----

        /** Skip corner grid below this tile size - it simply doesn't fit. */
        private const val MIN_TILE_DP_FOR_GRID = 22f

        private const val SLOT_COLS = 4
        private const val SLOT_ROWS = 2
        /** Distance from one slot centre to the next. */
        private const val SLOT_PITCH_DP = 6.5f
        /** Implicit gap = pitch - 2 * radius. */
        private const val SLOT_RADIUS_DP = 2.5f
        private const val SLOT_GAP_DP = 1.5f
        /** Distance from the tile edge to the closest slot centre. */
        private const val SLOT_MARGIN_DP = 2f
        private const val STROKE_WIDTH_DP = 0.75f
        /** Stroke width of the white selection outline (halo adds ~2dp more). */
        private const val SELECT_STROKE_WIDTH_DP = 2f

        /**
         * Slot layout: top row holds the four modern networks, bottom row
         * holds fallbacks. Slot positions are fixed so the user learns the
         * legend once and reads it instinctively.
         */
        private data class Slot(val type: NetworkType, val col: Int, val row: Int)

        private val SLOT_LAYOUT: List<Slot> = listOf(
            Slot(NetworkType.FiveG, col = 0, row = 0),
            Slot(NetworkType.NR_NSA, col = 1, row = 0),
            Slot(NetworkType.LTE, col = 2, row = 0),
            Slot(NetworkType.HSPA, col = 3, row = 0),
            Slot(NetworkType.GSM, col = 0, row = 1),
            Slot(NetworkType.EDGE, col = 1, row = 1),
            Slot(NetworkType.CDMA, col = 2, row = 1),
            Slot(NetworkType.Unknown, col = 3, row = 1),
        )
    }
}
