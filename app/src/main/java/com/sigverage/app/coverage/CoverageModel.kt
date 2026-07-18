package com.sigverage.app.coverage

import androidx.compose.ui.graphics.Color
import com.sigverage.app.model.NetworkType
import com.sigverage.app.model.SignalReading
import com.sigverage.app.ui.theme.NetworkColors
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Slippy-map tile coordinates at a particular zoom level.
 *
 * We use the standard Web-Mercator conventions used by the OSM tile server,
 * so a tile X/Y/Z maps back to the exact same lat/lng bounds for everyone.
 */
data class TileId(val x: Int, val y: Int, val zoom: Int)

/**
 * Per-network statistics accumulated inside one tile. [meanDbm] is `Int.MIN_VALUE`
 * when no reading in this cell reported a usable signal value.
 */
data class NetworkAggregate(
    val count: Int,
    val sumDbm: Int,
    val countDbm: Int,
    val lastTimestamp: Long,
) {
    val meanDbm: Int
        get() = if (countDbm == 0) Int.MIN_VALUE else sumDbm / countDbm
}

/** Aggregation for one tile, subdivided by network. */
data class CellStats(
    val perNetwork: Map<NetworkType, NetworkAggregate>,
    /** All operators observed in this tile. Used for operator filtering. */
    val operators: Set<String> = emptySet(),
) {
    companion object {
        val EMPTY = CellStats(emptyMap())
    }
}

/** North-west and south-east corners of a tile in WGS84 degrees. */
data class TileBounds(
    val northLat: Double,
    val westLng: Double,
    val southLat: Double,
    val eastLng: Double,
)

/**
 * Strength bucket used to kick alpha on the box. Alphas are kept low so the
 * coverage squares stay clearly translucent and the underlying map (streets,
 * labels, landmarks) remains readable through them.
 */
enum class SignalBucket(val alpha: Float) {
    Strong(0.60f),
    Ok(0.40f),
    Weak(0.22f),
    Unknown(0.35f),
}

/** Mercator slippy-tile coordinate of a lat/lng at zoom [zoom]. */
fun latLngToTile(lat: Double, lng: Double, zoom: Int): TileId {
    val n = 1 shl zoom
    val xNorm = ((lng + 180.0) / 360.0).coerceIn(0.0, 1.0)
    val x = (xNorm * n).toInt().coerceIn(0, n - 1)
    // Mercator clamps at ~±85.05°; tan blows up past that.
    val latSafe = lat.coerceIn(-85.05112878, 85.05112878)
    val latRad = Math.toRadians(latSafe)
    val yNorm = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0
    val y = (yNorm * n).toInt().coerceIn(0, n - 1)
    return TileId(x, y, zoom)
}

/** Geographic bounds of [tile]. Pure. */
fun tileBounds(tile: TileId): TileBounds {
    val n = 1 shl tile.zoom
    val west = tile.x.toDouble() / n * 360.0 - 180.0
    val east = (tile.x + 1).toDouble() / n * 360.0 - 180.0
    val n1 = Math.PI - 2.0 * Math.PI * tile.y / n
    val n2 = Math.PI - 2.0 * Math.PI * (tile.y + 1) / n
    val north = Math.toDegrees(atan(sinh(n1)))
    val south = Math.toDegrees(atan(sinh(n2)))
    return TileBounds(north, west, south, east)
}

/**
 * Aggregate a list of readings into a per-tile, per-network statistics map
 * using Web-Mercator slippy-tile indexing at the given storage zoom level.
 *
 * Pure function. O(N) over readings; safe to recompute every frame in the
 * worst case (a thousand readings bins in well under a millisecond on
 * modern hardware).
 */
