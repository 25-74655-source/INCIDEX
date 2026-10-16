package com.example.incidex

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.transition.TransitionManager
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class UserReportDetailActivity : AppCompatActivity() {

    private lateinit var report: UserReport
    private var map: MapView? = null
    private var mapReady = false

    @Suppress("DEPRECATION")
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.user_report_detail)

        report = intent.getSerializableExtra("report") as UserReport
        val r = report

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.txtTitle).text = r.type

        findViewById<ImageView>(R.id.txtIcon).apply {
            setImageResource(IncidentIcons.iconFor(r.type))
            setColorFilter(IncidentIcons.colorFor(r.type))
        }

        findViewById<TextView>(R.id.txtType).text = r.type
        findViewById<TextView>(R.id.txtId).text = r.id

        findViewById<TextView>(R.id.txtLocation).apply {
            text = r.location
            setStartIcon(R.drawable.ic_location, Color.parseColor("#E53935"), 16, 6)
        }
        findViewById<TextView>(R.id.txtDate).apply {
            text = r.dateTime
            setStartIcon(R.drawable.ic_time, Color.parseColor("#64748B"), 16, 6)
        }

        findViewById<TextView>(R.id.txtDescription).text = r.description

        val imgPhoto = findViewById<ImageView>(R.id.imgPhoto)
        val userPhoto = r.photoPath?.let { loadBitmapScaled(it) }
        if (userPhoto != null) {
            imgPhoto.setImageBitmap(userPhoto)
            imgPhoto.visibility = View.VISIBLE
        } else if (r.photoRes != 0) {
            imgPhoto.setImageResource(r.photoRes)
            imgPhoto.visibility = View.VISIBLE
        }

        styleBadge(findViewById(R.id.txtSeverity), r.severity.label, r.severity.text, r.severity.bg)
        styleBadge(findViewById(R.id.txtStatus), r.status.label, r.status.text, r.status.bg)

        setupTimeline(r)

        // EXPANDABLES
        setupExpandable(
            findViewById(R.id.headerDesc),
            findViewById(R.id.txtDescription),
            findViewById(R.id.arrowDesc),
            findViewById(R.id.cardDesc)
        )
        setupExpandable(
            findViewById(R.id.headerMap),
            findViewById(R.id.contentMap),
            findViewById(R.id.arrowMap),
            findViewById(R.id.cardMap)
        ) { setupMapOnce() }

        // Para gumana ang pan/zoom ng map sa loob ng ScrollView
        val scroll = findViewById<ScrollView>(R.id.scroll)
        findViewById<MapView>(R.id.detailMapView).setOnTouchListener { _, e ->
            scroll.requestDisallowInterceptTouchEvent(
                e.action != MotionEvent.ACTION_UP && e.action != MotionEvent.ACTION_CANCEL
            )
            false
        }

        // SHARE
        findViewById<View>(R.id.btnShare).setOnClickListener {
            val text = "${r.type} (${r.id})\n${r.location}\n${r.dateTime}\n" +
                    "https://maps.google.com/?q=${r.lat},${r.lng}"
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    },
                    "Share report"
                )
            )
        }
    }

    private fun setupExpandable(
        header: View,
        content: View,
        arrow: View,
        card: ViewGroup,
        onOpen: (() -> Unit)? = null
    ) {
        header.setOnClickListener {
            val opening = content.visibility != View.VISIBLE
            TransitionManager.beginDelayedTransition(card)
            content.visibility = if (opening) View.VISIBLE else View.GONE
            arrow.animate().rotation(if (opening) 180f else 0f).setDuration(200).start()
            if (opening) onOpen?.invoke()
        }
    }

    private fun setupMapOnce() {
        if (mapReady) return
        mapReady = true
        map = findViewById<MapView>(R.id.detailMapView).apply {
            setTileSource(MapTiles.STREETS)
            setMultiTouchControls(true)
            val point = GeoPoint(report.lat, report.lng)
            controller.setZoom(16.0)
            controller.setCenter(point)
            overlays.add(Marker(this).apply {
                position = point
                title = report.type
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            })
        }
    }

    private fun setupTimeline(r: UserReport) {
        val blue = Color.parseColor("#2563EB")
        val gray = Color.parseColor("#E2E8F0")
        val textDone = Color.parseColor("#111827")
        val textPending = Color.parseColor("#94A3B8")

        val steps = listOf(
            Triple("Submitted", r.submittedTime, true),
            Triple("Verified", r.verifiedTime, r.verifiedTime != null),
            Triple("Resolved", r.resolvedTime, r.resolvedTime != null)
        )
        val dots = listOf<TextView>(findViewById(R.id.dot1), findViewById(R.id.dot2), findViewById(R.id.dot3))
        val labels = listOf<TextView>(findViewById(R.id.txtT1), findViewById(R.id.txtT2), findViewById(R.id.txtT3))

        steps.forEachIndexed { i, (name, time, done) ->
            dots[i].setBackgroundResource(if (done) R.drawable.user_step_done else R.drawable.user_step_pending)
            dots[i].text = if (done) "✓" else ""
            labels[i].text = if (time != null) "$name\n$time" else name
            labels[i].setTextColor(if (done) textDone else textPending)
        }

        findViewById<View>(R.id.line1).setBackgroundColor(if (r.verifiedTime != null) blue else gray)
        findViewById<View>(R.id.line2).setBackgroundColor(if (r.resolvedTime != null) blue else gray)
    }

    override fun onResume() { super.onResume(); map?.onResume() }
    override fun onPause() { super.onPause(); map?.onPause() }
}