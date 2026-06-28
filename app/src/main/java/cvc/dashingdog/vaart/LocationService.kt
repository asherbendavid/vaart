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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper

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
        const val MOVING_THRESHOLD_KMH = 2.5f
        const val PAUSE_THRESHOLD_MS = 3 * 60 * 1000L
        const val TRIP_A_EXPIRY_MS = 30 * 60 * 1000L
        const val KEY_A_MAX_SPEED = "a_max_speed"
        const val KEY_B_MAX_SPEED = "b_max_speed"
        const val KEY_ODO_DISTANCE = "odo_distance"
        const val KEY_ACTIVE_VEHICLE_ID = "active_vehicle_id"
        const val OVERSPEED_THRESHOLD_KMH = 90f // change speed limit here
        private const val ALERT_BURST_INTERVAL_MS = 600L
        private const val ALERT_REPEAT_MS = 60_000L
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

    private lateinit var repository: VehicleRepository
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentTripId: Int = -1
    private var tripStartTime: Long = 0L
    private val pendingPoints = mutableListOf<TripPoint>()
    private var currentVehicleId: Int = -1  // kept in sync via a new setter
    private lateinit var soundPool: SoundPool
    private var alertSoundId: Int = 0
    private val alertHandler = Handler(Looper.getMainLooper())
    private var lastAlertTime = 0L
    private var alertBurstCount = 0
    private var sessionDistanceKm: Double = 0.0
    private var sessionMovingTimeMs: Long = 0L
    private var sessionMaxSpeedKmh: Int = 0
    private lateinit var speedLimitManager: SpeedLimitManager
    private var locationUpdateCounter = 0
    private var currentMaxSpeedLimit: Int? = null
    private var currentMinSpeedLimit: Int? = null
    private var wasUnderspeed = false

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        loadPersistedState()
        initSoundPool()
        setupLocationUpdates()
        repository = VehicleRepository(this)
        speedLimitManager = SpeedLimitManager(this)
    }

    private fun overspeedGraceMarginKmh(): Float {
        val raw = prefs.getString("pref_overspeed_grace_value", "0")?.toFloatOrNull() ?: 0f
        return raw * SpeedUnitFormatter.unitConversionFactor(this).toFloat()
    }
    private fun resolveAlertUsage(): Int {
        return when (prefs.getString("pref_audio_channel", "alarm")) {
            "notification" -> AudioAttributes.USAGE_NOTIFICATION
            "ringtone" -> AudioAttributes.USAGE_NOTIFICATION_RINGTONE
            "media" -> AudioAttributes.USAGE_MEDIA
            else -> AudioAttributes.USAGE_ALARM
        }
    }

    private fun initSoundPool() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(resolveAlertUsage())
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(3)  // allow 3 simultaneous plays for the overlap
            .setAudioAttributes(audioAttributes)
            .build()
        alertSoundId = soundPool.load(this, R.raw.overspeed_alert, 1)
    }

    /** Called when the audio channel setting changes while the service is already running. */
    fun reloadAudioSettings() {
        soundPool.release()
        initSoundPool()
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
            odometerKm = prefs.getFloat(KEY_ODO_DISTANCE, 0f).toDouble(),
            tripA = TripData(
                distanceKm = prefs.getFloat(KEY_A_DISTANCE, 0f).toDouble(),
                movingTimeMs = prefs.getLong(KEY_A_MOVING_TIME, 0L),
                maxSpeedKmh = prefs.getInt(KEY_A_MAX_SPEED, 0)
            ),
            tripB = TripData(
                distanceKm = prefs.getFloat(KEY_B_DISTANCE, 0f).toDouble(),
                movingTimeMs = prefs.getLong(KEY_B_MOVING_TIME, 0L),
                maxSpeedKmh = prefs.getInt(KEY_B_MAX_SPEED, 0)
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
        val displaySpeed = if (speedKmhFloat < 1f) 0 else speedKmhFloat.toInt()

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
        var newOdo = _uiState.value.odometerKm

        if (_uiState.value.isRunning && lastLocation != null && deltaMs > 0) {
            if (speedKmhFloat >= MOVING_THRESHOLD_KMH) {
                val distKm = lastLocation!!.distanceTo(location) / 1000.0
                sessionDistanceKm += distKm
                sessionMaxSpeedKmh = maxOf(sessionMaxSpeedKmh, displaySpeed)
                tripA = tripA.copy(
                    distanceKm = tripA.distanceKm + distKm,
                    maxSpeedKmh = maxOf(tripA.maxSpeedKmh, displaySpeed)
                )
                tripB = tripB.copy(
                    distanceKm = tripB.distanceKm + distKm,
                    maxSpeedKmh = maxOf(tripB.maxSpeedKmh, displaySpeed)
                )
                newOdo = _uiState.value.odometerKm + distKm
            }
            if (isMoving) {
                sessionMovingTimeMs += deltaMs
                tripA = tripA.copy(movingTimeMs = tripA.movingTimeMs + deltaMs)
                tripB = tripB.copy(movingTimeMs = tripB.movingTimeMs + deltaMs)
            }
            if (_uiState.value.isRunning && currentTripId != -1) {
                pendingPoints.add(
                    TripPoint(
                        tripId = currentTripId,
                        timestamp = now,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        speedKmh = displaySpeed,
                        accuracyM = location.accuracy
                    )
                )
                if (pendingPoints.size >= 10) flushPoints()
            }
            saveState(tripA, tripB, newOdo)
        }

        locationUpdateCounter++
        if (locationUpdateCounter % 3 == 0) {
            val lat = location.latitude
            val lon = location.longitude
            serviceScope.launch {
                speedLimitManager.ensureTileCached(lat, lon)
                val (maxLimit, minLimit) = speedLimitManager.lookupSpeedLimits(lat, lon)
                currentMaxSpeedLimit = maxLimit
                currentMinSpeedLimit = minLimit
            }
        }

        val grace = overspeedGraceMarginKmh()
        val isOverSpeed = _uiState.value.isRunning &&
                currentMaxSpeedLimit != null && speedKmhFloat > currentMaxSpeedLimit!! + grace
        if (isOverSpeed) triggerOverspeedAlert()

        val isUnderSpeed = _uiState.value.isRunning &&
                currentMinSpeedLimit != null && speedKmhFloat < currentMinSpeedLimit!! - grace
        if (isUnderSpeed && !wasUnderspeed) triggerUnderspeedAlert()
        wasUnderspeed = isUnderSpeed

        lastLocation = location
        _uiState.value = _uiState.value.copy(
            speedKmh = displaySpeed,
            gpsAccuracy = location.accuracy,
            tripA = tripA,
            tripB = tripB,
            odometerKm = newOdo,
            isOverspeed = isOverSpeed,
            isUnderspeed = isUnderSpeed,
            maxSpeedLimitKmh = currentMaxSpeedLimit,
            minSpeedLimitKmh = currentMinSpeedLimit
        )

    }

    fun startTrip(vehicleId: Int) {
        currentVehicleId = vehicleId
        sessionDistanceKm = 0.0
        sessionMaxSpeedKmh = 0
        sessionMovingTimeMs = 0L
        tripStartTime = System.currentTimeMillis()
        prefs.edit().putBoolean(KEY_TRIP_ACTIVE, true).apply()
        lastLocation = null
        lastUpdateTime = 0L
        isMoving = false
        belowThresholdSince = 0L
        _uiState.value = _uiState.value.copy(isRunning = true)

        serviceScope.launch {
            val record = TripRecord(
                vehicleId = vehicleId,
                startTime = tripStartTime,
                endTime = 0L,
                distanceKm = 0.0,
                movingTimeMs = 0L,
                maxSpeedKmh = 0
            )
            currentTripId = repository.insertTripRecord(record).toInt()
        }
    }

    fun stopTrip() {
        prefs.edit()
            .putBoolean(KEY_TRIP_ACTIVE, false)
            .putLong(KEY_A_STOP_TIME, System.currentTimeMillis())
            .apply()
        isMoving = false
        belowThresholdSince = 0L
        lastLocation = null

        val finalState = _uiState.value
        _uiState.value = finalState.copy(isRunning = false)

        if (currentTripId != -1) {
            flushPoints()
            val tripId = currentTripId
            serviceScope.launch {
                repository.getTripRecordById(tripId)?.let { record ->
                    repository.updateTripRecord(
                        record.copy(
                            endTime = System.currentTimeMillis(),
                            distanceKm = sessionDistanceKm,
                            movingTimeMs = sessionMovingTimeMs,
                            maxSpeedKmh = sessionMaxSpeedKmh
                        )
                    )
                }
            }
            currentTripId = -1
        }
    }

    fun resetTripA() {
        clearTripAPrefs()
        _uiState.value = _uiState.value.copy(tripA = TripData())
    }

    fun resetTripB() {
        prefs.edit()
            .putFloat(KEY_B_DISTANCE, 0f)
            .putLong(KEY_B_MOVING_TIME, 0L)
            .putInt(KEY_B_MAX_SPEED, 0)
            .apply()
        _uiState.value = _uiState.value.copy(tripB = TripData())
        if (currentVehicleId != -1) {
            serviceScope.launch {
                repository.getVehicleById(currentVehicleId)?.let { vehicle ->
                    repository.saveTripB(vehicle, TripData())
                }
            }
        }
    }

    private fun clearTripAPrefs() {
        prefs.edit()
            .putFloat(KEY_A_DISTANCE, 0f)
            .putLong(KEY_A_MOVING_TIME, 0L)
            .putLong(KEY_A_STOP_TIME, 0L)
            .putInt(KEY_A_MAX_SPEED, 0)
            .apply()
    }

    private fun flushPoints() {
        val toFlush = pendingPoints.toList()
        pendingPoints.clear()
        serviceScope.launch {
            toFlush.forEach { repository.insertTripPoint(it) }
        }
    }

    private fun saveState(tripA: TripData, tripB: TripData, odoKm: Double) {
        prefs.edit()
            .putFloat(KEY_A_DISTANCE, tripA.distanceKm.toFloat())
            .putLong(KEY_A_MOVING_TIME, tripA.movingTimeMs)
            .putInt(KEY_A_MAX_SPEED, tripA.maxSpeedKmh)
            .putFloat(KEY_B_DISTANCE, tripB.distanceKm.toFloat())
            .putLong(KEY_B_MOVING_TIME, tripB.movingTimeMs)
            .putInt(KEY_B_MAX_SPEED, tripB.maxSpeedKmh)
            .putFloat(KEY_ODO_DISTANCE, odoKm.toFloat())
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

    fun loadVehicleData(vehicleId: Int, odometerKm: Double, tripB: TripData) {
        currentVehicleId = vehicleId
        lastLocation = null // prevent phantom distance on switch
        val current = _uiState.value
        _uiState.value = current.copy(
            odometerKm = odometerKm,
            tripB = tripB
        )
        saveState(current.tripA, tripB, odometerKm)
    }

    fun resetForAnonymous() {
        currentVehicleId = -1
        lastLocation = null
        val current = _uiState.value
        _uiState.value = current.copy(
            odometerKm = 0.0,
            tripB = TripData()
        )
        saveState(current.tripA, TripData(), 0.0)
    }

    fun setActiveVehicleId(vehicleId: Int){
        currentVehicleId = vehicleId
    }

    private val burstRunnable = object : Runnable {
        override fun run() {
            if (alertBurstCount < 3) {
                soundPool.play(alertSoundId, 1f, 1f, 0, 0, 1f)
                alertBurstCount++
                alertHandler.postDelayed(this, ALERT_BURST_INTERVAL_MS)
            }
        }
    }

    fun getSessionData(): TripData = TripData(
        distanceKm  = sessionDistanceKm,
        movingTimeMs = sessionMovingTimeMs,
        maxSpeedKmh = sessionMaxSpeedKmh
    )

    private fun triggerOverspeedAlert() {
        if (!prefs.getBoolean("pref_indicator_audible", true)) return // exit function if audio alert is disabled in user settings
        if (!_uiState.value.isRunning) return // Disable audio warnings while not in active session
        val now = System.currentTimeMillis()
        if (now - lastAlertTime < ALERT_REPEAT_MS) return
        lastAlertTime = now
        alertBurstCount = 0
        alertHandler.removeCallbacks(burstRunnable)
        alertHandler.post(burstRunnable)
    }

    private fun triggerUnderspeedAlert() {
        if (!prefs.getBoolean("pref_indicator_audible", true)) return // exit function if audio alert is disabled in user settings
        if (!_uiState.value.isRunning) return // Disable audio warnings while not in active session
        soundPool.play(alertSoundId, 1f, 1f, 0, 0, 1f)
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        serviceScope.cancel()
        alertHandler.removeCallbacksAndMessages(null)
        soundPool.release()
    }
}