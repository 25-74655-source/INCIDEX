package com.example.incidex

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

fun adminTimeAgo(millis: Long): String {
    val min = (System.currentTimeMillis() - millis) / 60_000
    return when {
        min < 1 -> "Just now"
        min < 60 -> "$min min ago"
        min < 60 * 24 -> {
            val h = min / 60
            if (h == 1L) "1 hr ago" else "$h hrs ago"
        }
        else -> "${min / (60 * 24)} d ago"
    }
}

class AdminIncidentAdapter(
    private var items: List<UserReport>,
    private val onClick: (UserReport) -> Unit
) : RecyclerView.Adapter<AdminIncidentAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val photo: ImageView = v.findViewById(R.id.imgAdminCardPhoto)
        val icon: ImageView = v.findViewById(R.id.imgAdminCardIcon)
        val title: TextView = v.findViewById(R.id.txtAdminCardTitle)
        val severity: TextView = v.findViewById(R.id.txtAdminCardSeverity)
        val location: TextView = v.findViewById(R.id.txtAdminCardLocation)
        val reporter: TextView = v.findViewById(R.id.txtAdminCardReporter)
        val id: TextView = v.findViewById(R.id.txtAdminCardId)
        val status: TextView = v.findViewById(R.id.txtAdminCardStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context)
            .inflate(R.layout.admin_item_incident_card, parent, false)
    )

    override fun onBindViewHolder(h: VH, position: Int) {
        val r = items[position]

        // Photo if there is one, otherwise the incident icon
        fun showIcon() {
            h.photo.visibility = View.GONE
            h.icon.visibility = View.VISIBLE
            h.icon.setImageResource(IncidentIcons.iconFor(r.type))
            h.icon.setColorFilter(IncidentIcons.colorFor(r.type))
        }

        val url = r.photoUrl
        val bmp = r.photoPath?.let { loadBitmapScaled(it, 256) }
        when {
            !url.isNullOrBlank() -> {
                h.photo.visibility = View.VISIBLE
                h.icon.visibility = View.GONE
                ImageLoader.load(h.photo, url, 256) { ok -> if (!ok) showIcon() }
            }
            bmp != null -> {
                h.photo.tag = null
                h.photo.setImageBitmap(bmp)
                h.photo.visibility = View.VISIBLE
                h.icon.visibility = View.GONE
            }
            r.photoRes != 0 -> {
                h.photo.tag = null
                h.photo.setImageResource(r.photoRes)
                h.photo.visibility = View.VISIBLE
                h.icon.visibility = View.GONE
            }
            else -> {
                h.photo.tag = null
                showIcon()
            }
        }

        h.title.text = r.type
        h.location.text = r.location
        h.reporter.text =
            "${r.reporter.ifBlank { "Anonymous" }} · ${adminTimeAgo(r.createdAt)}"
        h.id.text = r.id

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