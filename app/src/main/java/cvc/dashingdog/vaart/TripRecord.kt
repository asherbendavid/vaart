package cvc.dashingdog.vaart

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trip_records")
data class TripRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val vehicleId: Int,          // -1 for anonymous
    val startTime: Long,
    val endTime: Long,
    val distanceKm: Double,
    val movingTimeMs: Long,
    val maxSpeedKmh: Int,
    val notes: String? = null,   // spare field — useful later, costs nothing now
    val type: String = TYPE_TRIP
){
    companion object {
        const val TYPE_TRIP = "trip"
        const val TYPE_TRIP_A_RESET = "trip_a_reset"
        const val TYPE_TRIP_B_RESET = "trip_b_reset"
    }
}