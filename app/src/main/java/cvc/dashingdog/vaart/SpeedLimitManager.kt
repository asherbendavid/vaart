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
     * v1 simplification: distance to the NEAREST POINT in the way's geometry,
     * not true nearest-segment distance. OSM ways are usually densely
     * sampled enough that this is a close approximation, and it's far
     * simpler/safer code than full point-to-segment math. Worth revisiting
     * if real-world testing shows mismatches on long straight stretches.
     */
    private fun distanceToWay(lat: Double, lon: Double, pointsEncoded: String): Double {
        val lonScale = cos(Math.toRadians(lat)) // compress longitude towards real-world distance
        var minDist = Double.MAX_VALUE
        for (pair in pointsEncoded.split(";")) {
            val parts = pair.split(",")
            if (parts.size != 2) continue
            val wLat = parts[0].toDoubleOrNull() ?: continue
            val wLon = parts[1].toDoubleOrNull() ?: continue
            val dLat = lat - wLat
            val dLon = (lon - wLon) * lonScale
            val dist = sqrt(dLat * dLat + dLon * dLon)
            if (dist < minDist) minDist = dist
        }
        return minDist
    }
}