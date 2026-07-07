package cvc.dashingdog.vaart

import android.content.Context
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.math.atan2
import kotlin.math.abs

class SpeedLimitManager(context: Context) {

    private val repository = SpeedLimitRepository(context)
    private var lockedWay: SpeedLimitWay? = null
    private var failedAttempts = mutableMapOf<Pair<Int, Int>, Long>()
    private var lastCallTime = 0L
    private var challengerWay: SpeedLimitWay? = null
    private var challengerCount: Int = 0

    companion object {
        private const val TILE_SIZE_DEG = 0.02       // ~2km at Western Cape latitude
        private const val TILE_EXPIRY_MS = 30L * 24 * 60 * 60 * 1000
        private const val EDGE_THRESHOLD_DEG = 0.0045 // ~500m
        private const val MATCH_RADIUS_DEG = 0.0003   // ~30m — used for scoring/distance precision
        private const val QUERY_RADIUS_DEG = 0.0015   // ~150m — used for candidate lookup window
        private const val MIN_BEARING_SPEED_KMH = 20.0 // below this, bearing is too noisy to use
        private const val MAX_BEARING_SPEED_KMH = 60.0 // above this, full bearing weight applied
        private const val HYSTERESIS_THRESHOLD = 2 // updates challenger must win before switching
        private val HIGHWAY_RANK = mapOf(
            "motorway" to 8,
            "trunk" to 7,
            "primary" to 6,
            "secondary" to 5,
            "tertiary" to 4,
            "unclassified" to 3,
            "residential" to 2,
            "motorway_link" to 4,
            "trunk_link" to 3,
            "primary_link" to 3,
            "secondary_link" to 2,
            "service" to 1
        )
        private const val CLASSIFICATION_WEIGHT = 0.3  // tunable: fraction of MATCH_RADIUS_DEG per rank step
        private const val FAILURE_COOLDOWN_MS = 60_000L // don't retry a failed tile for 1 minute
        private const val MIN_CALL_INTERVAL_MS = 31_000L // global spacing between any two Overpass calls
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

        val tileId = latKey to lonKey
        val lastFailure = failedAttempts[tileId]
        if (lastFailure != null && System.currentTimeMillis() - lastFailure < FAILURE_COOLDOWN_MS) return

        val now = System.currentTimeMillis()
        if (now - lastCallTime < MIN_CALL_INTERVAL_MS) return
        lastCallTime = now

        val south = latKey * TILE_SIZE_DEG
        val north = south + TILE_SIZE_DEG
        val west = lonKey * TILE_SIZE_DEG
        val east = west + TILE_SIZE_DEG

        val ways = OverpassClient.fetchSpeedLimitWays(south, west, north, east)
        if (ways != null) {
            if (ways.isNotEmpty()) repository.insertWays(ways)
            repository.markTileQueried(latKey, lonKey)
            failedAttempts.remove(tileId)
        } else {
            failedAttempts[tileId] = System.currentTimeMillis()
        }
    }

    data class SpeedLimitMatch(
        val maxSpeedKmh: Int?,
        val minSpeedKmh: Int?,
        val wayName: String?,
        val osmWayId: Long?,
        val matchDistanceM: Double? = null,
        val candidateCount: Int = 0,
        val hysteresisState: String? = null,
        val roadClassification: String? = null,
    )

    private data class SegmentResult(val distance: Double, val bearingDeg: Double)

