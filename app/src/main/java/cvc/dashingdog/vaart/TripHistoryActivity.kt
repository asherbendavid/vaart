package cvc.dashingdog.vaart

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import cvc.dashingdog.vaart.databinding.ActivityTripHistoryBinding
import kotlinx.coroutines.launch

class TripHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTripHistoryBinding
    private lateinit var repository: VehicleRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTripHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Trip History"

        repository = VehicleRepository(this)
        binding.rvTripHistory.layoutManager = LinearLayoutManager(this)

        loadHistory()
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            val records = repository.getAllTripRecords()
            val vehicleNames = repository.getAllVehicles().associate { it.id to it.name }

            if (records.isEmpty()) {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.rvTripHistory.visibility = View.GONE
            } else {
                binding.tvEmpty.visibility = View.GONE
                binding.rvTripHistory.visibility = View.VISIBLE
                binding.rvTripHistory.adapter = TripHistoryAdapter(records, vehicleNames)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}