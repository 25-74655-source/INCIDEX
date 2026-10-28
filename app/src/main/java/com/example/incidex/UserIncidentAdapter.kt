package com.example.incidex

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class UserIncident(
    val title: String,
    val severity: String,   // Critical, Moderate, Low
    val status: String,     // Verified, Submitted
    val meta: String,
    val lat: Double = 0.0,
    val lon: Double = 0.0
)

class UserIncidentAdapter(private var items: List<UserIncident>) :
    RecyclerView.Adapter<UserIncidentAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.txtIncidentIcon)
        val title: TextView = view.findViewById(R.id.txtIncidentTitle)
        val severity: TextView = view.findViewById(R.id.txtSeverity)
        val meta: TextView = view.findViewById(R.id.txtIncidentMeta)
        val status: TextView = view.findViewById(R.id.txtStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.user_item_incident, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]

        holder.title.text = item.title
        holder.meta.text = item.meta

        val (sevBg, sevColor) = when (item.severity) {
            "Critical" -> R.drawable.user_bg_badge_red to "#DC2626"
            "Moderate" -> R.drawable.user_bg_badge_amber to "#D97706"
            else -> R.drawable.user_bg_badge_green to "#16A34A"
        }

        // Icon is dark blue; the box behind it keeps the severity color
        holder.icon.setImageResource(IncidentIcons.iconFor(item.title))
        holder.icon.setColorFilter(IncidentIcons.colorFor(item.title))
        holder.icon.setBackgroundResource(sevBg)

        holder.severity.text = item.severity.uppercase()
        holder.severity.setBackgroundResource(sevBg)
        holder.severity.setTextColor(Color.parseColor(sevColor))

        val (stBg, stColor) = if (item.status == "Verified")
            R.drawable.user_bg_badge_blue to "#2563EB"
        else
            R.drawable.user_bg_badge_amber to "#D97706"
        holder.status.text = item.status.uppercase()
        holder.status.setBackgroundResource(stBg)
        holder.status.setTextColor(Color.parseColor(stColor))
    }

    override fun getItemCount() = items.size

    @SuppressLint("NotifyDataSetChanged")
    fun submit(newItems: List<UserIncident>) {
        items = newItems
        notifyDataSetChanged()
    }
}