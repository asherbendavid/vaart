package cvc.dashingdog.vaart

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
                val adapter = TripHistoryAdapter(records.toMutableList(), vehicleNames)
                binding.rvTripHistory.adapter = adapter
                attachSwipeToDelete(adapter)
            }
        }
    }

    private fun attachSwipeToDelete(adapter: TripHistoryAdapter) {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val record = adapter.getItem(position)
                AlertDialog.Builder(this@TripHistoryActivity)
                    .setTitle("Delete record")
                    .setMessage("Delete this record permanently? This cannot be undone.")
                    .setPositiveButton("Delete") { _, _ ->
                        lifecycleScope.launch {
                            repository.deleteTripRecord(record)
                            adapter.removeItem(position)
                            if (adapter.itemCount == 0) {
                                binding.tvEmpty.visibility = View.VISIBLE
                                binding.rvTripHistory.visibility = View.GONE
                            }
                        }
                    }
                    .setNegativeButton("Cancel") { _, _ -> adapter.notifyItemChanged(position) }
                    .setOnCancelListener { adapter.notifyItemChanged(position) }
                    .show()
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(binding.rvTripHistory)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}