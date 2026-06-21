package cvc.dashingdog.vaart

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "queried_tiles")
data class QueriedTile(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val tileLatKey: Int,
    val tileLonKey: Int,
    val fetchedAt: Long = System.currentTimeMillis()
)