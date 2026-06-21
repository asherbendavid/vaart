package cvc.dashingdog.vaart

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SpeedLimitWayDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWays(ways: List<SpeedLimitWay>)

    @Query("""
        SELECT * FROM speed_limit_ways
        WHERE minLat <= :north AND maxLat >= :south
          AND minLon <= :east AND maxLon >= :west
    """)
    suspend fun getCandidateWays(south: Double, north: Double, west: Double, east: Double): List<SpeedLimitWay>
}