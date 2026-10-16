package com.example.incidex

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

fun styleBadge(tv: TextView, label: String, textColor: String, bgColor: String) {
    tv.text = label
    tv.setTextColor(Color.parseColor(textColor))
    tv.background = GradientDrawable().apply {
        cornerRadius = 100f
        setColor(Color.parseColor(bgColor))
    }
}

class UserReportAdapter(
    private var items: List<UserReport>,
    private val onClick: (UserReport) -> Unit
) : RecyclerView.Adapter<UserReportAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.txtIcon)
        val type: TextView = v.findViewById(R.id.txtType)
        val severity: TextView = v.findViewById(R.id.txtSeverity)
        val location: TextView = v.findViewById(R.id.txtLocation)
        val date: TextView = v.findViewById(R.id.txtDate)
        val status: TextView = v.findViewById(R.id.txtStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.user_item_report, parent, false)
    )

    override fun onBindViewHolder(h: VH, position: Int) {
        val r = items[position]
        h.icon.setImageResource(IncidentIcons.iconFor(r.type))
        h.icon.setColorFilter(IncidentIcons.colorFor(r.type))
        h.type.text = r.type
        h.location.text = r.location
        h.location.setStartIcon(R.drawable.ic_location, Color.parseColor("#E53935"), 12, 4)
        h.date.text = r.dateTime
        styleBadge(h.severity, r.severity.label, r.severity.text, r.severity.bg)
        styleBadge(h.status, r.status.label, r.status.text, r.status.bg)
        h.itemView.setOnClickListener { onClick(r) }
    }

    override fun getItemCount() = items.size

    @SuppressLint("NotifyDataSetChanged")
    fun submit(newItems: List<UserReport>) {
        items = newItems
        notifyDataSetChanged()
    }
}