package cvc.dashingdog.vaart

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface TripRecordDao {

    @Insert
    suspend fun insertTripRecord(record: TripRecord): Long

    @Query("SELECT * FROM trip_records ORDER BY startTime DESC")
    suspend fun getAllTripRecords(): List<TripRecord>

    @Query("SELECT * FROM trip_records WHERE vehicleId = :vehicleId ORDER BY startTime DESC")
    suspend fun getTripRecordsForVehicle(vehicleId: Int): List<TripRecord>

    @Query("SELECT * FROM trip_records WHERE id = :id")
    suspend fun getTripRecordById(id: Int): TripRecord?

    @Delete
    suspend fun deleteTripRecord(record: TripRecord)

    @Update
    suspend fun updateTripRecord(record: TripRecord)
}