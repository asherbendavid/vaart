package cvc.dashingdog.vaart

import android.content.Context

class VehicleRepository(context: Context) {

    private val db = VaartDatabase.getInstance(context)
    private val dao = db.vehicleDao()
    private val tripRecordDao = db.tripRecordDao()
    private val tripPointDao = db.tripPointDao()
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

    suspend fun saveTripB(vehicle: Vehicle, tripB: TripData, unreliable: Boolean = false) {
        dao.updateVehicle(
            vehicle.copy(
                tripBDistanceKm = tripB.distanceKm,
                tripBMovingTimeMs = tripB.movingTimeMs,
                tripBMaxSpeedKmh = tripB.maxSpeedKmh,
                tripBUnreliable = unreliable,
                lastUsedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun insertTripRecord(record: TripRecord): Long =
        tripRecordDao.insertTripRecord(record)

    suspend fun getAllTripRecords(): List<TripRecord> =
        tripRecordDao.getAllTripRecords()

    suspend fun getTripRecordsForVehicle(vehicleId: Int): List<TripRecord> =
        tripRecordDao.getTripRecordsForVehicle(vehicleId)

    suspend fun deleteTripRecord(record: TripRecord) =
        tripRecordDao.deleteTripRecord(record)

    suspend fun insertTripPoint(point: TripPoint) =
        tripPointDao.insertTripPoint(point)

    suspend fun getPointsForTrip(tripId: Int): List<TripPoint> =
        tripPointDao.getPointsForTrip(tripId)

    suspend fun getTripRecordById(id: Int): TripRecord? =
        tripRecordDao.getTripRecordById(id)

    suspend fun updateTripRecord(record: TripRecord) =
        tripRecordDao.updateTripRecord(record)

    suspend fun getMostRecentTripRecord(vehicleId: Int, type: String): TripRecord? =
        tripRecordDao.getMostRecentTripRecord(vehicleId, type)

    suspend fun getResetRecordsInRange(vehicleId: Int, start: Long, end: Long): List<TripRecord> =
        tripRecordDao.getResetRecordsInRange(vehicleId, start, end)
}