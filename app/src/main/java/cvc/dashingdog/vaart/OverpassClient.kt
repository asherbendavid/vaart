package cvc.dashingdog.vaart

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object OverpassClient {

    private const val ENDPOINT = "https://overpass-api.de/api/interpreter"

    /**
     * Fetches ways tagged with maxspeed/minspeed inside the bounding box.
     * Returns null on network failure (caller should NOT mark the tile as
     * queried in that case, so it retries later). Returns an empty list if
     * the fetch succeeded but genuinely found nothing — that tile is done.
     * Blocking call — must run on a background coroutine.
     */
    fun fetchSpeedLimitWays(south: Double, west: Double, north: Double, east: Double): List<SpeedLimitWay>? {
        val query = """
            [out:json][timeout:25];
            (
              way[maxspeed]($south,$west,$north,$east);
              way[minspeed]($south,$west,$north,$east);
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
                return null
            }
            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            parseResponse(responseText)
        } catch (e: Exception) {
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
            val maxSpeed = tags?.optString("maxspeed")?.toIntOrNull()
            val minSpeed = tags?.optString("minspeed")?.toIntOrNull()
            if (maxSpeed == null && minSpeed == null) continue
            val wayName = tags?.optString("name")?.takeIf { it.isNotBlank() }
                ?: tags?.optString("ref")?.takeIf { it.isNotBlank() }
            val roadRank = tags?.optString("highway")


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
                    roadClassification = roadRank
                )
            )
        }
        return results
    }
}