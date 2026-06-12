package cvc.dashingdog.vaart

import androidx.room.*

@Dao
interface VehicleDao {

    @Query("SELECT * FROM vehicles ORDER BY lastUsedAt DESC")
    suspend fun getAllVehicles(): List<Vehicle>

    @Query("SELECT * FROM vehicles WHERE id = :id")
    suspend fun getVehicleById(id: Int): Vehicle?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicle(vehicle: Vehicle): Long

    @Update
    suspend fun updateVehicle(vehicle: Vehicle)

    @Delete
    suspend fun deleteVehicle(vehicle: Vehicle)
}