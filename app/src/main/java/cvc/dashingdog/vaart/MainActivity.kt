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
import android.widget.Toast

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var locationService: LocationService? = null
    private var isBound = false
    private var isHudMode = false
    private var currentVehicleId : Int = -1 // -1 = anonymous
    private var vehicleList: List<Vehicle> = emptyList()
    private lateinit var repository: VehicleRepository

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
        binding.btnHud.setOnClickListener {
            isHudMode = !isHudMode
            applyHudMode()
        }
        repository = VehicleRepository(this)
        binding.btnVehicleSelector.setOnClickListener { showVehicleMenu() }
        loadVehicleSelector()

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startAndBindService()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun loadVehicleSelector() {
        lifecycleScope.launch {
            vehicleList = repository.getAllVehicles()
            updateVehicleSelectorButton()
        }
    }

    private fun updateVehicleSelectorButton() {
        val label = if (currentVehicleId == -1) {
            "Anonymous"
        } else {
            vehicleList.find { it.id == currentVehicleId }?.name ?: "Anonymous"
        }
        binding.btnVehicleSelector.text = label
    }

    private fun showVehicleMenu() {
        val popup = androidx.appcompat.widget.PopupMenu(this, binding.btnVehicleSelector)

        // Vehicles
        vehicleList.forEachIndexed { index, vehicle ->
            popup.menu.add(0, vehicle.id, index, vehicle.name).apply {
                isChecked = vehicle.id == currentVehicleId
            }
        }

        // Separator + Anonymous
        popup.menu.add(1, -1, vehicleList.size, "Anonymous").apply {
            isChecked = currentVehicleId == -1
        }

        // Separator + New vehicle
        popup.menu.add(2, -2, vehicleList.size + 1, "New vehicle...")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                -2 -> {
                    if (currentVehicleId == -1) {
                        val currentOdo = locationService?.uiState?.value?.odometerKm ?: 0.0
                        val hasData = currentOdo > 0.0
                        if (hasData) {
                            androidx.appcompat.app.AlertDialog.Builder(this)
                                .setTitle("Anonymous Data")
                                .setMessage(
                                    "You have ${String.format("%.1f", currentOdo)} km on the anonymous odometer. Apply this data to the new vehicle?"
                                )
                                .setPositiveButton("Yes") { _, _ ->
                                    showNewVehicleDialog(
                                        prefilledOdo = currentOdo,
                                        prefilledFromAnonymous = true
                                    )
                                }
                                .setNegativeButton("No") { _, _ ->
                                    showNewVehicleDialog()
                                }
                                .show()
                        } else {
                            showNewVehicleDialog()
                        }
                    } else {
                        showNewVehicleDialog()
                    }
                    true
                }
                else -> { handleVehicleSelected(item.itemId); true }
            }
        }
        popup.show()
    }

    private fun handleVehicleSelected(vehicleId: Int) {
        if (vehicleId == currentVehicleId) return
        currentVehicleId = vehicleId
        updateVehicleSelectorButton()
        // Full switching logic comes in step 4
    }

    private fun showNewVehicleDialog(prefilledOdo: Double = 0.0, prefilledFromAnonymous: Boolean = false) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_new_vehicle, null)

        val etName = dialogView.findViewById<android.widget.EditText>(R.id.etVehicleName)
        val etReg = dialogView.findViewById<android.widget.EditText>(R.id.etVehicleReg)
        val etNotes = dialogView.findViewById<android.widget.EditText>(R.id.etVehicleNotes)
        val etOdo = dialogView.findViewById<android.widget.EditText>(R.id.etVehicleOdo)

        if (prefilledFromAnonymous) {
            etOdo.setText(prefilledOdo.toInt().toString())
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("New Vehicle")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = etName.text.toString().trim()
                val reg = etReg.text.toString().trim().ifEmpty { null }
                val notes = etNotes.text.toString().trim().ifEmpty { null }
                val odo = etOdo.text.toString().toDoubleOrNull() ?: 0.0

                if (name.isEmpty()) {
                    android.widget.Toast.makeText(
                        this, "Vehicle name is required", Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    val newVehicle = Vehicle(
                        name = name,
                        registration = reg,
                        notes = notes,
                        odometerKm = odo,
                        tripBDistanceKm = if (prefilledFromAnonymous)
                            locationService?.uiState?.value?.tripB?.distanceKm ?: 0.0 else 0.0,
                        tripBMovingTimeMs = if (prefilledFromAnonymous)
                            locationService?.uiState?.value?.tripB?.movingTimeMs ?: 0L else 0L,
                        tripBMaxSpeedKmh = if (prefilledFromAnonymous)
                            locationService?.uiState?.value?.tripB?.maxSpeedKmh ?: 0 else 0
                    )
                    val newId = repository.saveVehicle(newVehicle)
                    currentVehicleId = newId.toInt()
                    loadVehicleSelector()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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

        binding.btnVehicleSelector.isEnabled = !state.isRunning
        binding.btnVehicleSelector.setTextColor(
            if (state.isRunning) Color.parseColor("#444444")
            else Color.parseColor("#888888")
        )
    }

    override fun onResume() {
        super.onResume()
        loadVehicleSelector()
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

    private fun applyHudMode() {
        binding.root.scaleX = if (isHudMode) -1f else 1f
        binding.btnResetA.isEnabled = !isHudMode
        binding.btnResetB.isEnabled = !isHudMode
        //binding.btnHud.textColor  // optional visual feedback
        binding.btnHud.setTextColor(
            if (isHudMode) Color.parseColor("#F59E0B")
            else Color.parseColor("#888888")
        )
    }
}