    /** Returns (maxSpeedKmh, minSpeedKmh) for the nearest cached road, or nulls if nothing nearby. */
    suspend fun lookupSpeedLimits(lat: Double, lon: Double, bearingDeg: Float?, speedKmh: Int, countryCode: String?): SpeedLimitMatch {
        val candidates = repository.getCandidateWays(
            south = lat - QUERY_RADIUS_DEG, north = lat + QUERY_RADIUS_DEG,
            west = lon - QUERY_RADIUS_DEG, east = lon + QUERY_RADIUS_DEG
        )

        // Speed-scaled bearing weight: 0 below MIN speed, ramps to 1.0 at MAX speed
        val bearingWeight = if (bearingDeg != null) {
            ((speedKmh - MIN_BEARING_SPEED_KMH) / (MAX_BEARING_SPEED_KMH - MIN_BEARING_SPEED_KMH))
                .coerceIn(0.0, 1.0)
        } else 0.0

        var bestWay: SpeedLimitWay? = null
        var bestScore = Double.MAX_VALUE
        var bestDistanceDeg = Double.MAX_VALUE

        for (way in candidates) {
            val result = nearestSegment(lat, lon, way.pointsEncoded)

            // Bearing penalty: angular difference [0°–90°] scaled to same units as distance.
            // A perpendicular road at full speed adds one full MATCH_RADIUS_DEG to the score,
            // enough to flip between two close candidates without overwhelming distance.
            val bearingPenalty = if (bearingWeight > 0.0) {
                val diff = bearingDifference(bearingDeg!!.toDouble(), result.bearingDeg)
                bearingWeight * (diff / 90.0) * MATCH_RADIUS_DEG
            } else 0.0

            val rank = HIGHWAY_RANK[way.roadClassification] ?: 0
            val classificationBonus = (8 - rank) * CLASSIFICATION_WEIGHT * MATCH_RADIUS_DEG / 8.0
            val score = result.distance + bearingPenalty + classificationBonus
            if (score < bestScore) {
                bestScore = score
                bestWay = way
                bestDistanceDeg = result.distance
            }
        }

        // Convert degrees distance to approximate metres (1° ≈ 111320m at this latitude)
        val distanceM = bestDistanceDeg * 111320.0

// --- Hysteresis ---
        when {
            candidates.isEmpty() -> {
                // No candidates at all — count against the lock, clear if sustained
                if (lockedWay == null) {
                    // nothing locked, nothing to do
                } else if (bestWay == null || bestWay.osmWayId != lockedWay!!.osmWayId) {
                    challengerWay = bestWay  // null challenger = "clear" challenger
                    challengerCount++
                    if (challengerCount >= HYSTERESIS_THRESHOLD) {
                        lockedWay = null
                        challengerWay = null
                        challengerCount = 0
                    }
                }
            }
            lockedWay == null -> {
                // Nothing locked yet — commit immediately on first match
                lockedWay = bestWay
                challengerWay = null
                challengerCount = 0
            }
            bestWay?.osmWayId == lockedWay!!.osmWayId -> {
                // Winner matches lock — reset any challenger
                challengerWay = null
                challengerCount = 0
            }
            else -> {
                // Different way is winning — start or advance challenger count
                if (bestWay?.osmWayId == challengerWay?.osmWayId) {
                    challengerCount++
                    if (challengerCount >= HYSTERESIS_THRESHOLD) {
                        lockedWay = bestWay
                        challengerWay = null
                        challengerCount = 0
                    }
                } else {
                    // New challenger — reset count
                    challengerWay = bestWay
                    challengerCount = 1
                }
            }
        }

        val activeWay = lockedWay
        //val distanceM = bestDistanceDeg * 111320.0

        val hysteresisState = when {
            activeWay == null -> "no lock"
            challengerWay != null -> {
                val challengerName = challengerWay?.name ?: challengerWay?.osmWayId?.toString() ?: "unknown"
                "locked: ${activeWay.name ?: activeWay.osmWayId} | challenger: $challengerName ($challengerCount/$HYSTERESIS_THRESHOLD)"
            }
            else -> "locked: ${activeWay.name ?: activeWay.osmWayId}"
        }

        val resolvedMaxSpeed = activeWay?.maxSpeedKmh
            ?: DefaultSpeedLimits.getDefaultSpeed(countryCode, activeWay?.roadClassification)

        return SpeedLimitMatch(
            maxSpeedKmh = resolvedMaxSpeed,
            minSpeedKmh = activeWay?.minSpeedKmh,
            wayName = activeWay?.name,
            osmWayId = activeWay?.osmWayId,
            matchDistanceM = distanceM,
            candidateCount = candidates.size,
            hysteresisState = hysteresisState,
            roadClassification = activeWay?.roadClassification,
        )
    }

    /**
     * True nearest-segment distance: projects the current position onto each
     * consecutive segment [A→B] in the way's geometry, clamps to the segment
     * endpoints, and returns the minimum perpendicular distance across all segments.
     */
    private fun nearestSegment(lat: Double, lon: Double, pointsEncoded: String): SegmentResult {
        val lonScale = cos(Math.toRadians(lat))
        val points = pointsEncoded.split(";").mapNotNull { pair ->
            val parts = pair.split(",")
            if (parts.size != 2) null
            else parts[0].toDoubleOrNull()?.let { wLat ->
                parts[1].toDoubleOrNull()?.let { wLon -> wLat to wLon }
            }
        }
        if (points.isEmpty()) return SegmentResult(Double.MAX_VALUE, 0.0)

        var minDist = Double.MAX_VALUE
        var bestBearing = 0.0

        for (i in 0 until points.size - 1) {
            val (aLat, aLon) = points[i]
            val (bLat, bLon) = points[i + 1]

            val pX = (lon - aLon) * lonScale; val pY = lat - aLat
            val dX = (bLon - aLon) * lonScale; val dY = bLat - aLat
            val segLenSq = dX * dX + dY * dY

            val t = if (segLenSq == 0.0) 0.0
            else ((pX * dX + pY * dY) / segLenSq).coerceIn(0.0, 1.0)

            val nearX = pX - t * dX
            val nearY = pY - t * dY
            val dist = sqrt(nearX * nearX + nearY * nearY)

            if (dist < minDist) {
                minDist = dist
                // Segment bearing in degrees, 0=North, clockwise (matches GPS bearing convention)
                bestBearing = (Math.toDegrees(atan2(dX, dY)) + 360.0) % 360.0
            }
        }
        return SegmentResult(minDist, bestBearing)
    }

    /**
     * Angular difference between two bearings, folded to [0°, 90°].
     * Folding to 90° treats the way as bidirectional — travelling either
     * direction on a two-way road is an equally good match.
     */
    private fun bearingDifference(a: Double, b: Double): Double {
        val diff = abs(a - b) % 360.0
        val shortest = if (diff > 180.0) 360.0 - diff else diff   // [0, 180]
        return if (shortest > 90.0) 180.0 - shortest else shortest  // [0, 90]
    }
}