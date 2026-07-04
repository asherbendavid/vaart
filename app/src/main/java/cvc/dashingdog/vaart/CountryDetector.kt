package cvc.dashingdog.vaart

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Detects the current country from a GPS fix using Nominatim reverse geocoding.
 * Result is cached in SharedPreferences to avoid repeated network calls.
 *
 * Returns an ISO 3166-1 alpha-2 lowercase country code (e.g. "za", "us").
 * Falls back to device locale country if Nominatim is unreachable.
 *
 * Blocking network call — always run on a background coroutine.
 */
object CountryDetector {

    private const val PREFS_NAME = "country_detector"
    private const val PREF_COUNTRY_CODE = "country_code"
    private const val PREF_FETCHED_AT = "fetched_at"
    private const val PREF_CACHED_LAT = "cached_lat"
    private const val PREF_CACHED_LON = "cached_lon"

    private const val CACHE_EXPIRY_MS = 30L * 24 * 60 * 60 * 1000  // 30 days
    private const val BORDER_CROSSING_THRESHOLD_DEG = 0.45           // ~50km

    /**
     * Returns the current country code, using cache if still valid.
     * Falls back to stale cache, then device locale, if network fails.
     */
    fun getCountryCode(context: Context, lat: Double, lon: Double): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cached = prefs.getString(PREF_COUNTRY_CODE, null)
        val age = System.currentTimeMillis() - prefs.getLong(PREF_FETCHED_AT, 0L)

        if (cached != null && age < CACHE_EXPIRY_MS) return cached

        val fetched = fetchFromNominatim(lat, lon)
        return if (fetched != null) {
            prefs.edit()
                .putString(PREF_COUNTRY_CODE, fetched)
                .putLong(PREF_FETCHED_AT, System.currentTimeMillis())
                .putFloat(PREF_CACHED_LAT, lat.toFloat())
                .putFloat(PREF_CACHED_LON, lon.toFloat())
                .apply()
            fetched
        } else {
            cached ?: deviceLocaleCountry(context)
        }
    }

    /**
     * Invalidates the cache if the current position is more than ~50km
     * from where the country was last detected. Call on each location update.
     */
    fun invalidateIfMoved(context: Context, lat: Double, lon: Double) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedLat = prefs.getFloat(PREF_CACHED_LAT, Float.MAX_VALUE).toDouble()
        val cachedLon = prefs.getFloat(PREF_CACHED_LON, Float.MAX_VALUE).toDouble()
        val dLat = lat - cachedLat
        val dLon = lon - cachedLon
        if (Math.sqrt(dLat * dLat + dLon * dLon) > BORDER_CROSSING_THRESHOLD_DEG) {
            prefs.edit().putLong(PREF_FETCHED_AT, 0L).apply()
        }
    }

    private fun fetchFromNominatim(lat: Double, lon: Double): String? {
        return try {
            val url = URL(
                "https://nominatim.openstreetmap.org/reverse" +
                        "?lat=$lat&lon=$lon&format=json&zoom=3"
            )
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", "Vaart/1.0 (cvc.dashingdog.vaart)")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                return null
            }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            JSONObject(text)
                .optJSONObject("address")
                ?.optString("country_code")
                ?.lowercase()
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    private fun deviceLocaleCountry(context: Context): String? {
        val country = context.resources.configuration.locales[0].country
        return country.lowercase().takeIf { it.isNotBlank() }
    }
}