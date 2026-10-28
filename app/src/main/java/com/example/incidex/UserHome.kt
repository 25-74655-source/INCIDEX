package com.example.incidex

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import kotlin.math.abs

/** Turns a submitted report into a row for the Nearby Incidents list. */
private fun UserReport.toIncident() = UserIncident(
    title = type,
    severity = severity.label.lowercase().replaceFirstChar { it.uppercase() },
    status = status.label.lowercase().replaceFirstChar { it.uppercase() },
    meta = "Your report · $location",
    lat = lat,
    lon = lng
)

class UserHome : AppCompatActivity() {

    // The signed-in user's own reports
    private var allIncidents: List<UserIncident> = emptyList()
    private var currentFilter = "All"

    private lateinit var adapter: UserIncidentAdapter
    private lateinit var chips: Map<TextView, String>
    private lateinit var txtEmpty: TextView
    private var mapView: MapView? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.user_home)

        // HEADER
        bindHeader()

        val pinRed = Color.parseColor("#E53935")

        findViewById<TextView>(R.id.txtLocation).apply {
            text = "Nasugbu, Batangas"
            setStartIcon(R.drawable.ic_location, pinRed, 14, 4)
        }

        findViewById<TextView>(R.id.txtCurrentLocation).apply {
            text = "Nasugbu, Batangas"
            setStartIcon(R.drawable.ic_location, pinRed, 16, 6)
        }

        // MAP
        setupMap()

        // REPORT INCIDENT BUTTON
        findViewById<Button>(R.id.btnReportIncident)
            .setOnClickListener {
                startActivity(
                    Intent(this, UserReportIncidentActivity::class.java)
                )
            }

        // VIEW ALL
        findViewById<TextView>(R.id.txtViewAll)
            .setOnClickListener {
                toast("All incidents coming soon")
            }

        // QUICK REPORT
        val quickContainer =
            findViewById<LinearLayout>(R.id.layoutQuickReport)

        quickContainer.setPadding(0, 0, dp(16), 0)

        val categories = listOf(
            "Accident",
            "Traffic",
            "Hazard",
            "Flooding",
            "Closure"
        )

        for (label in categories) {
            val item = LayoutInflater.from(this)
                .inflate(
                    R.layout.user_item_quick_report,
                    quickContainer,
                    false
                )

            item.findViewById<ImageView>(R.id.txtQuickIcon).apply {
                setImageResource(IncidentIcons.iconFor(label))
                setColorFilter(IncidentIcons.colorFor(label))
            }

            item.findViewById<TextView>(R.id.txtQuickLabel).text =
                label

            item.setOnClickListener {
                startActivity(
                    Intent(
                        this,
                        UserReportIncidentActivity::class.java
                    ).putExtra(
                        UserReportIncidentActivity.EXTRA_QUICK_TYPE,
                        label
                    )
                )
            }

            quickContainer.addView(item)
        }

        // HORIZONTAL QUICK REPORT SCROLL
        val quickScroll =
            quickContainer.parent as HorizontalScrollView

        var startX = 0f
        var startY = 0f

        quickScroll.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y

                    view.parent
                        .requestDisallowInterceptTouchEvent(true)
                }

                MotionEvent.ACTION_MOVE -> {
                    val horizontal =
                        abs(event.x - startX) >
                                abs(event.y - startY)

                    view.parent
                        .requestDisallowInterceptTouchEvent(horizontal)
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    view.parent
                        .requestDisallowInterceptTouchEvent(false)
                }
            }

            // Let HorizontalScrollView handle scrolling.
            false
        }

        // INCIDENT LIST
        adapter = UserIncidentAdapter(emptyList())

        val recyclerView =
            findViewById<RecyclerView>(R.id.rvIncidents)

        recyclerView.layoutManager =
            LinearLayoutManager(this)

        recyclerView.adapter = adapter

        // FILTER CHIPS
        txtEmpty = findViewById(R.id.txtEmpty)

        chips = mapOf(
            findViewById<TextView>(R.id.chipAll) to "All",
            findViewById<TextView>(R.id.chipCritical) to "Critical",
            findViewById<TextView>(R.id.chipModerate) to "Moderate",
            findViewById<TextView>(R.id.chipLow) to "Low"
        )

        chips.forEach { (chip, filter) ->
            chip.setOnClickListener { applyFilter(filter) }
        }

        // The list fills in (and refreshes) whenever the reports change
        ReportStore.observe(this) {
            loadIncidents()
            applyFilter(currentFilter)
        }

        // BOTTOM NAVIGATION
        BottomNavHelper.setup(
            this,
            findViewById(R.id.bottomNav),
            R.id.nav_home
        )
    }

    /** Newest reports go first. */
    private fun loadIncidents() {
        allIncidents = ReportStore.all().map { it.toIncident() }
    }

    private fun applyFilter(filter: String) {
        currentFilter = filter

        chips.forEach { (chip, f) -> chip.isSelected = f == filter }

        val filtered =
            if (filter == "All") allIncidents
            else allIncidents.filter { it.severity == filter }

        adapter.submit(filtered)

        txtEmpty.visibility =
            if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupMap() {
        val map = findViewById<MapView>(R.id.mapView)
        mapView = map

        val here = GeoPoint(14.0697, 120.6328)

        map.setTileSource(MapTiles.STREETS)
        map.setMultiTouchControls(true)
        map.controller.setZoom(15.0)
        map.controller.setCenter(here)

        val marker = Marker(map).apply {
            position = here
            title = "You are here"
            icon = mapPin(this@UserHome)   // dark blue pin instead of the green one

            setAnchor(
                Marker.ANCHOR_CENTER,
                Marker.ANCHOR_BOTTOM
            )
        }

        map.overlays.add(marker)

        findViewById<TextView>(R.id.txtMapLabel).text =
            "Nasugbu, Batangas"

        // Allow map panning and zooming inside the page.
        map.setOnTouchListener { view, event ->
            view.parent.requestDisallowInterceptTouchEvent(
                event.action != MotionEvent.ACTION_UP &&
                        event.action != MotionEvent.ACTION_CANCEL
            )

            false
        }
    }

    private fun bindHeader() {
        val name = UserSession.name
        val firstName = name.trim().split(" ").firstOrNull().orEmpty()

        findViewById<TextView>(R.id.txtGreeting).text =
            if (firstName.isEmpty()) "Welcome to Incidex!"
            else "Welcome to Incidex!, $firstName"

        // Photo if the user set one, otherwise their initials
        findViewById<TextView>(R.id.txtAvatar)
            .bindAvatar(name, ProfilePhotos.userPath, R.drawable.user_bg_avatar)
    }

    override fun onResume() {
        super.onResume()

        mapView?.onResume()
        bindHeader()

        BottomNavHelper.highlight(
            findViewById<BottomNavigationView>(R.id.bottomNav),
            R.id.nav_home
        )
    }

    override fun onPause() {
        mapView?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        mapView?.onDetach()
        mapView = null
        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun toast(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT
        ).show()
    }
}