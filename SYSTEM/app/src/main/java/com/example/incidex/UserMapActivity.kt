package com.example.incidex

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MotionEvent
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

data class UserMapIncident(
    val id: Int,
    val type: String,       // "Road Accident", "Traffic", "Flooding", ...
    val severity: String,   // "Critical", "Moderate", "Low"
    val title: String,
    val lat: Double,
    val lon: Double
)

class UserMapActivity : AppCompatActivity() {

    // fake data until you have a backend
    private val allIncidents = listOf(
        UserMapIncident(1, "Road Accident", "Critical", "Motorcycle collision", 14.0745, 120.6310),
        UserMapIncident(2, "Road Accident", "Critical", "Two-vehicle crash", 14.0660, 120.6270),
        UserMapIncident(3, "Traffic", "Moderate", "Heavy traffic near the plaza", 14.0770, 120.6400),
        UserMapIncident(4, "Flooding", "Moderate", "Flooded road", 14.0690, 120.6360),
        UserMapIncident(5, "Road Hazard", "Low", "Fallen tree branch", 14.0620, 120.6300),
        UserMapIncident(6, "Road Hazard", "Low", "Small pothole", 14.0725, 120.6450)
    )

    private val here = GeoPoint(14.0697, 120.6328)

    private lateinit var map: MapView
    private val incidentMarkers = mutableListOf<Marker>()

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.user_activity_map)

        map = findViewById(R.id.user_map_view)
        map.setTileSource(MapTiles.STREETS)
        map.setMultiTouchControls(true)
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
        val chips = mapOf(
            findViewById<TextView>(R.id.mapChipAll) to "All",
            findViewById<TextView>(R.id.mapChipCritical) to "Critical",
            findViewById<TextView>(R.id.mapChipModerate) to "Moderate",
            findViewById<TextView>(R.id.mapChipLow) to "Low"
        )

        fun applyFilter(selected: TextView) {
            chips.keys.forEach { chip -> chip.isSelected = chip == selected }
            val filter = chips[selected]
            showIncidents(
                if (filter == "All") allIncidents
                else allIncidents.filter { it.severity == filter }
            )
        }

        chips.keys.forEach { chip ->
            chip.setOnClickListener { applyFilter(chip) }
        }

        applyFilter(findViewById(R.id.mapChipAll))

        // BOTTOM NAVIGATION
        BottomNavHelper.setup(this, findViewById(R.id.user_bottom_nav), R.id.nav_map)
    }

    private fun showIncidents(list: List<UserMapIncident>) {
        incidentMarkers.forEach { map.overlays.remove(it) }
        incidentMarkers.clear()

        list.forEach { incident ->
            val marker = Marker(map).apply {
                position = GeoPoint(incident.lat, incident.lon)
                title = incident.title
                snippet = "${incident.severity} · ${incident.type}"
                icon = dotIcon(colorFor(incident.severity))
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { _, _ ->
                    Toast.makeText(
                        this@UserMapActivity,
                        "${incident.severity} · ${incident.type}: ${incident.title}",
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

    private fun colorFor(severity: String) = when (severity) {
        "Critical" -> Color.parseColor("#EF4444")
        "Moderate" -> Color.parseColor("#F59E0B")
        "Low" -> Color.parseColor("#22C55E")
        else -> Color.GRAY
    }

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