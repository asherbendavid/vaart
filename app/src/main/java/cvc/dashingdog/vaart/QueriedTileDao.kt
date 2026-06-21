package cvc.dashingdog.vaart

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface QueriedTileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTile(tile: QueriedTile)

    @Query("SELECT * FROM queried_tiles WHERE tileLatKey = :latKey AND tileLonKey = :lonKey LIMIT 1")
    suspend fun getTile(latKey: Int, lonKey: Int): QueriedTile?
}