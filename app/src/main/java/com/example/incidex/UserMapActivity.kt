package com.example.incidex

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class UserMapActivity : AppCompatActivity() {

    private val here = NasugbuBoundary.safeCenter()

    private lateinit var map: MapView
    private val incidentMarkers = mutableListOf<Marker>()

    // The signed-in user's own reports (only ones inside the Nasugbu boundary)
    private var reports: List<UserReport> = emptyList()
    private var severityFilter: Severity? = null   // null = All

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.user_activity_map)

        map = findViewById(R.id.user_map_view)
        map.setTileSource(MapTiles.STREETS)
        map.setMultiTouchControls(true)
        NasugbuBoundary.applyToMap(map)   // zoom + camera limits and the boundary outline
        map.controller.setZoom(15.0)
        map.controller.setCenter(here)

        // "You are here" marker
        map.overlays.add(Marker(map).apply {
            position = here
            title = "You are here"
            icon = dotIcon(Color.parseColor("#2F4BDB"))
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        })

        // FILTER CHIPS (same style as Home)
        val chips = mapOf<TextView, Severity?>(
            findViewById<TextView>(R.id.mapChipAll) to null,
            findViewById<TextView>(R.id.mapChipCritical) to Severity.CRITICAL,
            findViewById<TextView>(R.id.mapChipModerate) to Severity.MODERATE,
            findViewById<TextView>(R.id.mapChipLow) to Severity.LOW
        )

        fun applyFilter(selected: TextView) {
            severityFilter = chips[selected]
            chips.keys.forEach { chip -> chip.isSelected = chip == selected }
            showIncidents()
        }

        chips.keys.forEach { chip ->
            chip.setOnClickListener { applyFilter(chip) }
        }

        findViewById<TextView>(R.id.mapChipAll).isSelected = true

        // Redraw the pins whenever the reports change
        ReportStore.observe(this) {
            reports = ReportStore.all()
            showIncidents()
        }

        // BOTTOM NAVIGATION
        BottomNavHelper.setup(this, findViewById(R.id.user_bottom_nav), R.id.nav_map)
    }

    private fun showIncidents() {
        incidentMarkers.forEach { map.overlays.remove(it) }
        incidentMarkers.clear()

        val list = reports.filter { severityFilter == null || it.severity == severityFilter }

        list.forEach { report ->
            val label = report.severity.label.lowercase().replaceFirstChar { it.uppercase() }
            val marker = Marker(map).apply {
                position = GeoPoint(report.lat, report.lng)
                title = report.type
                snippet = "$label · ${report.location}"
                icon = dotIcon(colorFor(report.severity))
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { _, _ ->
                    Toast.makeText(
                        this@UserMapActivity,
                        "$label · ${report.type}: ${report.location}",
                        Toast.LENGTH_SHORT
                    ).show()
                    true
                }
            }
            incidentMarkers.add(marker)
            map.overlays.add(marker)
        }
        map.invalidate()
    }

    private fun colorFor(severity: Severity) = Color.parseColor(
        when (severity) {
            Severity.CRITICAL -> "#EF4444"
            Severity.MODERATE -> "#F59E0B"
            Severity.LOW -> "#22C55E"
        }
    )

    private fun dotIcon(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(dp(3), Color.WHITE)
        setSize(dp(22), dp(22))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onResume() {
        super.onResume()
        map.onResume()
        BottomNavHelper.highlight(
            findViewById<BottomNavigationView>(R.id.user_bottom_nav),
            R.id.nav_map
        )
    }

    override fun onPause() {
        map.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        map.onDetach()
        super.onDestroy()
    }
}