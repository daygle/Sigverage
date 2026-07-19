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
 * Each tile draws five visual layers:
 *
 *  1. **Segmented fill** - horizontal bands, one per network type, with
 *     height proportional to reading count. 4G, 5G, and every other
 *     network present each get their own coloured stripe, so the user sees
 *     the full mix at a glance. Filtered-out networks are skipped.
 *
 *  2. **Outline stroke** - thin dark border around each tile so adjacent
 *     tiles remain distinct regardless of their fill colours.
 *
 *  3. **Corner slot grid** - a 2×4 fixed-layout grid in the bottom-right
 *     of each tile, with one slot per [NetworkType]. Each slot is empty
 *     unless that network has at least one reading in the tile *and* the
 *     user has the corresponding chip enabled. Slot positions are fixed so
 *     the legend is stable across the map: top row holds the four modern
 *     networks, bottom row holds fallbacks.
 *
 *  4. **Mean-dBm label** - the dominant network's mean signal, printed as a
 *     small white-on-halo number in the top-left corner. It is the exact
 *     value behind the fill's opacity bucket, so a square reads as
 *     hue = network, opacity = strength band, number = mean dBm. Suppressed
 *     when the cell has no usable signal value or the tile is too small.
 *
 *  5. **Selection highlight** - a two-layer (dark halo + white stroke)
 *     outline on the tapped tile.
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
    private var hiddenOperators: Set<String> = emptySet() // empty = nothing hidden, show all

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
    // Mean-dBm corner label: white glyphs over a dark halo so the number
    // stays legible on any tile colour or map background. Same two-layer
    // trick as the selection outline.
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
    }
    private val labelHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(180, 0, 0, 0)
        textAlign = Paint.Align.LEFT
    }
    // Scratch buffer for rendering the mean dBm without allocating a String
    // per tile per frame. Widest value is "-140" - 4 chars - so 8 is ample.
    private val numBuf = CharArray(8)
    // Pre-allocated buffer for sorting visible network indices in
    // drawSegmentedFill. Never allocates inside draw().
    private val segmentIdxBuf = IntArray(NetworkType.entries.size)
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

    fun setHiddenOperators(newHidden: Set<String>) {
        hiddenOperators = newHidden
    }

    /** Set (or clear, with `null`) the highlighted tile. */
    fun setSelectedTile(tile: TileId?) {
        selectedTile = tile
    }

    /** Inject the live palette produced by `rememberNetworkColors()`. */
    fun setPalette(newPalette: Map<NetworkType, androidx.compose.ui.graphics.Color>) {
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
        labelPaint.textSize = LABEL_TEXT_SIZE_DP * density
        labelHaloPaint.textSize = LABEL_TEXT_SIZE_DP * density
        labelHaloPaint.strokeWidth = LABEL_HALO_WIDTH_DP * density

        for ((tile, cellStats) in stats) {
            if (tile.zoom != storageZoom) continue

            // Skip tiles where any operator has been hidden by the user.
            if (hiddenOperators.isNotEmpty() && cellStats.operators.any { it in hiddenOperators }) continue

            val pick = pickDominant(cellStats, allowed) ?: continue

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

            // Layer 2: stacked horizontal segments, one per network type,
            // with height proportional to reading count. Every network that
            // has readings in this tile gets its own coloured band.
            drawSegmentedFill(canvas, tileRect, cellStats)

            canvas.drawRect(tileRect, strokePaint)

            // Layer 3: corner slot grid — small circles in the bottom-right
            // corner showing every network type present.
            drawSlotGrid(canvas, density, tileRect, cellStats)

            // Layer 4: dominant network's mean dBm, printed in the top-left
            // corner. This is the exact number behind the fill's opacity
            // bucket, so the square becomes self-describing (hue = network,
            // opacity = strength band, number = mean signal).
            drawMeanLabel(canvas, density, tileRect, pick.second.meanDbm)

            // Layer 5: selection highlight for the tapped tile.
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
            (hiddenOperators.isEmpty() || cell.operators.none { it in hiddenOperators }) &&
            pickDominant(cell, allowed) != null
        if (hit) {
            selectedTile = tile
            onTileTap?.invoke(tile, cell)
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

    /**
     * Fill [tileRect] with stacked horizontal segments, one per
     * [NetworkType] present in [cellStats], with heights proportional to
     * each network's reading count. This lets the user see every network
     * that contributes to the tile at a glance, not just the dominant one.
     *
     * Networks are sorted by reading count (most first) so the strongest
     * network claims the largest visual area at the top of the tile.
     * Networks filtered out via [allowed] are skipped.
     *
     * Allocation-free: reuses [fillPaint] and [segmentIdxBuf] for the actual
     * drawing and scratch sorting; performs no heap allocations per tile.
     */
    private fun drawSegmentedFill(
        canvas: Canvas,
        tileRect: Rect,
        cellStats: CellStats,
    ) {
        // Build a list of NetworkType indices that are present and allowed.
        // Max 8 entries (enum size), so a pre-allocated IntArray is fine.
        val entries = NetworkType.entries
        var count = 0
        var total = 0
        for (i in entries.indices) {
            val net = entries[i]
            if (net !in allowed) continue
            val agg = cellStats.perNetwork[net] ?: continue
            segmentIdxBuf[count] = i
            total += agg.count
            count++
        }
        if (count == 0) return

        // Selection-sort indices by reading count descending.
        // N ≤ 8, so insertion/selection sort is cheaper than any allocation.
        for (i in 0 until count - 1) {
            var best = i
            for (j in i + 1 until count) {
                val jCount = cellStats.perNetwork[entries[segmentIdxBuf[j]]]!!.count
                val bestSoFar = cellStats.perNetwork[entries[segmentIdxBuf[best]]]!!.count
                if (jCount > bestSoFar) best = j
            }
            if (best != i) {
                val tmp = segmentIdxBuf[i]
                segmentIdxBuf[i] = segmentIdxBuf[best]
                segmentIdxBuf[best] = tmp
            }
        }

        val totalF = total.toFloat()
        val left = tileRect.left.toFloat()
        val right = tileRect.right.toFloat()
        val tileHeight = tileRect.height().toFloat()
        var currentTop = tileRect.top.toFloat()

        for (i in 0 until count) {
            val net = entries[segmentIdxBuf[i]]
            val agg = cellStats.perNetwork[net]!!
            val segmentHeight = tileHeight * (agg.count / totalF)
            val bucket = bucketFor(agg.meanDbm)
            fillPaint.color = colorFor(net, bucket, palette).toArgb()
            canvas.drawRect(left, currentTop, right, currentTop + segmentHeight, fillPaint)
            currentTop += segmentHeight
        }
    }

    /**
     * Print [meanDbm] (the dominant network's mean signal) in the top-left
     * corner of `tileRect` - a white number over a dark halo. Skipped when
     * the cell has no usable signal value ([Int.MIN_VALUE]) or the tile is
     * too small to fit the label cleanly.
     */
    private fun drawMeanLabel(
        canvas: Canvas,
        density: Float,
        tileRect: Rect,
        meanDbm: Int,
    ) {
        if (meanDbm == Int.MIN_VALUE) return
        val tileMinDp = minOf(tileRect.width(), tileRect.height()).toFloat() / density
        if (tileMinDp < MIN_TILE_DP_FOR_LABEL) return

        val count = formatInt(meanDbm)
        val marginPx = LABEL_MARGIN_DP * density
        val x = tileRect.left + marginPx
        // Baseline sits one text-size below the top edge (+ margin), so the
        // glyph tops clear the tile's top stroke.
        val y = tileRect.top + marginPx + labelPaint.textSize
        canvas.drawText(numBuf, 0, count, x, y, labelHaloPaint)
        canvas.drawText(numBuf, 0, count, x, y, labelPaint)
    }

    /**
     * Render [value] into [numBuf] as decimal digits (with a leading '-' for
     * negatives) and return the character count. Allocation-free so it is
     * safe to call for every tile inside `draw()`; dBm values comfortably fit
     * the 8-char buffer.
     */
    private fun formatInt(value: Int): Int {
        if (value == 0) {
            numBuf[0] = '0'
            return 1
        }
        val neg = value < 0
        var n = if (neg) -value else value
        var digits = 0
        var tmp = n
        while (tmp > 0) {
            digits++
            tmp /= 10
        }
        val count = digits + if (neg) 1 else 0
        var idx = count - 1
        while (n > 0) {
            numBuf[idx--] = ('0' + (n % 10))
            n /= 10
        }
        if (neg) numBuf[0] = '-'
        return count
    }

    companion object {
        /** Fixed storage zoom - the single source of truth for coverage
         *  cell size. Z=20 in Web-Mercator gives ~38 m tiles at the equator
         *  and ~27 m east-west at 45° latitude. The user-facing label is
         *  \"50 m cells\" - a rounded mid-latitude figure that matches what
         *  users actually see in the field. **Exact** 50 m would require
         *  a fractional-zoom refactor (the `aggregate()` function and the
         *  `TileId` data class in `com.sigverage.app.coverage.CoverageModel`
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

        private const val MIN_TILE_DP_FOR_GRID = 28f

        private const val SLOT_COLS = 4
        private const val SLOT_ROWS = 2
        /** Distance from one slot centre to the next. */
        private const val SLOT_PITCH_DP = 9.0f
        /** Implicit gap = pitch - 2 * radius. */
        private const val SLOT_RADIUS_DP = 3.5f
        private const val SLOT_GAP_DP = 1.5f
        /** Distance from the tile edge to the closest slot centre. */
        private const val SLOT_MARGIN_DP = 2.5f
        private const val STROKE_WIDTH_DP = 0.75f
        /** Stroke width of the white selection outline (halo adds ~2dp more). */
        private const val SELECT_STROKE_WIDTH_DP = 2f

        // ---- Mean-dBm corner label geometry ----

        /** Skip the label below this tile size - it can't fit "-140" cleanly. */
        private const val MIN_TILE_DP_FOR_LABEL = 36f
        private const val LABEL_TEXT_SIZE_DP = 12f
        /** Distance from the tile's top-left corner to the label. */
        private const val LABEL_MARGIN_DP = 3f
        /** Dark halo stroke width under the white glyphs. */
        private const val LABEL_HALO_WIDTH_DP = 2f

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
