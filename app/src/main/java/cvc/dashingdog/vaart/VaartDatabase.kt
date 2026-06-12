package cvc.dashingdog.vaart

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Vehicle::class], version = 1, exportSchema = false)
abstract class VaartDatabase : RoomDatabase() {

    abstract fun vehicleDao(): VehicleDao

    companion object {
        @Volatile
        private var INSTANCE: VaartDatabase? = null

        fun getInstance(context: Context): VaartDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    VaartDatabase::class.java,
                    "vaart_database"
                ).build().also { INSTANCE = it }
            }
        }
    }
}