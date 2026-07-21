package cvc.dashingdog.vaart

import android.content.Intent
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import cvc.dashingdog.vaart.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import android.Manifest
import android.content.res.ColorStateList.valueOf
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.savedstate.serialization.saved
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.BatteryManager
import android.animation.ValueAnimator
import android.graphics.drawable.GradientDrawable
import java.util.Locale
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.suspendCancellableCoroutine

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var locationService: LocationService? = null
    private var isBound = false
    private var isHudMode = false
    private var currentVehicleId : Int = -1 // -1 = anonymous
    private var vehicleList: List<Vehicle> = emptyList()
    private lateinit var repository: VehicleRepository
    private var isVehicleCorrectionInProgress = false
    private var pendingCorrectionTripId: Int = -1
    private var pendingCorrectionSessionData: TripData = TripData()
    private var stateObserverStarted = false
    private var batteryPulseAnimator: ValueAnimator? = null
    private val prefs by lazy {getSharedPreferences(LocationService.PREFS_NAME, MODE_PRIVATE)}
    private val tickReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when(intent?.action){
                Intent.ACTION_TIME_TICK, Intent.ACTION_TIME_CHANGED -> updateClock()
                Intent.ACTION_BATTERY_CHANGED -> intent.let {updateBattery(it)}
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) startAndBindService()
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            locationService = (binder as LocationService.LocalBinder).getService()
            isBound = true
            if (!stateObserverStarted){
                observeState()
            }
            loadActiveVehicle()
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
        applyStatusBarPref()
        applyScreenRotation()
        updateClock()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding.btnStartStop.setOnClickListener {
            locationService?.let { svc ->
                if (svc.uiState.value.isRunning) handleStopWithSummary()
                else svc.startTrip(currentVehicleId)
            }
        }
        binding.btnResetA.setOnClickListener {
            lifecycleScope.launch {
                if (confirmResetTripAIfPinned()) {
                    locationService?.resetTripA()
                }
            }
        }
        binding.btnResetB.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset Trip B")
                .setMessage("This is your since-refuel distance. Reset it anyway?")
                .setPositiveButton("Reset") { _, _ -> locationService?.resetTripB() }
                .setNegativeButton("Cancel", null)
                .show()
        }
        binding.btnMenu.setOnClickListener { showMainMenu() }
        binding.btnHud.setOnClickListener {
            isHudMode = !isHudMode
            applyHudMode()
            applyScreenRotation()
            applySignColorScheme()
        }
        repository = VehicleRepository(this)
        binding.btnVehicleSelector.setOnClickListener { showVehicleMenu() }
        loadVehicleSelector()

        binding.tvOdometer.setOnLongClickListener {
            val state = locationService?.uiState?.value ?: return@setOnLongClickListener true
            if (!state.isRunning) showStandaloneOdoUpdateDialog(state)
            true
        }

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startAndBindService()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }


    private suspend fun confirmResetTripAIfPinned(): Boolean {
        val pinned = locationService?.uiState?.value?.isTripAPinned ?: false
        if (!pinned) return true
        return suspendCancellableCoroutine { cont ->
            AlertDialog.Builder(this)
                .setTitle("Trip A is pinned")
                .setMessage("Reset Trip A anyway?")
                .setPositiveButton("Reset") { _, _ -> cont.resume(true) {} }
                .setNegativeButton("Cancel") { _, _ -> cont.resume(false) {} }
                .setOnCancelListener { cont.resume(false) {} }
                .show()
        }
    }
    private fun showMainMenu() {
        val popup = PopupMenu(this, binding.btnMenu)
        popup.menu.add(0, 1, 0, "Trip history")
        popup.menu.add(0, 2, 1, "Settings")
        val pinItem = popup.menu.add(0, 3, 2, "Pin Trip A")
        val isPinned = locationService?.uiState?.value?.isTripAPinned ?: false
        if (isPinned) {
            pinItem.setIcon(R.drawable.ic_check)
        }

        try {
            val fields = popup.javaClass.declaredFields
            for (field in fields) {
                if (field.name == "mPopup") {
                    field.isAccessible = true
                    val menuPopupHelper = field.get(popup)
                    val classPopupHelper = Class.forName(menuPopupHelper.javaClass.name)
                    val setForceIcons = classPopupHelper.getMethod("setForceShowIcon", Boolean::class.javaPrimitiveType)
                    setForceIcons.invoke(menuPopupHelper, true)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> { startActivity(Intent(this, TripHistoryActivity::class.java)); true }
                2 -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
                3 -> {
                    val newState = !isPinned
                    locationService?.setTripAPinned(newState)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }
    private fun showStandaloneOdoUpdateDialog(state: UiState) {
        val vehicleName = if (currentVehicleId == -1) "Anonymous"
        else vehicleList.find { it.id == currentVehicleId }?.name ?: "Anonymous"

        val input = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(state.odometerKm.toInt().toString())
            selectAll()
            setPadding(48, 24, 48, 24)
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Update Odometer")
            .setMessage("Odometer for $vehicleName (km):")
            .setView(input)
            .setPositiveButton("Update", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    val newOdo = input.text.toString().toDoubleOrNull()
                    if (newOdo == null) {
                        input.error = "Please enter a valid number"
                        return@setOnClickListener
                    }
                    lifecycleScope.launch {
                        locationService?.loadVehicleData(currentVehicleId, newOdo, state.tripB)
                        if (currentVehicleId != -1) {
                            repository.getVehicleById(currentVehicleId)?.let { vehicle ->
                                repository.updateVehicle(
                                    vehicle.copy(
                                        odometerKm = newOdo,
                                        lastUsedAt = System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                    }
                    dialog.dismiss()
                }
        }
        dialog.show()
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

        vehicleList.forEachIndexed { index, vehicle ->
            popup.menu.add(0, vehicle.id, index, vehicle.name).apply {
                isChecked = vehicle.id == currentVehicleId
            }
        }

        // Separator
        popup.menu.add(0, -3, vehicleList.size, "──────────────").isEnabled = false

        // Anonymous
        popup.menu.add(1, -1, vehicleList.size + 1, "Anonymous").apply {
            isChecked = currentVehicleId == -1
        }

        // Separator
        popup.menu.add(1, -4, vehicleList.size + 2, "──────────────").isEnabled = false

        // New vehicle
        popup.menu.add(2, -2, vehicleList.size + 3, "New vehicle...")

        // Manage vehicles
        popup.menu.add(2, -5, vehicleList.size + 4, "Manage vehicles...")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                -5 -> {showManageVehiclesDialog(); true}
                -2 -> {
                    if (currentVehicleId == -1) {
                        val currentOdo = locationService?.uiState?.value?.odometerKm ?: 0.0
                        if (currentOdo > 0.0) {
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
                                .setNegativeButton("No") { _, _ -> showNewVehicleDialog() }
                                .show()
                        } else {
                            showNewVehicleDialog()
                        }
                    } else {
                        showNewVehicleDialog()
                    }
                    true
                }
                -3, -4 -> true
                else -> { handleVehicleSelected(item.itemId); true }
            }
        }
        popup.show()
    }

    private fun showManageVehiclesDialog() {
        if (vehicleList.isEmpty()) {
            Toast.makeText(this, "No vehicles to manage", Toast.LENGTH_SHORT).show()
            return
        }
        val names = vehicleList.map {
            it.name + if (!it.registration.isNullOrEmpty()) " (${it.registration})" else ""
        }.toTypedArray()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Manage vehicles")
            .setItems(names) { _, index -> showVehicleActionsDialog(vehicleList[index]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showVehicleActionsDialog(vehicle: Vehicle) {
        val title = vehicle.name +
                if (!vehicle.registration.isNullOrEmpty()) " · ${vehicle.registration}" else ""

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(arrayOf("Edit details", "Delete vehicle")) { _, which ->
                when (which) {
                    0 -> showEditVehicleDialog(vehicle)
                    1 -> showDeleteVehicleDialog(vehicle)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditVehicleDialog(vehicle: Vehicle) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_new_vehicle, null)
        val etName  = dialogView.findViewById<android.widget.EditText>(R.id.etVehicleName)
        val etReg   = dialogView.findViewById<android.widget.EditText>(R.id.etVehicleReg)
        val etNotes = dialogView.findViewById<android.widget.EditText>(R.id.etVehicleNotes)
        val etOdo   = dialogView.findViewById<android.widget.EditText>(R.id.etVehicleOdo)

        etName.setText(vehicle.name)
        etReg.setText(vehicle.registration ?: "")
        etNotes.setText(vehicle.notes ?: "")

        val displayOdo = if (vehicle.id == currentVehicleId)
            locationService?.uiState?.value?.odometerKm ?: vehicle.odometerKm
        else
            vehicle.odometerKm
        etOdo.setText(displayOdo.toInt().toString())

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Edit Vehicle")
            .setView(dialogView)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    val name = etName.text.toString().trim()
                    if (name.isEmpty()) {
                        etName.error = "Vehicle name is required"
                        etName.requestFocus()
                        return@setOnClickListener
                    }
                    val reg   = etReg.text.toString().trim().ifEmpty { null }
                    val notes = etNotes.text.toString().trim().ifEmpty { null }
                    val newOdo = etOdo.text.toString().toDoubleOrNull() ?: displayOdo

                    lifecycleScope.launch {
                        repository.updateVehicle(
                            vehicle.copy(
                                name = name,
                                registration = reg,
                                notes = notes,
                                odometerKm = newOdo,
                                lastUsedAt = System.currentTimeMillis()
                            )
                        )
                        if (vehicle.id == currentVehicleId) {
                            locationService?.uiState?.value?.let { state ->
                                locationService?.loadVehicleData(vehicle.id, newOdo, state.tripB)
                            }
                        }
                        loadVehicleSelector() // refreshes vehicleList and updates button label
                    }
                    dialog.dismiss()
                }
        }
        dialog.show()
    }

    private fun showDeleteVehicleDialog(vehicle: Vehicle) {
        val message = if (vehicle.id == currentVehicleId)
            "${vehicle.name} is currently active. Deleting it will switch to Anonymous mode."
        else
            "All data for ${vehicle.name} will be permanently deleted."

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete ${vehicle.name}?")
            .setMessage(message)
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch { performDelete(vehicle) }
            }
            .setNeutralButton("Export & Delete") { _, _ ->
                shareVehicleData(vehicle)
                lifecycleScope.launch { performDelete(vehicle) }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun shareVehicleData(vehicle: Vehicle) {
        val tripB = TripData(
            distanceKm    = vehicle.tripBDistanceKm,
            movingTimeMs  = vehicle.tripBMovingTimeMs,
            maxSpeedKmh   = vehicle.tripBMaxSpeedKmh
        )
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())

        val text = buildString {
            appendLine("Vaart Vehicle Export — $date")
            appendLine()
            appendLine("Name: ${vehicle.name}")
            vehicle.registration?.let { appendLine("Registration: $it") }
            vehicle.notes?.let      { appendLine("Notes: $it") }
            appendLine()
            appendLine("Odometer: ${vehicle.odometerKm.toInt()} km")
            appendLine()
            appendLine("Trip B:")
            appendLine("  Distance:  ${tripB.formattedDistance}")
            appendLine("  Time:      ${tripB.formattedTime}")
            appendLine("  Avg speed: ${if (tripB.movingTimeMs > 0) "${tripB.avgSpeedKmh} km/h" else "--"}")
            appendLine("  Max speed: ${if (tripB.maxSpeedKmh > 0) "${tripB.maxSpeedKmh} km/h" else "--"}")
        }

        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Vaart — ${vehicle.name}")
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                "Export vehicle data"
            )
        )
    }

    private suspend fun performDelete(vehicle: Vehicle) {
        repository.deleteVehicle(vehicle)
        if (vehicle.id == currentVehicleId) {
            locationService?.resetForAnonymous()
            currentVehicleId = -1
            saveActiveVehicleId()
        }
        loadVehicleSelector()
    }

    private fun handleVehicleSelected(vehicleId: Int) {
        if (vehicleId == currentVehicleId) return
        if (vehicleId == -1) {
            // switching to anonymous
            lifecycleScope.launch {
                saveCurrentVehicleData()
                if (!isVehicleCorrectionInProgress) {
                    if (confirmResetTripAIfPinned()) locationService?.resetTripA()
                }
                isVehicleCorrectionInProgress = false
                locationService?.resetForAnonymous()
                currentVehicleId = -1
                saveActiveVehicleId()
                updateVehicleSelectorButton()
                loadVehicleSelector()
            }
        } else {
            // switching to real vehicle — show odo check first
            lifecycleScope.launch {
                val newVehicle = repository.getVehicleById(vehicleId) ?: return@launch
                if (isVehicleCorrectionInProgress) {
                    performVehicleCorrection(newVehicle)
                } else {
                    showOdoCheckPrompt(newVehicle) { confirmedVehicle ->
                        lifecycleScope.launch {
                            saveCurrentVehicleData()
                            if (!isVehicleCorrectionInProgress) {
                                if (confirmResetTripAIfPinned()) locationService?.resetTripA()
                            }
                            isVehicleCorrectionInProgress = false
                            locationService?.loadVehicleData(
                                newVehicle.id,
                                confirmedVehicle.odometerKm,
                                TripData(
                                    distanceKm = newVehicle.tripBDistanceKm,
                                    movingTimeMs = newVehicle.tripBMovingTimeMs,
                                    maxSpeedKmh = newVehicle.tripBMaxSpeedKmh
                                ),
                                newVehicle.tripBUnreliable,
                            )
                            currentVehicleId = vehicleId
                            saveActiveVehicleId()
                            updateVehicleSelectorButton()
                            loadVehicleSelector()
                        }
                    }
                }
            }
        }
    }

    private suspend fun performVehicleCorrection(newVehicle: Vehicle) {
        val tripId = pendingCorrectionTripId
        val sessionData = pendingCorrectionSessionData
        val oldVehicleId = currentVehicleId

        val record = repository.getTripRecordById(tripId)
        if (record == null) {
            isVehicleCorrectionInProgress = false
            return
        }

        // Reassign the session's own trip record
        repository.updateTripRecord(record.copy(vehicleId = newVehicle.id))

        // Reassign any Trip A/B reset events that happened during this session
        val resetsInRange = repository.getResetRecordsInRange(oldVehicleId, record.startTime, record.endTime)
        resetsInRange.forEach { reset ->
            repository.updateTripRecord(reset.copy(vehicleId = newVehicle.id))
        }

        // Log the correction itself for a clear audit trail
        val oldVehicleName = vehicleList.find { it.id == oldVehicleId }?.name ?: "Anonymous"
        repository.insertTripRecord(
            TripRecord(
                vehicleId = newVehicle.id,
                startTime = record.startTime,
                endTime = record.endTime,
                distanceKm = 0.0,
                movingTimeMs = 0L,
                maxSpeedKmh = 0,
                notes = "Reassigned from $oldVehicleName",
                type = TripRecord.TYPE_VEHICLE_REASSIGNED
            )
        )

        // Odometer — exact transfer
        var updatedNewVehicle = newVehicle
        if (oldVehicleId != -1) {
            repository.getVehicleById(oldVehicleId)?.let { oldVehicle ->
                repository.updateVehicle(oldVehicle.copy(odometerKm = oldVehicle.odometerKm - sessionData.distanceKm))
            }
        }
        updatedNewVehicle = updatedNewVehicle.copy(odometerKm = updatedNewVehicle.odometerKm + sessionData.distanceKm)

        // Trip B — reset both or flag both as unreliable, per setting
        val resetBothOnReassign = prefs.getBoolean("pref_reset_tripb_on_reassign", true)
        if (resetBothOnReassign) {
            if (oldVehicleId != -1) {
                repository.getVehicleById(oldVehicleId)?.let { repository.saveTripB(it, TripData()) }
            }
            repository.saveTripB(updatedNewVehicle, TripData())
        } else {
            if (oldVehicleId != -1) {
                repository.getVehicleById(oldVehicleId)?.let {
                    repository.updateVehicle(it.copy(tripBUnreliable = true))
                }
            }
            updatedNewVehicle = updatedNewVehicle.copy(tripBUnreliable = true)
            repository.updateVehicle(updatedNewVehicle)
        }

        // Finalize the switch, same as a normal vehicle change
        val finalVehicle = repository.getVehicleById(newVehicle.id) ?: updatedNewVehicle
        isVehicleCorrectionInProgress = false
        locationService?.loadVehicleData(
            finalVehicle.id,
            finalVehicle.odometerKm,
            TripData(
                distanceKm = finalVehicle.tripBDistanceKm,
                movingTimeMs = finalVehicle.tripBMovingTimeMs,
                maxSpeedKmh = finalVehicle.tripBMaxSpeedKmh
            ),
            finalVehicle.tripBUnreliable
        )
        currentVehicleId = finalVehicle.id
        saveActiveVehicleId()
        updateVehicleSelectorButton()
        loadVehicleSelector()

        pendingCorrectionTripId = -1
        pendingCorrectionSessionData = TripData()
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

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("New Vehicle")
            .setView(dialogView)
            .setPositiveButton("Save", null) // null listener here - we override below
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    val name = etName.text.toString().trim()
                    val reg = etReg.text.toString().trim().ifEmpty { null }
                    val notes = etNotes.text.toString().trim().ifEmpty { null }
                    val odo = etOdo.text.toString().toDoubleOrNull() ?: 0.0

                    if (name.isEmpty()) {
                        etName.error = "Vehicle name is required"
                        etName.requestFocus()
                        return@setOnClickListener // dialog stays open
                    }

                    lifecycleScope.launch {
                        saveCurrentVehicleData() // saves old vehicle before switching; no-op if anonymous
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
                        saveActiveVehicleId()
                        locationService?.loadVehicleData(
                            newId.toInt(),
                            odo,
                            TripData(
                                distanceKm = newVehicle.tripBDistanceKm,
                                movingTimeMs = newVehicle.tripBMovingTimeMs,
                                maxSpeedKmh = newVehicle.tripBMaxSpeedKmh
                            )
                        )
                        loadVehicleSelector()
                    }
                    dialog.dismiss()
                }
        }

        dialog.show()

    }

    private fun loadActiveVehicle() {
        //val prefs = getSharedPreferences(LocationService.PREFS_NAME, MODE_PRIVATE)
        val savedId = prefs.getInt(LocationService.KEY_ACTIVE_VEHICLE_ID, -1)
        if (savedId == -1) {
            currentVehicleId = -1
            updateVehicleSelectorButton()
            return
        }
        lifecycleScope.launch {
            val vehicle = repository.getVehicleById(savedId)
            if (vehicle != null) {
                currentVehicleId = savedId
                locationService?.setActiveVehicleId(savedId)
                updateVehicleSelectorButton()
            } else {
                // vehicle was deleted externally, fall back to anonymous
                currentVehicleId = -1
                saveActiveVehicleId()
                updateVehicleSelectorButton()
            }
        }
    }

    private fun saveActiveVehicleId() {
        getSharedPreferences(LocationService.PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putInt(LocationService.KEY_ACTIVE_VEHICLE_ID, currentVehicleId)
            .apply()
    }

    private suspend fun saveCurrentVehicleData() {
        if (currentVehicleId == -1) return
        val state = locationService?.uiState?.value ?: return
        val vehicle = repository.getVehicleById(currentVehicleId) ?: return
        repository.updateVehicle(
            vehicle.copy(
                odometerKm = state.odometerKm,
                tripBDistanceKm = state.tripB.distanceKm,
                tripBMovingTimeMs = state.tripB.movingTimeMs,
                tripBMaxSpeedKmh = state.tripB.maxSpeedKmh,
                lastUsedAt = System.currentTimeMillis()
            )
        )
    }

    private fun showOdoCheckPrompt(vehicle: Vehicle, onConfirmed: (Vehicle) -> Unit) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(vehicle.name)
            .setMessage(
                "Is the odometer value of ${vehicle.odometerKm.toInt()} km still correct?"
            )
            .setPositiveButton("Yes") { _, _ -> onConfirmed(vehicle) }
            .setNegativeButton("No") { _, _ ->
                showOdoUpdateDialog(vehicle) { updatedVehicle ->
                    lifecycleScope.launch {
                        repository.updateVehicle(updatedVehicle)
                        onConfirmed(updatedVehicle)
                    }
                }
            }
            .show()
    }

    private fun showOdoUpdateDialog(vehicle: Vehicle, onUpdated: (Vehicle) -> Unit) {
        val input = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(vehicle.odometerKm.toInt().toString())
            selectAll()
            setPadding(48, 24, 48, 24)
        }
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Update Odometer")
            .setMessage("Enter current odometer reading for ${vehicle.name} (km):")
            .setView(input)
            .setPositiveButton("Update", null)
            .setNegativeButton("Cancel") { _, _ -> onUpdated(vehicle) }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    val newOdo = input.text.toString().toDoubleOrNull()
                    if (newOdo == null) {
                        input.error = "Please enter a valid number"
                        return@setOnClickListener
                    }
                    onUpdated(vehicle.copy(odometerKm = newOdo))
                    dialog.dismiss()
                }
        }
        dialog.show()
    }

    private fun handleStopWithSummary() {
        val svc = locationService ?: return
        val thisSessionData = svc.getSessionData()
        val vehicle = vehicleList.find { it.id == currentVehicleId }
        val vehicleName = vehicle?.name ?: "Anonymous"
        val vehicleReg = vehicle?.registration
        val tripId = svc.stopTrip()
        showSessionSummaryDialog(thisSessionData, vehicleName, vehicleReg, tripId)
    }

    private fun showSessionSummaryDialog(tripA: TripData, vehicleName: String, vehicleReg: String?, tripId: Int) {
        val regLine = if (!vehicleReg.isNullOrEmpty()) "\nRegistration: $vehicleReg" else ""
        val unit = speedUnitLabel()
        val message = buildString {
            append("Vehicle: $vehicleName$regLine\n\n")
            append("Distance:  ${formatDistance(tripA.distanceKm)}\n")
            append("Time:      ${tripA.formattedTime}\n")
            append("Avg speed: ${if (tripA.movingTimeMs > 0) "${formatSpeed(tripA.avgSpeedKmh)} $unit" else "--"}\n")
            append("Max speed: ${if (tripA.maxSpeedKmh > 0) "${formatSpeed(tripA.maxSpeedKmh)} $unit" else "--"}")
        }

        val changeEnabled = when {
            vehicleList.isEmpty() -> false
            vehicleList.size == 1 && vehicleList[0].id == currentVehicleId -> false
            else -> true
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Session Complete")
            .setMessage(message)
            .setPositiveButton("Done", null)
            .setNeutralButton("Change vehicle", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).apply {
                isEnabled = changeEnabled
                setOnClickListener {
                    pendingCorrectionTripId = tripId
                    pendingCorrectionSessionData = tripA
                    dialog.dismiss()
                    showVehicleChangePicker()
                }
            }
        }

        dialog.show()
    }

    private fun showVehicleChangePicker() {
        val names = vehicleList.map { it.name }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Change vehicle")
            .setItems(names) { _, index ->
                isVehicleCorrectionInProgress = true
                handleVehicleSelected(vehicleList[index].id)
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
            "--" else formatSpeed(state.speedKmh)

        binding.tvUnit.text = speedUnitLabel()

        // Overspeed / Underspeed color additions
        binding.tvSpeed.setTextColor(
            when {
                state.isOverspeed -> android.graphics.Color.parseColor("#FF4444")
                state.isUnderspeed -> android.graphics.Color.parseColor("#F59E0B")
                else -> android.graphics.Color.WHITE
            }
        )

        val lineEnabled = prefs.getBoolean("pref_indicator_line", true)
        val opacityEnabled = prefs.getBoolean("pref_indicator_opacity", true)
        val baseAlpha = prefs.getInt("pref_sign_base_alpha", 30) / 100f

        binding.vMaxSpeedLine.visibility = if (lineEnabled && state.isOverspeed) View.VISIBLE else View.INVISIBLE
        binding.vMinSpeedLine.visibility = if (lineEnabled && state.isUnderspeed) View.VISIBLE else View.INVISIBLE

        binding.tvMaxSpeedSign.alpha = if (opacityEnabled && state.isOverspeed) 1f else baseAlpha
        binding.tvMinSpeedSign.alpha = if (opacityEnabled && state.isUnderspeed) 1f else baseAlpha

        binding.tvMaxSpeedSign.text = state.maxSpeedLimitKmh?.let { formatSpeed(it) } ?: "--"
        binding.tvMinSpeedSign.text = state.minSpeedLimitKmh?.let { formatSpeed(it) } ?: ""
        binding.tvMinSpeedSign.visibility = if (state.minSpeedLimitKmh != null) View.VISIBLE else View.INVISIBLE

        // Odometer
        binding.tvOdometer.text = SpeedUnitFormatter.formatOdometer(this,state.odometerKm)

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
            if (state.isRunning) Color.parseColor("#450E0E")
            else Color.parseColor("#0E4520")
        )

        // Trip A
        binding.tvDistA.text = formatDistance(state.tripA.distanceKm)
        binding.tvTimeA.text = state.tripA.formattedTime
        binding.tvAvgA.text = if (state.tripA.movingTimeMs > 0)
            "${formatSpeed(state.tripA.avgSpeedKmh)} ${speedUnitLabel()} avg" else "-- ${speedUnitLabel()} avg"
        binding.tvMaxA.text = if (state.tripA.maxSpeedKmh > 0)
            "${formatSpeed(state.tripA.maxSpeedKmh)} ${speedUnitLabel()} max" else "-- ${speedUnitLabel()} max"
        binding.imgPinA.visibility = if (state.isTripAPinned) View.VISIBLE else View.GONE

        // Trip B
        binding.tvDistB.text = formatDistance(state.tripB.distanceKm)
        binding.tvTimeB.text = state.tripB.formattedTime
        binding.tvAvgB.text = if (state.tripB.movingTimeMs > 0)
            "${formatSpeed(state.tripB.avgSpeedKmh)} ${speedUnitLabel()} avg" else "-- ${speedUnitLabel()} avg"
        binding.tvMaxB.text = if (state.tripB.maxSpeedKmh > 0)
            "${formatSpeed(state.tripB.maxSpeedKmh)} ${speedUnitLabel()} max" else "-- ${speedUnitLabel()} max"
        binding.tvTripBUnreliable.visibility = if (state.isTripBUnreliable) View.VISIBLE else View.GONE

        binding.btnVehicleSelector.isEnabled = !state.isRunning
        binding.btnVehicleSelector.setTextColor(
            if (state.isRunning) Color.parseColor("#444444")
            else Color.parseColor("#888888")
        )

        // Debug panel
        binding.tvDebugPanel.text = DebugPanelRenderer.render(state.debugInfo)
    }

    private fun updateClock() {
        val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        binding.tvClock.text = fmt.format(java.util.Date())
    }

    private fun updateBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        if (level == -1 || scale == -1) return

        val pct = (level * 100) / scale
        val showPct = prefs.getBoolean("pref_show_battery_pct",true)
        binding.tvBatteryPct.visibility = if(showPct) View.VISIBLE else View.GONE
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        binding.tvBatteryPct.text = "$pct%"

        val segments = listOf(binding.segBattery1, binding.segBattery2, binding.segBattery3)

        val (litCount, color) = when {
            isCharging -> Pair(((pct + 32) / 33).coerceIn(1, 3), Color.parseColor("#3B82F6"))
            pct > 75 -> Pair(3, Color.parseColor("#22C55E"))
            pct > 50 -> Pair(2, Color.parseColor("#F59E0B"))
            pct > 25 -> Pair(1, Color.parseColor("#EF4444"))
            else -> Pair(0, Color.parseColor("#EF4444"))
        }

        segments.forEachIndexed { index, seg ->
            seg.backgroundTintList = valueOf(
                if (index < litCount) color else Color.parseColor("#333333")
            )
        }

        binding.tvBatteryPct.setTextColor(
            if (pct <= 25 && !isCharging) Color.WHITE else Color.parseColor("#888888")
        )

        if (pct <= 25 && !isCharging) startBatteryPulse() else stopBatteryPulse()
    }

    private fun startBatteryPulse() {
        if (batteryPulseAnimator?.isRunning == true) return
        val shellBg = (binding.batteryShell.background.mutate() as GradientDrawable)
        binding.batteryShell.background = shellBg
        val segments = listOf(binding.segBattery1, binding.segBattery2, binding.segBattery3)

        batteryPulseAnimator = ValueAnimator.ofInt(20, 220).apply {
            duration = 700
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { anim ->
                val alpha = anim.animatedValue as Int
                val flashColor = Color.argb(alpha, 0xEF, 0x44, 0x44)
                shellBg.setColor(flashColor)
                segments.forEach { it.backgroundTintList = valueOf(flashColor) }
            }
            start()
        }
    }

    private fun stopBatteryPulse() {
        batteryPulseAnimator?.cancel()
        batteryPulseAnimator = null
        (binding.batteryShell.background as? GradientDrawable)?.setColor(Color.TRANSPARENT)
    }

    override fun onResume() {
        super.onResume()
        loadVehicleSelector()
        registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let {updateBattery(it)}
        applyStatusBarPref()
        applyHudMode()
        applyScreenRotation()
        applySignColorScheme()
        locationService?.reloadAudioSettings()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyStatusBarPref()
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(tickReceiver)
        stopBatteryPulse()
        val isRunning = locationService?.uiState?.value?.isRunning ?: false
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        if (!isRunning) {
            stopService(Intent(this, LocationService::class.java))
        }
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(tickReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        })
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && !isBound
        ) {
            startAndBindService()
        }
    }

    private fun applySignColorScheme() {
        val scheme = SignColorSchemes.byKey(prefs.getString("pref_sign_color_scheme", "international"))
        val strokeWidthPx = (6 * resources.displayMetrics.density).toInt() // matches existing 6dp

        binding.tvMaxSpeedSign.background = scheme.buildMaxDrawable(strokeWidthPx)
        binding.tvMaxSpeedSign.setTextColor(scheme.maxTextColor)

        binding.tvMinSpeedSign.background = scheme.buildMinDrawable(strokeWidthPx)
        binding.tvMinSpeedSign.setTextColor(scheme.minTextColor)
    }

    private fun applyHudMode() {
        binding.root.scaleX = if (isHudMode) -1f else 1f
        binding.btnResetA.isEnabled = !isHudMode
        binding.btnResetB.isEnabled = !isHudMode
        //binding.btnHud.textColor  // optional visual feedback
        binding.btnMenu.isEnabled = !isHudMode
        binding.btnHud.setTextColor(
            if (isHudMode) Color.parseColor("#F59E0B")
            else Color.parseColor("#888888")
        )
    }

    private fun applyScreenRotation() {
        val rotatePrefKey = if (isHudMode) "pref_rotate_hud" else "pref_rotate_normal"
        val shouldRotate = prefs.getBoolean(rotatePrefKey, false)
        requestedOrientation = if (shouldRotate)
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        else
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

    private fun applyStatusBarPref() {
        val hide = prefs.getBoolean("pref_hide_status_bar", false)
        val controller = WindowCompat.getInsetsController(window, binding.root)

        window.statusBarColor = Color.BLACK
        controller.isAppearanceLightStatusBars = false // light-colored icons, for a dark bg

        if (hide) {
            controller.hide(WindowInsetsCompat.Type.statusBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    private fun formatSpeed(kmh: Int): String = SpeedUnitFormatter.formatSpeed(this, kmh)
    private fun formatDistance(km: Double): String = SpeedUnitFormatter.formatDistance(this, km)
    private fun speedUnitLabel(): String = SpeedUnitFormatter.unitLabel(this)
}