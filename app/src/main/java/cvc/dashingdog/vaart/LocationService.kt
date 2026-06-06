package cvc.dashingdog.vaart

import android.app.*
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.os.*
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LocationService : Service() {

    companion object {
        const val CHANNEL_ID = "vaart_tracking"
        const val NOTIFICATION_ID = 1
        const val PREFS_NAME = "vaart_prefs"
        const val KEY_A_DISTANCE = "a_distance"
        const val KEY_A_MOVING_TIME = "a_moving_time"
        const val KEY_A_STOP_TIME = "a_stop_time"
        const val KEY_B_DISTANCE = "b_distance"
        const val KEY_B_MOVING_TIME = "b_moving_time"
        const val KEY_TRIP_ACTIVE = "trip_active"
        const val MOVING_THRESHOLD_KMH = 5f
        const val PAUSE_THRESHOLD_MS = 3 * 60 * 1000L
        const val TRIP_A_EXPIRY_MS = 30 * 60 * 1000L
    }

    inner class LocalBinder : Binder() {
        fun getService(): LocationService = this@LocationService
    }

    private val binder = LocalBinder()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var prefs: SharedPreferences

    private var lastLocation: Location? = null
    private var lastUpdateTime: Long = 0L
    private var isMoving = false
    private var belowThresholdSince: Long = 0L

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        loadPersistedState()
        setupLocationUpdates()
    }

    private fun loadPersistedState() {
        val stopTime = prefs.getLong(KEY_A_STOP_TIME, 0L)
        val tripActive = prefs.getBoolean(KEY_TRIP_ACTIVE, false)

        if (!tripActive && stopTime > 0 &&
            System.currentTimeMillis() - stopTime > TRIP_A_EXPIRY_MS) {
            clearTripAPrefs()
        }

        _uiState.value = _uiState.value.copy(
            isRunning = tripActive,
            tripA = TripData(
                distanceKm = prefs.getFloat(KEY_A_DISTANCE, 0f).toDouble(),
                movingTimeMs = prefs.getLong(KEY_A_MOVING_TIME, 0L)
            ),
            tripB = TripData(
                distanceKm = prefs.getFloat(KEY_B_DISTANCE, 0f).toDouble(),
                movingTimeMs = prefs.getLong(KEY_B_MOVING_TIME, 0L)
            )
        )
    }

    private fun setupLocationUpdates() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { processLocation(it) }
            }
        }
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 1000L
        ).setMinUpdateIntervalMillis(500L).build()

        try {
            fusedLocationClient.requestLocationUpdates(
                request, locationCallback, Looper.getMainLooper()
            )
        } catch (e: SecurityException) { /* permission not granted */ }
    }

    private fun processLocation(location: Location) {
        val now = System.currentTimeMillis()
        val deltaMs = if (lastUpdateTime > 0) now - lastUpdateTime else 0L
        lastUpdateTime = now

        val speedKmhFloat = if (location.hasSpeed()) location.speed * 3.6f else 0f
        val displaySpeed = if (speedKmhFloat < 3f) 0 else speedKmhFloat.toInt()

        // Moving state
        when {
            speedKmhFloat >= MOVING_THRESHOLD_KMH -> {
                isMoving = true
                belowThresholdSince = 0L
            }
            isMoving -> {
                if (belowThresholdSince == 0L) belowThresholdSince = now
                if (now - belowThresholdSince > PAUSE_THRESHOLD_MS) isMoving = false
            }
        }

        var tripA = _uiState.value.tripA
        var tripB = _uiState.value.tripB

        if (_uiState.value.isRunning && lastLocation != null && deltaMs > 0) {
            if (speedKmhFloat >= MOVING_THRESHOLD_KMH) {
                val distKm = lastLocation!!.distanceTo(location) / 1000.0
                tripA = tripA.copy(distanceKm = tripA.distanceKm + distKm)
                tripB = tripB.copy(distanceKm = tripB.distanceKm + distKm)
            }
            if (isMoving) {
                tripA = tripA.copy(movingTimeMs = tripA.movingTimeMs + deltaMs)
                tripB = tripB.copy(movingTimeMs = tripB.movingTimeMs + deltaMs)
            }
            saveTrips(tripA, tripB)
        }

        lastLocation = location
        _uiState.value = _uiState.value.copy(
            speedKmh = displaySpeed,
            gpsAccuracy = location.accuracy,
            tripA = tripA,
            tripB = tripB
        )
    }

    fun startTrip() {
        prefs.edit().putBoolean(KEY_TRIP_ACTIVE, true).apply()
        lastLocation = null
        lastUpdateTime = 0L
        isMoving = false
        belowThresholdSince = 0L
        _uiState.value = _uiState.value.copy(isRunning = true)
    }

    fun stopTrip() {
        prefs.edit()
            .putBoolean(KEY_TRIP_ACTIVE, false)
            .putLong(KEY_A_STOP_TIME, System.currentTimeMillis())
            .apply()
        isMoving = false
        belowThresholdSince = 0L
        lastLocation = null
        _uiState.value = _uiState.value.copy(isRunning = false)
    }

    fun resetTripA() {
        clearTripAPrefs()
        _uiState.value = _uiState.value.copy(tripA = TripData())
    }

    fun resetTripB() {
        prefs.edit()
            .putFloat(KEY_B_DISTANCE, 0f)
            .putLong(KEY_B_MOVING_TIME, 0L)
            .apply()
        _uiState.value = _uiState.value.copy(tripB = TripData())
    }

    private fun clearTripAPrefs() {
        prefs.edit()
            .putFloat(KEY_A_DISTANCE, 0f)
            .putLong(KEY_A_MOVING_TIME, 0L)
            .putLong(KEY_A_STOP_TIME, 0L)
            .apply()
    }

    private fun saveTrips(tripA: TripData, tripB: TripData) {
        prefs.edit()
            .putFloat(KEY_A_DISTANCE, tripA.distanceKm.toFloat())
            .putLong(KEY_A_MOVING_TIME, tripA.movingTimeMs)
            .putFloat(KEY_B_DISTANCE, tripB.distanceKm.toFloat())
            .putLong(KEY_B_MOVING_TIME, tripB.movingTimeMs)
            .apply()
    }

    private fun createNotificationChannel() {
        NotificationChannel(
            CHANNEL_ID, "Vaart Tracking",
            NotificationManager.IMPORTANCE_LOW
        ).also {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(it)
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Vaart")
            .setContentText("Speed tracking active")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}