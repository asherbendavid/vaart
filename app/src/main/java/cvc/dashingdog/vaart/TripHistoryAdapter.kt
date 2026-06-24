package cvc.dashingdog.vaart

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import cvc.dashingdog.vaart.databinding.ItemTripBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TripHistoryAdapter(
    private val records: List<TripRecord>,
    private val vehicleNames: Map<Int, String>
) : RecyclerView.Adapter<TripHistoryAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemTripBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTripBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        val holder = ViewHolder(binding)
        binding.root.setOnClickListener {
            val record = records[holder.adapterPosition]
            val intent = android.content.Intent(parent.context, TripMapActivity::class.java)
            intent.putExtra(TripMapActivity.EXTRA_TRIP_ID, record.id)
            parent.context.startActivity(intent)
        }
        return holder
    }

    override fun getItemCount() = records.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = records[position]
        val b = holder.binding
        val context = holder.binding.root.context

        // Vehicle name
        when {
            record.vehicleId == -1 -> {
                b.tvVehicleName.text = "ANONYMOUS"
                b.tvVehicleName.setTypeface(null, Typeface.NORMAL)
            }
            vehicleNames.containsKey(record.vehicleId) -> {
                b.tvVehicleName.text = vehicleNames[record.vehicleId]?.uppercase()
                b.tvVehicleName.setTypeface(null, Typeface.NORMAL)
            }
            else -> {
                b.tvVehicleName.text = "deleted"
                b.tvVehicleName.setTypeface(null, Typeface.ITALIC)
            }
        }

        // Date
        b.tvDate.text = SimpleDateFormat("dd MMM yyyy  HH:mm", Locale.getDefault())
            .format(Date(record.startTime))

        // Distance
        b.tvDistance.text = SpeedUnitFormatter.formatDistance(context, record.distanceKm)

        // Duration
        val s = record.movingTimeMs / 1000
        b.tvDuration.text = "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)

        // Avg speed
        val unit = SpeedUnitFormatter.unitLabel(context)
        val avg = if (record.movingTimeMs > 0)
            (record.distanceKm / (record.movingTimeMs / 3_600_000.0)).toInt()
        else 0
        b.tvAvgSpeed.text = if (avg > 0)
            "${SpeedUnitFormatter.formatSpeed(context, avg)} $unit avg" else "-- avg"

        // Max speed
        b.tvMaxSpeed.text = if (record.maxSpeedKmh > 0)
            "${SpeedUnitFormatter.formatSpeed(context, record.maxSpeedKmh)} $unit max" else "-- max"
    }
}