package cvc.dashingdog.vaart

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Vehicle::class,
        TripRecord::class,
        TripPoint::class,
        SpeedLimitWay::class,
        QueriedTile::class
    ],
    version = 3,
    exportSchema = false
)
abstract class VaartDatabase : RoomDatabase() {

    abstract fun vehicleDao(): VehicleDao
    abstract fun tripRecordDao(): TripRecordDao
    abstract fun tripPointDao(): TripPointDao
    abstract fun speedLimitWayDao(): SpeedLimitWayDao
    abstract fun queriedTileDao(): QueriedTileDao

    companion object {
        @Volatile
        private var INSTANCE: VaartDatabase? = null

        fun getInstance(context: Context): VaartDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    VaartDatabase::class.java,
                    "vaart_database"
                )
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}