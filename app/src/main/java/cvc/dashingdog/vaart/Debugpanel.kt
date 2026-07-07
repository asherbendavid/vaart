package cvc.dashingdog.vaart

/**
 * Raw debug values that may be of interest while testing Phase 6 (speed-limit matching).
 * LocationService always populates every field it is able to compute — whether a given
 * field actually reaches the on-screen debug panel is controlled separately, below, by
 * ACTIVE_DEBUG_FIELDS. This keeps "what we measure" and "what we currently look at" independent.
 */
data class DebugInfo(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val wayName: String? = null,
    val wayId: Long? = null,
    val matchDistanceM: Double? = null,
    val candidateCount: Int? = null,
    val bearingRawDeg: Float? = null,
    val bearingSmoothedDeg: Float? = null,
    val hysteresisState: String? = null,
    val roadClassification: String? = null,
    val maxSpeedLimitKmh: Int? = null,
    val minSpeedLimitKmh: Int? = null,
    val detectedCountry: String? = null,
    val overpassStats: String? = null,
)

/**
 * Every value DebugInfo is capable of carrying. The panel always displays active fields
 * in this declared order, regardless of the order they appear in ACTIVE_DEBUG_FIELDS.
 */
enum class DebugField {
    CURRENT_LOCATION,
    WAY_NAME,
    WAY_ID,
    MATCH_DISTANCE,
    CANDIDATE_COUNT,
    BEARING_RAW,
    BEARING_SMOOTHED,
    HYSTERESIS_STATE,
    ROAD_CLASSIFICATION,
    SPEED_LIMITS,
    DETECTED_COUNTRY,
    OVERPASS_STATS,
}

/**
 * Edit this set before each test build/deploy to choose what the on-screen debug panel
 * shows. Fields not yet computed by LocationService (i.e. still null in DebugInfo) are
 * silently skipped even if listed here, so it's safe to leave forward-looking fields in
 * this set ahead of the logic that will eventually populate them.
 */
val ACTIVE_DEBUG_FIELDS: Set<DebugField> = setOf(
    DebugField.WAY_NAME,
    DebugField.DETECTED_COUNTRY,
    DebugField.ROAD_CLASSIFICATION,
    DebugField.OVERPASS_STATS,
)

/** Renders only the active, currently-available fields as one "Label: value" line each. */
object DebugPanelRenderer {

    fun render(
        info: DebugInfo,
        activeFields: Set<DebugField> = ACTIVE_DEBUG_FIELDS
    ): String {
        val lines = mutableListOf<String>()

        for (field in DebugField.entries) {
            if (field !in activeFields) continue

            val line: String? = when (field) {
                DebugField.CURRENT_LOCATION -> {
                    val lat = info.latitude
                    val lon = info.longitude
                    if (lat != null && lon != null) "Location: %.5f, %.5f".format(lat, lon) else "Location: No data"
                }
                DebugField.WAY_NAME -> info.wayName?.let { "Way name: $it" }
                DebugField.WAY_ID -> info.wayId?.let { "Way ID: $it" }
                DebugField.MATCH_DISTANCE -> info.matchDistanceM?.let { "Match dist: %.1f m".format(it) }
                DebugField.CANDIDATE_COUNT -> info.candidateCount?.let { "Candidates: $it" }
                DebugField.BEARING_RAW -> info.bearingRawDeg?.let { "Bearing (raw): %.0f\u00B0".format(it) }
                DebugField.BEARING_SMOOTHED -> info.bearingSmoothedDeg?.let { "Bearing (smooth): %.0f\u00B0".format(it) }
                DebugField.HYSTERESIS_STATE -> info.hysteresisState?.let { "Hysteresis: $it" }
                DebugField.ROAD_CLASSIFICATION -> info.roadClassification?.let { "Class: $it" }
                DebugField.SPEED_LIMITS -> {
                    if (info.maxSpeedLimitKmh != null || info.minSpeedLimitKmh != null) {
                        val min = info.minSpeedLimitKmh?.toString() ?: "--"
                        val max = info.maxSpeedLimitKmh?.toString() ?: "--"
                        "Limits: $min / $max km/h"
                    } else null
                }
                DebugField.DETECTED_COUNTRY -> {
                    val country = info.detectedCountry
                    val mph = LocationService.detectedUseMph
                    if (country != null) "Country: $country (${if (mph == true) "mph" else "km/h"})"
                    else "Country: not yet detected"
                }
                DebugField.OVERPASS_STATS -> info.overpassStats?.let { "Overpass: $it" }
            }

            if (line != null) lines.add(line)
        }

        return lines.joinToString("\n")
    }
}