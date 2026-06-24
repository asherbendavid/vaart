package cvc.dashingdog.vaart

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import cvc.dashingdog.vaart.databinding.ActivityTripMapBinding
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TripMapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTripMapBinding
    private lateinit var repository: VehicleRepository
    private var mapView: MapView? = null

    companion object {
        const val EXTRA_TRIP_ID = "extra_trip_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTripMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        repository = VehicleRepository(this)

        // OSMDroid requires this before any MapView is created
        Configuration.getInstance().userAgentValue = packageName

        val tripId = intent.getIntExtra(EXTRA_TRIP_ID, -1)
        if (tripId == -1) { finish(); return }

        loadTrip(tripId)
    }

    private fun loadTrip(tripId: Int) {
        lifecycleScope.launch {
            val record = repository.getTripRecordById(tripId) ?: run { finish(); return@launch }
            val points = repository.getPointsForTrip(tripId)
            val vehicles = repository.getAllVehicles().associate { it.id to it.name }

            // Toolbar title
            val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                .format(Date(record.startTime))
            supportActionBar?.title = dateStr

            // Detail section
            bindDetails(record, vehicles)

            // Map
            if (points.isEmpty()) {
                binding.tvNoMap.visibility = View.VISIBLE
            } else {
                setupMap(points)
            }
        }
    }

    private fun bindDetails(record: TripRecord, vehicleNames: Map<Int, String>) {
        val vehicleName = when {
            record.vehicleId == -1 -> "ANONYMOUS"
            vehicleNames.containsKey(record.vehicleId) ->
                vehicleNames[record.vehicleId]?.uppercase() ?: "ANONYMOUS"
            else -> "deleted"
        }
        binding.tvDetailVehicle.text = vehicleName
        if (!vehicleNames.containsKey(record.vehicleId) && record.vehicleId != -1) {
            binding.tvDetailVehicle.setTypeface(null, Typeface.ITALIC)
        }

        binding.tvDetailDistance.text = SpeedUnitFormatter.formatDistance(this, record.distanceKm)

        val startStr = SimpleDateFormat("dd MMM yyyy  HH:mm", Locale.getDefault())
            .format(Date(record.startTime))
        val endStr = SimpleDateFormat("HH:mm", Locale.getDefault())
            .format(Date(record.endTime))
        binding.tvDetailDate.text = "$startStr – $endStr"

        val s = record.movingTimeMs / 1000
        binding.tvDetailDuration.text = "Moving time: %d:%02d:%02d"
            .format(s / 3600, (s % 3600) / 60, s % 60)

        val unit = SpeedUnitFormatter.unitLabel(this)
        val avg = if (record.movingTimeMs > 0)
            (record.distanceKm / (record.movingTimeMs / 3_600_000.0)).toInt() else 0
        binding.tvDetailAvg.text = if (avg > 0)
            "Avg speed: ${SpeedUnitFormatter.formatSpeed(this, avg)} $unit" else "Avg speed: --"
        binding.tvDetailMax.text = if (record.maxSpeedKmh > 0)
            "Max speed: ${SpeedUnitFormatter.formatSpeed(this, record.maxSpeedKmh)} $unit" else "Max speed: --"
    }

    private fun setupMap(points: List<TripPoint>) {
        val map = MapView(this)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        binding.mapContainer.addView(map, 0) // add behind tvNoMap
        mapView = map

        val geoPoints = points.map { GeoPoint(it.latitude, it.longitude) }

        val polyline = Polyline().apply {
            setPoints(geoPoints)
            color = Color.parseColor("#22C55E") // same green as START button
            width = 6f
        }
        map.overlays.add(polyline)

        // Zoom to fit the route
        val box = BoundingBox.fromGeoPoints(geoPoints)
        map.post {
            map.zoomToBoundingBox(box.increaseByScale(1.2f), true)
        }
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}