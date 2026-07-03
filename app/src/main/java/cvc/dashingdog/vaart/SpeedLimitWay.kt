package cvc.dashingdog.vaart

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "speed_limit_ways")
data class SpeedLimitWay(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val osmWayId: Long,
    val maxSpeedKmh: Int? = null,
    val minSpeedKmh: Int? = null,
    val pointsEncoded: String, // "lat1,lon1;lat2,lon2;..."
    val name: String? = null,
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
    val roadClassification: String? = null
)