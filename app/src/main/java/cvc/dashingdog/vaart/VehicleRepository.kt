package cvc.dashingdog.vaart

import android.content.Context

class VehicleRepository(context: Context) {

    private val dao = VaartDatabase.getInstance(context).vehicleDao()

    suspend fun getAllVehicles(): List<Vehicle> = dao.getAllVehicles()

    suspend fun getVehicleById(id: Int): Vehicle? = dao.getVehicleById(id)

    suspend fun saveVehicle(vehicle: Vehicle): Long = dao.insertVehicle(vehicle)

    suspend fun updateVehicle(vehicle: Vehicle) = dao.updateVehicle(vehicle)

    suspend fun deleteVehicle(vehicle: Vehicle) = dao.deleteVehicle(vehicle)

    suspend fun updateOdometer(vehicle: Vehicle, newOdoKm: Double) {
        dao.updateVehicle(
            vehicle.copy(
                odometerKm = newOdoKm,
                lastUsedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun saveTripB(vehicle: Vehicle, tripB: TripData) {
        dao.updateVehicle(
            vehicle.copy(
                tripBDistanceKm = tripB.distanceKm,
                tripBMovingTimeMs = tripB.movingTimeMs,
                tripBMaxSpeedKmh = tripB.maxSpeedKmh,
                lastUsedAt = System.currentTimeMillis()
            )
        )
    }
}