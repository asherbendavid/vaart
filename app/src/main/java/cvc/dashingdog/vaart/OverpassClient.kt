package cvc.dashingdog.vaart

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object OverpassClient {

    // debug counters
    var callCount = 0
        private set
    var successCount = 0
        private set
    var failureCount = 0
        private set

    private const val ENDPOINT = "https://overpass-api.de/api/interpreter"

    /**
     * Fetches ways tagged with maxspeed/minspeed inside the bounding box.
     * Returns null on network failure (caller should NOT mark the tile as
     * queried in that case, so it retries later). Returns an empty list if
     * the fetch succeeded but genuinely found nothing — that tile is done.
     * Blocking call — must run on a background coroutine.
     */
    fun fetchSpeedLimitWays(south: Double, west: Double, north: Double, east: Double): List<SpeedLimitWay>? {
        callCount++
        val query = """
        [out:json][timeout:25];
        (
          way[maxspeed]($south,$west,$north,$east);
          way[minspeed]($south,$west,$north,$east);
          way[highway~"^(motorway|trunk|primary|secondary|tertiary|unclassified|residential|motorway_link|trunk_link|primary_link|secondary_link)$"]($south,$west,$north,$east);
        );
        out geom;
        """.trimIndent()

        return try {
            val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 25_000
            }
            val body = "data=" + URLEncoder.encode(query, "UTF-8")
            connection.outputStream.use { it.write(body.toByteArray()) }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                failureCount++
                return null
            }
            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            successCount++
            parseResponse(responseText)
        } catch (e: Exception) {
            failureCount++
            null
        }
    }

    private fun parseResponse(json: String): List<SpeedLimitWay> {
        val results = mutableListOf<SpeedLimitWay>()
        val elements = JSONObject(json).optJSONArray("elements") ?: return results

        for (i in 0 until elements.length()) {
            val element = elements.getJSONObject(i)
            if (element.optString("type") != "way") continue

            val tags = element.optJSONObject("tags")
            val maxSpeed = parseSpeedTag(tags?.optString("maxspeed"))
            val minSpeed = parseSpeedTag(tags?.optString("minspeed"))
            val highwayType = tags?.optString("highway")?.takeIf { it.isNotBlank() }
            if (maxSpeed == null && minSpeed == null && highwayType == null) continue
            val wayName = tags?.optString("name")?.takeIf { it.isNotBlank() }
                ?: tags?.optString("ref")?.takeIf { it.isNotBlank() }


            val geometry = element.optJSONArray("geometry") ?: continue
            if (geometry.length() == 0) continue

            var minLat = Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
            var minLon = Double.MAX_VALUE; var maxLon = -Double.MAX_VALUE
            val points = StringBuilder()

            for (j in 0 until geometry.length()) {
                val point = geometry.getJSONObject(j)
                val lat = point.optDouble("lat")
                val lon = point.optDouble("lon")
                if (lat < minLat) minLat = lat
                if (lat > maxLat) maxLat = lat
                if (lon < minLon) minLon = lon
                if (lon > maxLon) maxLon = lon
                if (points.isNotEmpty()) points.append(";")
                points.append(lat).append(",").append(lon)
            }

            results.add(
                SpeedLimitWay(
                    osmWayId = element.optLong("id"),
                    maxSpeedKmh = maxSpeed,
                    minSpeedKmh = minSpeed,
                    pointsEncoded = points.toString(),
                    name = wayName,
                    minLat = minLat, maxLat = maxLat,
                    minLon = minLon, maxLon = maxLon,
                    roadClassification = highwayType
                )
            )
        }
        return results
    }

    /**
     * Parses an OSM maxspeed/minspeed tag value into km/h.
     * Handles the known global variants:
     *   "120"          — plain numeric (km/h assumed)
     *   "120 km/h"     — explicit km/h suffix
     *   "70 mph"       — mph, converted to km/h
     *   "none"         — no limit (returns null)
     *   "unlimited"    — no limit (returns null)
     *   "walk"         — walking pace (~7 km/h)
     *   "living_street"— living street (~20 km/h)
     *   "ZA:urban"     — country:context format, resolved via defaults table
     *   "ZA:rural"     — same
     *   "ZA:motorway"  — same
     * Returns null for anything unrecognised — caller treats as untagged.
     */
    private fun parseSpeedTag(raw: String?): Int? {
        if (raw == null) return null
        val value = raw.trim().lowercase()

        // Special named values
        when (value) {
            "none", "unlimited" -> return null  // no limit — treat as untagged
            "walk" -> return 7
            "living_street" -> return 20
        }

        // country:context format e.g. "ZA:urban", "ZA:rural", "ZA:motorway"
        // Map common OSM context names to OSM highway types for DefaultSpeedLimits lookup
        if (value.contains(":")) {
            val parts = value.split(":")
            if (parts.size == 2) {
                val country = parts[0].lowercase()
                val context = when (parts[1].lowercase()) {
                    "urban"       -> "residential"
                    "rural"       -> "primary"
                    "motorway"    -> "motorway"
                    "trunk"       -> "trunk"
                    "living_street" -> return 20
                    "walk"        -> return 7
                    else          -> parts[1].lowercase()  // pass through as-is
                }
                // Defer to our defaults table — same source of truth
                return DefaultSpeedLimits.getDefaultSpeed(country, context)
            }
        }

        // "70 mph" or "120 km/h"
        if (value.contains(" ")) {
            val parts = value.split(" ")
            if (parts.size == 2) {
                val number = parts[0].toDoubleOrNull() ?: return null
                return when (parts[1].trim()) {
                    "mph" -> (number * 1.60934).toInt()
                    "km/h", "kmh", "kph" -> number.toInt()
                    else -> null
                }
            }
        }

        // Plain numeric — km/h assumed
        return value.toDoubleOrNull()?.toInt()
    }
}