package cvc.dashingdog.vaart

import android.content.Context
import java.util.Locale
import kotlin.math.ceil

object SpeedUnitFormatter {

    private const val KM_TO_MI = 0.621371
    internal val MPH_COUNTRY_CODES = setOf(
        "US", "GB", "LR", "MM", "BS", "AG", "DM", "GD", "LC", "VC"
    )

    private fun resolvedUseMph(context: Context): Boolean {
        val prefs = context.getSharedPreferences(LocationService.PREFS_NAME, Context.MODE_PRIVATE)
        return when (prefs.getString("pref_speed_unit", "region")) {
            "mph" -> true
            "kmh" -> false
            else -> LocationService.detectedUseMph
                ?: (Locale.getDefault().country.uppercase() in MPH_COUNTRY_CODES)
        }
    }

    fun unitConversionFactor(context: Context): Double =
        if (resolvedUseMph(context)) 1.0 / KM_TO_MI else 1.0

    fun formatSpeed(context: Context, kmh: Int): String =
        if (resolvedUseMph(context)) ceil(kmh * KM_TO_MI).toInt().toString() else kmh.toString()

    fun formatDistance(context: Context, km: Double): String =
        if (resolvedUseMph(context)) "%.1f mi".format(ceil(km * KM_TO_MI * 10) / 10) else "%.1f km".format(km)

    fun unitLabel(context: Context): String =
        if (resolvedUseMph(context)) "mph" else "km/h"

    fun formatOdometer(context: Context, km: Double): String {
        val displayValue = if (resolvedUseMph(context)) ceil(km * KM_TO_MI) else km
        val total = displayValue.toInt().coerceIn(0, 999999)
        val thousands = total / 1000
        val remainder = total % 1000
        val unit = if (resolvedUseMph(context)) "mi" else "km"
        return "%03d %03d %s".format(thousands, remainder, unit)
    }
}