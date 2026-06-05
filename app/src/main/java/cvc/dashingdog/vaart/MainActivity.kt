package cvc.dashingdog.vaart

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.Looper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import cvc.dashingdog.vaart.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) startLocationUpdates()
        else showPermissionDenied()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { updateDisplay(it) }
            }
        }

        checkPermissionAndStart()
    }

    private fun checkPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startLocationUpdates()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 1000L
        ).setMinUpdateIntervalMillis(500L).build()

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                request, locationCallback, Looper.getMainLooper()
            )
            binding.tvGpsStatus.text = "Acquiring..."
        }
    }

    private fun updateDisplay(location: Location) {
        val speedKmh = if (location.hasSpeed()) {
            (location.speed * 3.6).toInt()
        } else {
            null
        }
        binding.tvSpeed.text = speedKmh?.toString() ?: "--"

        val accuracy = location.accuracy
        val (color, statusText) = when {
            accuracy <= 10f  -> Color.parseColor("#22C55E") to "GPS Good"
            accuracy <= 25f  -> Color.parseColor("#F59E0B") to "GPS Fair"
            else             -> Color.parseColor("#EF4444") to "GPS Weak"
        }
        binding.vGpsIndicator.backgroundTintList = ColorStateList.valueOf(color)
        binding.tvGpsStatus.setTextColor(color)
        binding.tvGpsStatus.text = statusText
    }

    private fun showPermissionDenied() {
        binding.tvSpeed.text = "--"
        binding.tvGpsStatus.text = "Location denied"
        binding.tvGpsStatus.setTextColor(Color.parseColor("#EF4444"))
    }

    override fun onResume() {
        super.onResume()
        checkPermissionAndStart()
    }

    override fun onPause() {
        super.onPause()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}