fun aggregate(readings: List<SignalReading>, storageZoom: Int): Map<TileId, CellStats> {
    val tmp = HashMap<Long, TileAccumulator>()
    for (r in readings) {
        val tile = latLngToTile(r.latitude, r.longitude, storageZoom)
        val key = packKey(tile.x, tile.y)
        val acc = tmp.getOrPut(key) { TileAccumulator() }
        val agg = acc.perNet.getOrPut(r.networkType) { MutableAgg() }
        agg.add(r)
        r.operatorName?.let { acc.operators.add(it) }
    }
    val out = HashMap<TileId, CellStats>(tmp.size)
    for ((key, acc) in tmp) {
        val (x, y) = unpackKey(key)
        val stats = HashMap<NetworkType, NetworkAggregate>(acc.perNet.size)
        for ((net, agg) in acc.perNet) stats[net] = agg.build()
        out[TileId(x, y, storageZoom)] = CellStats(stats, acc.operators.toSet())
    }
    return out
}

/** Pack `(x, y)` into one Long for cheap HashMap keys. */
private fun packKey(x: Int, y: Int): Long =
    (x.toLong() and 0xFFFFFFFFL) or (y.toLong() shl 32)

private fun unpackKey(key: Long): Pair<Int, Int> {
    val x = (key and 0xFFFFFFFFL).toInt()
    val y = (key ushr 32).toInt()
    return x to y
}

/**
 * Pick the network to display for a cell, given the user's filter and
 * tiebreakers:
 *   1. Highest `count` wins.
 *   2. Most recent `lastTimestamp` wins on ties.
 *   3. Stronger mean dBm (less negative) wins on further ties.
 *
 * Returns null if every observed network is currently filtered out, or if
 * the cell has no readings at all.
 */
fun pickDominant(
    stats: CellStats,
    allowed: Set<NetworkType>,
): Pair<NetworkType, NetworkAggregate>? {
    var best: Pair<NetworkType, NetworkAggregate>? = null
    for ((net, agg) in stats.perNetwork) {
        if (net !in allowed) continue
        val current = best ?: run {
            best = net to agg
            continue
        }
        val other = current.second
        val better: Boolean = when {
            agg.count != other.count -> agg.count > other.count
            agg.lastTimestamp != other.lastTimestamp -> agg.lastTimestamp > other.lastTimestamp
            else -> agg.meanDbm > other.meanDbm
        }
        if (better) best = net to agg
    }
    return best
}

/** Map a mean dBm strength to the four buckets our visual encoding uses. */
fun bucketFor(meanDbm: Int): SignalBucket = when {
    meanDbm == Int.MIN_VALUE -> SignalBucket.Unknown
    meanDbm >= -90 -> SignalBucket.Strong
    meanDbm >= -105 -> SignalBucket.Ok
    else -> SignalBucket.Weak
}

/**
 * Final colour for a tile: network hue from [palette], alpha from the
 * signal strength bucket. This is the **HSL hybrid** encoding - single
 * visual channel, two dimensions.
 *
 * [palette] defaults to the static [NetworkColors] fallback so non-Compose
 * callers (DAOs, ViewModels, tests) keep working unchanged. Compose
 * callers should pass the result of `rememberNetworkColors()` so the
 * network hues track the live `ColorScheme` and the legend stays stable
 * across light/dark/dynamic-colour changes.
 */
fun colorFor(
    network: NetworkType,
    bucket: SignalBucket,
    palette: Map<NetworkType, Color> = NetworkColors,
): Color {
    val base = palette[network] ?: Color.Gray
    return base.copy(alpha = bucket.alpha)
}

/** Per-tile accumulator used by [aggregate]. */
private class TileAccumulator {
    val perNet = HashMap<NetworkType, MutableAgg>()
    val operators = HashSet<String>()
}

/** Internal accumulator used by `aggregate` while binning readings. */
private class MutableAgg {
    var count = 0
    var sumDbm = 0
    var countDbm = 0
    var lastTimestamp = 0L

    fun add(r: SignalReading) {
        count++
        val d = r.signalDbm
        if (d != null) {
            sumDbm += d
            countDbm++
        }
        if (r.timestamp > lastTimestamp) lastTimestamp = r.timestamp
    }

    fun build() = NetworkAggregate(count, sumDbm, countDbm, lastTimestamp)
}
