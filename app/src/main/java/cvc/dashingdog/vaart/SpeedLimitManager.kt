package cvc.dashingdog.vaart

import android.content.Context
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sqrt

class SpeedLimitManager(context: Context) {

    private val repository = SpeedLimitRepository(context)

    companion object {
        private const val TILE_SIZE_DEG = 0.02       // ~2km at Western Cape latitude
        private const val TILE_EXPIRY_MS = 30L * 24 * 60 * 60 * 1000
        private const val EDGE_THRESHOLD_DEG = 0.0045 // ~500m
        private const val MATCH_RADIUS_DEG = 0.0003   // ~30m
    }

    private fun tileKey(value: Double): Int = floor(value / TILE_SIZE_DEG).toInt()

    /** Ensures the current tile (and a neighbour, if close to an edge) is cached. */
    suspend fun ensureTileCached(lat: Double, lon: Double) {
        val latKey = tileKey(lat)
        val lonKey = tileKey(lon)
        fetchTileIfNeeded(latKey, lonKey)

        val latInTile = lat - latKey * TILE_SIZE_DEG
        val lonInTile = lon - lonKey * TILE_SIZE_DEG

        if (latInTile < EDGE_THRESHOLD_DEG) fetchTileIfNeeded(latKey - 1, lonKey)
        if (TILE_SIZE_DEG - latInTile < EDGE_THRESHOLD_DEG) fetchTileIfNeeded(latKey + 1, lonKey)
        if (lonInTile < EDGE_THRESHOLD_DEG) fetchTileIfNeeded(latKey, lonKey - 1)
        if (TILE_SIZE_DEG - lonInTile < EDGE_THRESHOLD_DEG) fetchTileIfNeeded(latKey, lonKey + 1)
    }

    private suspend fun fetchTileIfNeeded(latKey: Int, lonKey: Int) {
        val existing = repository.getTile(latKey, lonKey)
        if (existing != null && System.currentTimeMillis() - existing.fetchedAt < TILE_EXPIRY_MS) return

        val south = latKey * TILE_SIZE_DEG
        val north = south + TILE_SIZE_DEG
        val west = lonKey * TILE_SIZE_DEG
        val east = west + TILE_SIZE_DEG

        val ways = OverpassClient.fetchSpeedLimitWays(south, west, north, east)
        if (ways != null) {
            if (ways.isNotEmpty()) repository.insertWays(ways)
            repository.markTileQueried(latKey, lonKey) // only mark on real success
        }
    }

    data class SpeedLimitMatch(
        val maxSpeedKmh: Int?,
        val minSpeedKmh: Int?,
        val wayName: String?,
        val osmWayId: Long?
    )

    /** Returns (maxSpeedKmh, minSpeedKmh) for the nearest cached road, or nulls if nothing nearby. */
    suspend fun lookupSpeedLimits(lat: Double, lon: Double): SpeedLimitMatch {
        val candidates = repository.getCandidateWays(
            south = lat - MATCH_RADIUS_DEG, north = lat + MATCH_RADIUS_DEG,
            west = lon - MATCH_RADIUS_DEG, east = lon + MATCH_RADIUS_DEG
        )
        if (candidates.isEmpty()) return SpeedLimitMatch(null, null, null, null)

        var nearest: SpeedLimitWay? = null
        var nearestDist = Double.MAX_VALUE
        for (way in candidates) {
            val dist = distanceToWay(lat, lon, way.pointsEncoded)
            if (dist < nearestDist) { nearestDist = dist; nearest = way }
        }
        return SpeedLimitMatch(nearest?.maxSpeedKmh, nearest?.minSpeedKmh, nearest?.name, nearest?.osmWayId)
    }

    /**
     * True nearest-segment distance: projects the current position onto each
     * consecutive segment [A→B] in the way's geometry, clamps to the segment
     * endpoints, and returns the minimum perpendicular distance across all segments.
     */
    private fun distanceToWay(lat: Double, lon: Double, pointsEncoded: String): Double {
        val lonScale = cos(Math.toRadians(lat))
        val points = pointsEncoded.split(";").mapNotNull { pair ->
            val parts = pair.split(",")
            if (parts.size != 2) null
            else parts[0].toDoubleOrNull()?.let { wLat ->
                parts[1].toDoubleOrNull()?.let { wLon -> wLat to wLon }
            }
        }
        if (points.isEmpty()) return Double.MAX_VALUE

        var minDist = Double.MAX_VALUE

        for (i in 0 until points.size - 1) {
            val (aLat, aLon) = points[i]
            val (bLat, bLon) = points[i + 1]

            // Work in a flat (scaled) coordinate space so lat/lon distances are comparable
            val pX = (lon - aLon) * lonScale;  val pY = lat - aLat
            val dX = (bLon - aLon) * lonScale; val dY = bLat - aLat
            val segLenSq = dX * dX + dY * dY

            // t is how far along the segment [A→B] the closest point to P falls (0=A, 1=B)
            val t = if (segLenSq == 0.0) 0.0 else ((pX * dX + pY * dY) / segLenSq).coerceIn(0.0, 1.0)

            val nearX = pX - t * dX
            val nearY = pY - t * dY
            val dist = sqrt(nearX * nearX + nearY * nearY)
            if (dist < minDist) minDist = dist
        }
        return minDist
    }
}