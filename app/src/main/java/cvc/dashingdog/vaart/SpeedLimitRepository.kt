package cvc.dashingdog.vaart

import android.content.Context

class SpeedLimitRepository(context: Context) {

    private val db = VaartDatabase.getInstance(context)
    private val wayDao = db.speedLimitWayDao()
    private val tileDao = db.queriedTileDao()

    suspend fun insertWays(ways: List<SpeedLimitWay>) = wayDao.insertWays(ways)

    suspend fun getCandidateWays(south: Double, north: Double, west: Double, east: Double): List<SpeedLimitWay> =
        wayDao.getCandidateWays(south, north, west, east)

    suspend fun getTile(latKey: Int, lonKey: Int): QueriedTile? = tileDao.getTile(latKey, lonKey)

    suspend fun markTileQueried(latKey: Int, lonKey: Int) =
        tileDao.insertTile(QueriedTile(tileLatKey = latKey, tileLonKey = lonKey))
}