package cvc.dashingdog.vaart

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trip_points",
    foreignKeys = [ForeignKey(
        entity = TripRecord::class,
        parentColumns = ["id"],
        childColumns = ["tripId"],
        onDelete = ForeignKey.CASCADE   // delete points when record deleted
    )],
    indices = [Index("tripId")]         // speeds up "get all points for trip X"
)
data class TripPoint(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tripId: Int,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Int,
    val accuracyM: Float
)