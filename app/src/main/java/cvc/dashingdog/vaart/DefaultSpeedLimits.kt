package cvc.dashingdog.vaart

import android.content.Context
import org.json.JSONObject

/**
 * Reads the bundled highway_speed_defaults.json asset and provides default
 * speed limit lookup by country code and highway type.
 *
 * Used when a matched OSM way has no explicit maxspeed tag — returns the
 * legally implied default for that road type in that country.
 *
 * The JSON asset is loaded once on first use and cached in memory.
 * To update speed limit data, replace the asset file and rebuild.
 */
object DefaultSpeedLimits {

    private var limits: JSONObject? = null

    /**
     * Call once during app startup (or lazily on first lookup) to load the asset.
     * Safe to call multiple times — only loads once.
     */
    fun init(context: Context) {
        if (limits != null) return
        try {
            val json = context.assets.open("highway_speed_defaults.json")
                .bufferedReader().use { it.readText() }
            limits = JSONObject(json).optJSONObject("limits")
        } catch (e: Exception) {
            limits = JSONObject() // empty — all lookups will return null
        }
    }

    /**
     * Returns the default speed limit in km/h for the given country and highway type,
     * or null if no match is found and no default entry exists.
     *
     * Lookup order:
     * 1. Exact country + highway type (e.g. "za" + "motorway")
     * 2. "default" entry + highway type (catches countries not in the file)
     * 3. null
     *
     * @param countryCode ISO 3166-1 alpha-2 lowercase (e.g. "za", "us")
     * @param highwayType OSM highway tag value (e.g. "motorway", "secondary")
     */
    fun getDefaultSpeed(countryCode: String?, highwayType: String?): Int? {
        val l = limits ?: return null
        if (countryCode == null || highwayType == null) return null

        val country = l.optJSONObject(countryCode.lowercase())
        val speed = country?.optInt(highwayType, -1)?.takeIf { it >= 0 }
        if (speed != null) return if (speed == 999) null else speed

        // Fall back to "default" entry
        val default = l.optJSONObject("default")
        val defaultSpeed = default?.optInt(highwayType, -1)?.takeIf { it >= 0 }
        return if (defaultSpeed == 999) null else defaultSpeed
    }
}