package cvc.dashingdog.vaart

data class TripData(
    val distanceKm: Double = 0.0,
    val movingTimeMs: Long = 0L
) {
    val avgSpeedKmh: Int
        get() = if (movingTimeMs > 0)
            (distanceKm / (movingTimeMs / 3_600_000.0)).toInt()
        else 0

    val formattedDistance: String
        get() = "%.1f km".format(distanceKm)

    val formattedTime: String
        get() {
            val s = movingTimeMs / 1000
            return "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
        }
}