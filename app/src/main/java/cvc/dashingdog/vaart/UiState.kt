package cvc.dashingdog.vaart

data class UiState(
    val speedKmh: Int = 0,
    val gpsAccuracy: Float = 0f,
    val isRunning: Boolean = false,
    val tripA: TripData = TripData(),
    val tripB: TripData = TripData(),
    val odometerKm: Double = 0.0
)