package cvc.dashingdog.vaart

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Converts a completed trip (TripRecord + its TripPoints) into a GPX 1.1 XML string.
 *
 * Pure function, no Android framework or DB dependencies — takes everything it needs
 * as parameters so it's trivial to eyeball or unit-test in isolation.
 *
 * Output is intentionally minimal but valid GPX 1.1: no <ele>, since TripPoint doesn't
 * capture elevation and the GPX 1.1 schema defines every <trkpt> child as optional —
 * only the lat/lon attributes are required.
 */
object TripPointsToGpxWriter {

    // ISO 8601 UTC, matching what GpxParser.vb / GpxParser.kt (spoofer) both expect.
    private fun isoUtc(timestamp: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(timestamp))
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    /**
     * @param record The trip being exported.
     * @param points Track points for the trip, expected pre-sorted by timestamp
     *               (TripPointDao.getPointsForTrip already orders by timestamp ASC).
     * @param vehicleName Resolved display name (e.g. "BANTAM" or "ANONYMOUS") —
     *                     passed in rather than looked up, since this writer has no DB access.
     * @param deviceId Device identifier, e.g. "$Build.MANUFACTURER ${Build.MODEL}".
     */
    fun write(
        record: TripRecord,
        points: List<TripPoint>,
        vehicleName: String,
        deviceId: String
    ): String {
        val nameFmt = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
        val displayName = "$vehicleName — ${nameFmt.format(Date(record.startTime))}"

        val durationS = record.movingTimeMs / 1000
        val durationStr = "%d:%02d:%02d".format(durationS / 3600, (durationS % 3600) / 60, durationS % 60)
        val distanceStr = "%.1f km".format(record.distanceKm)

        val desc = "Vehicle: $vehicleName | Device: $deviceId | " +
            "Distance: $distanceStr | Moving time: $durationStr | Max speed: ${record.maxSpeedKmh} km/h"

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append(
            """<gpx version="1.1" creator="Vaart" xmlns="http://www.topografix.com/GPX/1/1" """ +
                """xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" """ +
                """xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">"""
        ).append('\n')

        sb.append("  <metadata>\n")
        sb.append("    <name>${escapeXml(displayName)}</name>\n")
        sb.append("    <desc>${escapeXml(desc)}</desc>\n")
        sb.append("  </metadata>\n")

        sb.append("  <trk>\n")
        sb.append("    <name>${escapeXml(displayName)}</name>\n")
        sb.append("    <trkseg>\n")
        for (p in points) {
            sb.append("""      <trkpt lat="${p.latitude}" lon="${p.longitude}">""").append('\n')
            sb.append("        <time>${isoUtc(p.timestamp)}</time>\n")
            sb.append("      </trkpt>\n")
        }
        sb.append("    </trkseg>\n")
        sb.append("  </trk>\n")
        sb.append("</gpx>\n")

        return sb.toString()
    }
}
