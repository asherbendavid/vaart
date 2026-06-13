package cvc.dashingdog.vaart

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TripPointDao {

    @Insert
    suspend fun insertTripPoint(point: TripPoint)

    @Query("SELECT * FROM trip_points WHERE tripId = :tripId ORDER BY timestamp ASC")
    suspend fun getPointsForTrip(tripId: Int): List<TripPoint>

    @Query("DELETE FROM trip_points WHERE tripId = :tripId")
    suspend fun deletePointsForTrip(tripId: Int)
}