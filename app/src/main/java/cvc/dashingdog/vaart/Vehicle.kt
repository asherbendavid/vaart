package cvc.dashingdog.vaart

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class Vehicle(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val registration: String? = null,
    val notes: String? = null,
    val odometerKm: Double = 0.0,
    val tripBDistanceKm: Double = 0.0,
    val tripBMovingTimeMs: Long = 0L,
    val tripBMaxSpeedKmh: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis(),
    val tripBUnreliable: Boolean = false,
)