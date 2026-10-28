package com.example.incidex

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class AdminIncidentDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ID = "report_id"
    }

    private lateinit var reportId: String
    private lateinit var edtNote: EditText
    private var noteLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.admin_incident_detail)

        reportId = intent.getStringExtra(EXTRA_ID) ?: run { finish(); return }
        edtNote = findViewById(R.id.edtAdminDetailNote)

        findViewById<View>(R.id.btnAdminDetailBack).setOnClickListener { finish() }

        // Collapsible rows
        setupToggle(
            findViewById(R.id.rowAdminDetailReporter),
            findViewById(R.id.txtAdminDetailReporterBody),
            findViewById(R.id.chevAdminDetailReporter)
        )
        setupToggle(
            findViewById(R.id.rowAdminDetailMap),
            findViewById(R.id.bodyAdminDetailMap),
            findViewById(R.id.chevAdminDetailMap)
        )

        // Note
        findViewById<View>(R.id.btnAdminDetailSaveNote).setOnClickListener {
            val note = edtNote.text.toString().trim()
            ReportStore.saveNote(reportId, note) { error ->
                edtNote.clearFocus()
                Toast.makeText(this, error ?: "Note saved", Toast.LENGTH_SHORT).show()
            }
        }

        // Actions
        findViewById<View>(R.id.btnAdminVerify).setOnClickListener { setStatus(Status.VERIFIED) }
        findViewById<View>(R.id.btnAdminResolve).setOnClickListener { setStatus(Status.RESOLVED) }
        findViewById<View>(R.id.btnAdminReject).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reject this report?")
                .setMessage("It will be marked as Rejected and the reporter will see that status.")
                .setPositiveButton("Reject") { _, _ -> setStatus(Status.REJECTED) }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Redraw whenever the report changes (for example after a status update)
        ReportStore.observe(this) { bind() }
    }

    private fun setupToggle(header: View, body: View, chevron: View) {
        header.setOnClickListener {
            val open = body.visibility != View.VISIBLE
            body.visibility = if (open) View.VISIBLE else View.GONE
            chevron.animate().rotation(if (open) 180f else 0f).setDuration(150).start()
        }
    }

    private fun setStatus(newStatus: Status) {
        val note = edtNote.text.toString().trim()

        ReportStore.setStatus(reportId, newStatus, note) { error ->
            val message = error
                ?: "Marked as ${newStatus.label.lowercase().replaceFirstChar { it.uppercase() }}"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun bind() {
        // The report may not have arrived yet; bind() runs again when it does
        val r = ReportStore.find(reportId) ?: return

        if (!noteLoaded) {
            noteLoaded = true
            edtNote.setText(r.adminNote)
        }

        // PHOTO
        val photo = findViewById<ImageView>(R.id.imgAdminDetailPhoto)
        val url = r.photoUrl
        val bmp = r.photoPath?.let { loadBitmapScaled(it, 1024) }
        when {
            !url.isNullOrBlank() -> {
                photo.visibility = View.VISIBLE
                ImageLoader.load(photo, url, 1024) { ok -> if (!ok) photo.visibility = View.GONE }
            }
            bmp != null -> { photo.setImageBitmap(bmp); photo.visibility = View.VISIBLE }
            r.photoRes != 0 -> { photo.setImageResource(r.photoRes); photo.visibility = View.VISIBLE }
            else -> photo.visibility = View.GONE
        }

        // DETAILS
        findViewById<ImageView>(R.id.imgAdminDetailIcon).apply {
            setImageResource(IncidentIcons.iconFor(r.type))
            setColorFilter(IncidentIcons.colorFor(r.type))
        }
        findViewById<TextView>(R.id.txtAdminDetailTitle).text = r.type
        findViewById<TextView>(R.id.txtAdminDetailId).text = r.id
        findViewById<TextView>(R.id.txtAdminDetailLocation).text = r.location
        findViewById<TextView>(R.id.txtAdminDetailDateTime).text = r.dateTime
        findViewById<TextView>(R.id.txtAdminDetailDescription).text = r.description
        styleBadge(
            findViewById(R.id.txtAdminDetailSeverity),
            r.severity.label, r.severity.text, r.severity.bg
        )
        styleBadge(
            findViewById(R.id.txtAdminDetailStatus),
            r.status.label, r.status.text, r.status.bg
        )

        // TIMELINE
        val verifiedDone = r.status == Status.VERIFIED || r.status == Status.RESOLVED
        val resolvedDone = r.status == Status.RESOLVED
        bindStep(findViewById(R.id.adminStepSubmitted), "Submitted", true, r.submittedTime,
            leftOn = false, rightOn = verifiedDone, first = true, last = false)
        bindStep(findViewById(R.id.adminStepVerified), "Verified", verifiedDone, r.verifiedTime,
            leftOn = verifiedDone, rightOn = resolvedDone, first = false, last = false)
        bindStep(findViewById(R.id.adminStepResolved), "Resolved", resolvedDone, r.resolvedTime,
            leftOn = resolvedDone, rightOn = false, first = false, last = true)
        findViewById<View>(R.id.txtAdminDetailRejected).visibility =
            if (r.status == Status.REJECTED) View.VISIBLE else View.GONE

        // REPORTER
        val reporter = r.reporter.ifBlank { "Anonymous" }
        findViewById<TextView>(R.id.txtAdminDetailReporterHeader).text = "Reporter — $reporter"
        findViewById<TextView>(R.id.txtAdminDetailReporterBody).text =
            "Name: $reporter\nReported: ${adminTimeAgo(r.createdAt)}\nSubmitted: ${r.dateTime}"

        // MAP
        findViewById<TextView>(R.id.txtAdminDetailCoords).text =
            "%.5f, %.5f".format(Locale.US, r.lat, r.lng)
        findViewById<View>(R.id.btnAdminDetailOpenMaps).setOnClickListener {
            val uri = Uri.parse("geo:${r.lat},${r.lng}?q=${r.lat},${r.lng}(${Uri.encode(r.type)})")
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, "No maps app found", Toast.LENGTH_SHORT).show()
            }
        }

        // ACTION BUTTONS: only the steps that make sense stay enabled
        setEnabled(R.id.btnAdminVerify, r.status == Status.SUBMITTED)
        setEnabled(R.id.btnAdminResolve, r.status == Status.VERIFIED)
        setEnabled(R.id.btnAdminReject, r.status == Status.SUBMITTED || r.status == Status.VERIFIED)
    }

    private fun setEnabled(id: Int, enabled: Boolean) {
        findViewById<View>(id).apply {
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.4f
        }
    }

    private fun bindStep(
        step: View, label: String, done: Boolean, time: String?,
        leftOn: Boolean, rightOn: Boolean, first: Boolean, last: Boolean
    ) {
        val on = Color.parseColor("#2563EB")
        val off = Color.parseColor("#E2E8F0")

        step.findViewById<View>(R.id.stepLineLeft).apply {
            visibility = if (first) View.INVISIBLE else View.VISIBLE
            setBackgroundColor(if (leftOn) on else off)
        }
        step.findViewById<View>(R.id.stepLineRight).apply {
            visibility = if (last) View.INVISIBLE else View.VISIBLE
            setBackgroundColor(if (rightOn) on else off)
        }
        step.findViewById<ImageView>(R.id.stepCircle).apply {
            setBackgroundResource(
                if (done) R.drawable.admin_bg_step_done else R.drawable.admin_bg_step_todo
            )
            if (done) setImageResource(R.drawable.admin_ic_check) else setImageDrawable(null)
        }
        step.findViewById<TextView>(R.id.stepLabel).text = label
        step.findViewById<TextView>(R.id.stepTime).apply {
            text = time.orEmpty()
            visibility = if (time.isNullOrBlank()) View.GONE else View.VISIBLE
        }
    }
}