package com.example.incidex

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

class AdminMap : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var card: View
    private lateinit var chips: Map<TextView, Status?>

    private val markers = mutableListOf<Marker>()
    private var allReports: List<UserReport> = emptyList()
    private var statusFilter: Status? = null   // null = All
    private var query = ""
    private var shown: UserReport? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.admin_map)

        card = findViewById(R.id.cardAdminMap)

        // MAP
        map = findViewById<MapView>(R.id.adminMapView).apply {
            setTileSource(MapTiles.STREETS)
            setMultiTouchControls(true)
            // Show the +/- buttons so zooming also works without pinching
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.ALWAYS)
            NasugbuBoundary.applyToMap(this)                    // <-- new: zoom + camera limits, outline
            controller.setZoom(14.0)
            controller.setCenter(NasugbuBoundary.safeCenter())  // <-- changed: opens inside Nasugbu

            // Tap on empty map closes the card.
            // Added first so the markers (added later) get the taps before it.
            overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                    hideCard()
                    return true
                }

                override fun longPressHelper(p: GeoPoint) = false
            }))

            // Keep the map from being blocked by parent scrolling
            setOnTouchListener { v, e ->
                v.parent.requestDisallowInterceptTouchEvent(
                    e.action != MotionEvent.ACTION_UP && e.action != MotionEvent.ACTION_CANCEL
                )
                false
            }
        }

        // SEARCH (zooms to the matching incidents while typing)
        findViewById<EditText>(R.id.edtAdminMapSearch).doAfterTextChanged {
            query = it?.toString().orEmpty().trim().lowercase()
            showMarkers(zoomToResults = true)
        }

        // STATUS CHIPS YUNG MGA COLOR TUH
        chips = mapOf<TextView, Status?>(
            findViewById<TextView>(R.id.adminMapChipAll) to null,
            findViewById<TextView>(R.id.adminMapChipSubmitted) to Status.SUBMITTED,
            findViewById<TextView>(R.id.adminMapChipVerified) to Status.VERIFIED,
            findViewById<TextView>(R.id.adminMapChipResolved) to Status.RESOLVED,
            findViewById<TextView>(R.id.adminMapChipRejected) to Status.REJECTED
        )
        chips.forEach { (chip, status) ->
            chip.setOnClickListener {
                statusFilter = status
                showMarkers(zoomToResults = false)
            }
        }

        // Reload markers whenever reports change
        ReportStore.observe(this) {
            allReports = ReportStore.all().sortedByDescending { it.createdAt }
            showMarkers(zoomToResults = false)
        }

        // VIEW DETAILS (opens the report detail for now)
        findViewById<View>(R.id.btnAdminMapDetails).setOnClickListener {
            shown?.let { report ->
                startActivity(
                    Intent(this, UserReportDetailActivity::class.java)
                        .putExtra("report", report)
                )
            }
        }

        // BOTTOM NAVIGATION
        AdminBottomNav.setup(
            this,
            findViewById<BottomNavigationView>(R.id.adminBottomNav),
            R.id.admin_nav_map
        )
    }

    // markers

    private fun showMarkers(zoomToResults: Boolean) {
        chips.forEach { (chip, status) -> chip.isSelected = status == statusFilter }

        markers.forEach { map.overlays.remove(it) }
        markers.clear()

        val list = allReports.filter { r ->
            // skip reports without a real location
            !(r.lat == 0.0 && r.lng == 0.0) &&
                    (statusFilter == null || r.status == statusFilter) &&
                    (query.isEmpty() ||
                            listOf(r.type, r.location, r.reporter, r.id)
                                .any { it.lowercase().contains(query) })
        }

        list.forEach { r ->
            val marker = Marker(map).apply {
                position = GeoPoint(r.lat, r.lng)
                icon = dotIcon(colorFor(r.status))
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { _, _ ->
                    showCard(r)
                    true
                }
            }
            markers.add(marker)
            map.overlays.add(marker)
        }
        map.invalidate()

        // Close the card if its incident is no longer shown
        if (shown != null && list.none { it.id == shown?.id }) hideCard()

        // While searching, bring the matches into view
        if (zoomToResults && query.isNotEmpty() && list.isNotEmpty()) {
            val points = list.map { GeoPoint(it.lat, it.lng) }
            if (points.size == 1) {
                map.controller.setZoom(16.0)
                map.controller.animateTo(points[0])
            } else {
                map.post {
                    map.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(points), true, dp(60))
                }
            }
        }
    }

    private fun colorFor(status: Status) = Color.parseColor(
        when (status) {
            Status.SUBMITTED -> "#F59E0B"   // pending: orange
            Status.VERIFIED -> "#2563EB"    // blue
            Status.RESOLVED -> "#22C55E"    // green
            Status.REJECTED -> "#94A3B8"    // grey
        }
    )

    private fun dotIcon(color: Int): Drawable {
        val halo = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(60, Color.red(color), Color.green(color), Color.blue(color)))
            setSize(dp(32), dp(32))
        }
        val white = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
            setSize(dp(16), dp(16))
        }
        val core = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setSize(dp(10), dp(10))
        }
        return LayerDrawable(arrayOf(halo, white, core)).apply {
            setLayerInset(1, dp(8), dp(8), dp(8), dp(8))
            setLayerInset(2, dp(11), dp(11), dp(11), dp(11))
        }
    }

    // info card

    private fun showCard(r: UserReport) {
        shown = r

        findViewById<ImageView>(R.id.imgAdminMapIcon).apply {
            setImageResource(IncidentIcons.iconFor(r.type))
            setColorFilter(IncidentIcons.colorFor(r.type))
        }
        findViewById<TextView>(R.id.txtAdminMapTitle).text = r.type
        findViewById<TextView>(R.id.txtAdminMapLocation).text = r.location
        findViewById<TextView>(R.id.txtAdminMapMeta).text =
            "${r.reporter.ifBlank { "Anonymous" }} · ${adminTimeAgo(r.createdAt)} · ${r.id}"

        styleBadge(
            findViewById(R.id.txtAdminMapSeverity),
            r.severity.label, r.severity.text, r.severity.bg
        )
        styleBadge(
            findViewById(R.id.txtAdminMapStatus),
            r.status.label, r.status.text, r.status.bg
        )

        card.visibility = View.VISIBLE
        map.controller.animateTo(GeoPoint(r.lat, r.lng))
    }

    private fun hideCard() {
        shown = null
        card.visibility = View.GONE
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // lifecycle

    override fun onResume() {
        super.onResume()
        map.onResume()
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