package cvc.dashingdog.vaart

import android.content.Context
import java.util.Locale
import kotlin.math.ceil

object SpeedUnitFormatter {

    private val MPH_COUNTRY_CODES = setOf(
        "US", "GB", "LR", "MM", "BS", "AG", "DM", "GD", "LC", "VC"
    )

    private fun resolvedUseMph(context: Context): Boolean {
        val prefs = context.getSharedPreferences(LocationService.PREFS_NAME, Context.MODE_PRIVATE)
        return when (prefs.getString("pref_speed_unit", "region")) {
            "mph" -> true
            "kmh" -> false
            else -> Locale.getDefault().country.uppercase() in MPH_COUNTRY_CODES
        }
    }

    fun formatSpeed(context: Context, kmh: Int): String =
        if (resolvedUseMph(context)) ceil(kmh * 0.621371).toInt().toString() else kmh.toString()

    fun formatDistance(context: Context, km: Double): String =
        if (resolvedUseMph(context)) "%.1f mi".format(ceil(km * 0.621371 * 10) / 10) else "%.1f km".format(km)

    fun unitLabel(context: Context): String =
        if (resolvedUseMph(context)) "mph" else "km/h"

    fun formatOdometer(context: Context, km: Double): String {
        val displayValue = if (resolvedUseMph(context)) ceil(km * 0.621371) else km
        val total = displayValue.toInt().coerceIn(0, 999999)
        val thousands = total / 1000
        val remainder = total % 1000
        val unit = if (resolvedUseMph(context)) "mi" else "km"
        return "%03d %03d %s".format(thousands, remainder, unit)
    }
}