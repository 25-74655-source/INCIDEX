package com.example.incidex

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class AdminHome : AppCompatActivity() {
    private val typeColors = listOf(
        "Accident" to "#EF4444",
        "Traffic" to "#F59E0B",
        "Hazard" to "#2563EB",
        "Flooding" to "#64748B",
        "Closure" to "#111827",
        "Other" to "#22C55E"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.admin_home)

        val today = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date())
        findViewById<TextView>(R.id.txtAdminDate).text = "Nasugbu, Batangas · $today"

        // VIEW ALL -> Incidents screen
        findViewById<View>(R.id.txtAdminViewAll).setOnClickListener {
            startActivity(
                Intent(this, AdminIncidents::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
            finish()
        }

        // Redraw the dashboard whenever reports change
        ReportStore.observe(this) { bindDashboard() }

        // BOTTOM NAVIGATION
        AdminBottomNav.setup(
            this,
            findViewById<BottomNavigationView>(R.id.adminBottomNav),
            R.id.admin_nav_dashboard
        )
    }

    override fun onResume() {
        super.onResume()
        bindHeader()
    }

    private fun bindHeader() {
        val name = AdminSession.name.ifBlank { "Admin" }
        findViewById<TextView>(R.id.txtAdminAvatar)
            .bindAvatar(name, ProfilePhotos.adminPath, R.drawable.admin_bg_avatar)
    }

    private fun bindDashboard() {
        val reports = ReportStore.all().sortedByDescending { it.createdAt }

        // STATS
        findViewById<TextView>(R.id.txtAdminTotal).text = reports.size.toString()
        findViewById<TextView>(R.id.txtAdminPending).text =
            reports.count { it.status == Status.SUBMITTED }.toString()
        findViewById<TextView>(R.id.txtAdminVerified).text =
            reports.count { it.status == Status.VERIFIED }.toString()
        findViewById<TextView>(R.id.txtAdminResolved).text =
            reports.count { it.status == Status.RESOLVED }.toString()

        bindTypeBars(reports)
        bindRecent(reports)
    }

    private fun bucketOf(type: String): String =
        REPORT_TYPES.firstOrNull { it.name == type }?.quickLabel ?: "Other"

    private fun bindTypeBars(reports: List<UserReport>) {
        val container = findViewById<LinearLayout>(R.id.layoutAdminTypeBars)
        container.removeAllViews()

        val counts = reports.groupingBy { bucketOf(it.type) }.eachCount()
        val total = reports.size
        val density = resources.displayMetrics.density

        typeColors.forEach { (label, color) ->
            val pct = if (total == 0) 0
            else ((counts[label] ?: 0) * 100.0 / total).roundToInt()

            val row = layoutInflater.inflate(R.layout.admin_item_type_bar, container, false)
            row.findViewById<TextView>(R.id.txtAdminBarLabel).text = label
            row.findViewById<TextView>(R.id.txtAdminBarPercent).text = "$pct%"

            val fill = row.findViewById<View>(R.id.adminBarFill)
            (fill.layoutParams as LinearLayout.LayoutParams).weight = pct.toFloat()
            fill.background = GradientDrawable().apply {
                cornerRadius = 3f * density
                setColor(Color.parseColor(color))
            }
            container.addView(row)
        }
    }

    private fun bindRecent(reports: List<UserReport>) {
        val container = findViewById<LinearLayout>(R.id.layoutAdminRecent)
        container.removeAllViews()

        reports.take(3).forEach { r ->
            val item = layoutInflater.inflate(R.layout.admin_item_incident, container, false)

            item.findViewById<ImageView>(R.id.imgAdminIncidentIcon).apply {
                setImageResource(IncidentIcons.iconFor(r.type))
                setColorFilter(IncidentIcons.colorFor(r.type))
            }
            item.findViewById<TextView>(R.id.txtAdminIncidentTitle).text = r.type
            item.findViewById<TextView>(R.id.txtAdminIncidentMeta).text =
                "${r.location} · ${adminTimeAgo(r.createdAt)}"

            styleBadge(
                item.findViewById(R.id.txtAdminSeverity),
                r.severity.label, r.severity.text, r.severity.bg
            )
            styleBadge(
                item.findViewById(R.id.txtAdminStatus),
                r.status.label, r.status.text, r.status.bg
            )
            container.addView(item)
        }
    }
}