package cvc.dashingdog.vaart

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import cvc.dashingdog.vaart.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import android.Manifest
import android.content.res.ColorStateList.valueOf

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var locationService: LocationService? = null
    private var isBound = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) startAndBindService()
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            locationService = (binder as LocationService.LocalBinder).getService()
            isBound = true
            observeState()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            locationService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding.btnStartStop.setOnClickListener {
            locationService?.let { svc ->
                if (svc.uiState.value.isRunning) svc.stopTrip()
                else svc.startTrip()
            }
        }
        binding.btnResetA.setOnClickListener { locationService?.resetTripA() }
        binding.btnResetB.setOnClickListener { locationService?.resetTripB() }

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startAndBindService()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun startAndBindService() {
        val intent = Intent(this, LocationService::class.java)
        startForegroundService(intent)
        bindService(intent, serviceConnection, 0)
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                locationService?.uiState?.collect { state ->
                    updateUi(state)
                }
            }
        }
    }

    private fun updateUi(state: UiState) {
        // Speed
        binding.tvSpeed.text = if (state.speedKmh == 0 && state.gpsAccuracy == 0f)
            "--" else state.speedKmh.toString()

        // Odometer
        binding.tvOdometer.text = formatOdometer(state.odometerKm)

        // GPS indicator
        val (color, label) = when {
            state.gpsAccuracy == 0f    -> Color.parseColor("#888888") to "No GPS"
            state.gpsAccuracy <= 10f   -> Color.parseColor("#22C55E") to "GPS Good"
            state.gpsAccuracy <= 25f   -> Color.parseColor("#F59E0B") to "GPS Fair"
            else                       -> Color.parseColor("#EF4444") to "GPS Weak"
        }
        binding.vGpsIndicator.backgroundTintList = valueOf(color)
        binding.tvGpsStatus.setTextColor(color)
        binding.tvGpsStatus.text = label

        // START/STOP button
        binding.btnStartStop.text = if (state.isRunning) "STOP" else "START"
        binding.btnStartStop.backgroundTintList = valueOf(
            if (state.isRunning) Color.parseColor("#EF4444")
            else Color.parseColor("#22C55E")
        )

        // Trip A
        binding.tvDistA.text = state.tripA.formattedDistance
        binding.tvTimeA.text = state.tripA.formattedTime
        binding.tvAvgA.text = if (state.tripA.movingTimeMs > 0)
            "${state.tripA.avgSpeedKmh} km/h avg" else "-- km/h avg"
        binding.tvMaxA.text = if (state.tripA.maxSpeedKmh > 0)
            "${state.tripA.maxSpeedKmh} km/h max" else "-- km/h max"

        // Trip B
        binding.tvDistB.text = state.tripB.formattedDistance
        binding.tvTimeB.text = state.tripB.formattedTime
        binding.tvAvgB.text = if (state.tripB.movingTimeMs > 0)
            "${state.tripB.avgSpeedKmh} km/h avg" else "-- km/h avg"
        binding.tvMaxB.text = if (state.tripB.maxSpeedKmh > 0)
            "${state.tripB.maxSpeedKmh} km/h max" else "-- km/h max"
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    override fun onStart() {
        super.onStart()
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && !isBound
        ) {
            startAndBindService()
        }
    }

    private fun formatOdometer(km: Double): String {
        val total = km.toInt().coerceIn(0, 999999)
        val thousands = total / 1000
        val remainder = total % 1000
        return "%03d %03d km".format(thousands, remainder)
    }
}