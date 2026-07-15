package com.signalspotter.app.coverage

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Point
import androidx.compose.ui.graphics.toArgb
import com.signalspotter.app.model.NetworkType
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * osmdroid `Overlay` that paints the coverage grid.
 *
 * Reads from [stats] (a `Map<TileId, CellStats>` recomputed externally when the
 * underlying readings change) and [allowed] (the user's network filter). Both
 * are updated via setter methods; the host must call `mapView.invalidate()`
 * after each change.
 *
 * Performance contract:
 *   - **Zero allocations inside `draw()`.** A single `Paint` per role, a
 *     single `Rect`, two `GeoPoint`s and two `Point`s are reused every frame.
 *   - **Viewport culling**: tiles outside `projection.boundingBox` are
 *     rejected before any pixel work happens.
 *   - **Pixel-rect culling**: after projection, tiles whose screen rectangle
 *     is entirely off-screen are rejected.
 *   - O(#visible tiles) per frame; for hundreds of tiles this hits the
 *     hardware-accelerated `Canvas.drawRect` path comfortably.
 */
class CoverageGridOverlay(
    initialStorageZoom: Int = DEFAULT_STORAGE_ZOOM,
) : Overlay() {

    /** Updates when the host zooms or when storage zoom is explicitly changed. */
    var storageZoom: Int = initialStorageZoom
        set(value) {
            field = value.coerceIn(MIN_STORAGE_ZOOM, MAX_STORAGE_ZOOM)
        }

    private var stats: Map<TileId, CellStats> = emptyMap()
    private var allowed: Set<NetworkType> = NetworkType.values().toSet()

    // Pre-allocated scratch. Never reassigned in draw().
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(56, 0, 0, 0)
    }
    private val tlGeo = GeoPoint(0.0, 0.0)
    private val brGeo = GeoPoint(0.0, 0.0)
    private val tlPt = Point()
    private val brPt = Point()
    private val rect = Rect()

    fun setStats(newStats: Map<TileId, CellStats>) {
        stats = newStats
    }

    fun setAllowed(newAllowed: Set<NetworkType>) {
        allowed = newAllowed
    }

    fun update(newStats: Map<TileId, CellStats>, newAllowed: Set<NetworkType>) {
        stats = newStats
        allowed = newAllowed
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || stats.isEmpty()) return
        val projection = mapView.projection
        val bb = projection.boundingBox
        val mapW = mapView.width
        val mapH = mapView.height
        if (mapW <= 0 || mapH <= 0) return

        // Stroke width scales with display density so it stays ~0.75 dp.
        strokePaint.strokeWidth =
            STROKE_WIDTH_DP * mapView.context.resources.displayMetrics.density

        for ((tile, cellStats) in stats) {
            if (tile.zoom != storageZoom) continue

            val pick = pickDominant(cellStats, allowed) ?: continue
            val bucket = bucketFor(pick.second.meanDbm)
            fillPaint.color = colorFor(pick.first, bucket).toArgb()

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

            rect.set(left, top, right, bottom)
            canvas.drawRect(rect, fillPaint)
            canvas.drawRect(rect, strokePaint)
        }
    }

    companion object {
        /** Default storage zoom gives ~600 m tiles at the equator — a good
         *  balance between detail and screen real-estate. */
        const val DEFAULT_STORAGE_ZOOM = 16

        /** Floor for auto-clamping storageZoom. Below this, the overlay is
         *  generally too coarse to be useful. */
        const val MIN_STORAGE_ZOOM = 12

        /** Ceiling for auto-clamping. Above this, individual readings tell
         *  the story better than aggregated tiles. */
        const val MAX_STORAGE_ZOOM = 19

        private const val STROKE_WIDTH_DP = 0.75f
    }
}
