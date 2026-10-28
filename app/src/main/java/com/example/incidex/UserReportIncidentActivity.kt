package com.example.incidex

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.textfield.TextInputEditText
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UserReportIncidentActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_QUICK_TYPE = "quick_type"   // "Accident", "Traffic", ...
        const val EXTRA_GUEST = "guest"             // from MainActivity (not logged in)
    }

    private val draft = ReportDraft()
    private val total = 5
    private var step = 1
    private val stepNames = listOf("Location", "Type", "Severity", "Details", "Review")
    private val dateFormat = "MMM d, yyyy · hh:mm a"

    private lateinit var steps: List<View>
    private lateinit var segs: List<View>
    private lateinit var txtStepSub: TextView
    private lateinit var btnNext: Button

    // Step 1
    private lateinit var map: MapView
    private var pinMarker: Marker? = null
    private lateinit var txtAddress: TextView
    private lateinit var txtPinTime: TextView

    // Step 2 & 3
    private val typeViews = mutableMapOf<ReportType, View>()
    private val severityViews = mutableMapOf<Severity, View>()

    // Step 4
    private lateinit var edtDesc: TextInputEditText
    private lateinit var imgPhotoPreview: ImageView
    private lateinit var txtPhotoHint: TextView
    private lateinit var btnRemovePhoto: View
    private var pendingPhotoFile: File? = null

    // True while the photo is uploading and the report is being saved
    private var submitting = false

    private val isGuest by lazy { intent.getBooleanExtra(EXTRA_GUEST, false) }

    // Camera: takes the picture and saves it to pendingPhotoFile
    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val file = pendingPhotoFile
            if (success && file != null && file.length() > 0) {
                setPhoto(file.absolutePath)
            } else {
                file?.delete() // cancelled or nothing captured
            }
            pendingPhotoFile = null
        }

    private val askLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) useMyLocation() else toast("Location permission denied")
        }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.report_incident)

        steps = listOf(
            R.id.stepLocation, R.id.stepType, R.id.stepSeverity, R.id.stepDetails, R.id.stepReview
        ).map { findViewById<View>(it) }
        segs = listOf(R.id.seg1, R.id.seg2, R.id.seg3, R.id.seg4, R.id.seg5)
            .map { findViewById<View>(it) }

        txtStepSub = findViewById(R.id.txtStepSub)
        btnNext = findViewById(R.id.btnNext)

        setupMap()
        buildTypeGrid()
        setupSeverity()
        setupDetails()

        // Quick report from Home: the type is already selected
        val quick = intent.getStringExtra(EXTRA_QUICK_TYPE)
        REPORT_TYPES.firstOrNull { it.quickLabel == quick }?.let { selectType(it) }

        findViewById<View>(R.id.btnBack).setOnClickListener { handleBack() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleBack()
        })

        btnNext.setOnClickListener {
            if (step < total) showStep(step + 1) else submit()
        }

        showStep(1)
    }

    // ---------------------------------------------------------------- navigation

    private fun showStep(n: Int) {
        step = n
        steps.forEachIndexed { i, v -> v.visibility = if (i == n - 1) View.VISIBLE else View.GONE }
        segs.forEachIndexed { i, v -> v.background = segBg(i < n) }
        txtStepSub.text = "Step $n of $total — ${stepNames[n - 1]}"
        btnNext.text = if (n == total) "Submit Report" else "Next  ›"
        if (n == total) bindReview()
        hideKeyboard()
        updateNext()
    }

    private fun handleBack() {
        if (submitting) return   // don't leave while the report is being sent
        when {
            step > 1 -> showStep(step - 1)
            hasProgress() -> AlertDialog.Builder(this)
                .setTitle("Discard report?")
                .setMessage("Your answers won't be saved.")
                .setPositiveButton("Discard") { _, _ ->
                    draft.photoPath?.let { File(it).delete() }
                    finish()
                }
                .setNegativeButton("Keep editing", null)
                .show()
            else -> finish()
        }
    }

    private fun hasProgress() =
        draft.lat != null || draft.severity != null ||
                draft.description.isNotBlank() || draft.photoPath != null

    private fun updateNext() {
        val valid = !submitting && when (step) {
            1 -> draft.lat != null
            2 -> draft.type != null
            3 -> draft.severity != null
            4 -> draft.description.trim().length >= 10 && draft.photoPath != null   // photo is required
            else -> true
        }
        btnNext.isEnabled = valid
        btnNext.alpha = if (valid) 1f else 0.5f
    }

    private fun segBg(active: Boolean) = GradientDrawable().apply {
        cornerRadius = dp(2).toFloat()
        setColor(Color.parseColor(if (active) "#2563EB" else "#E2E8F0"))
    }

    // ---------------------------------------------------------------- step 1: location

    private fun setupMap() {
        txtAddress = findViewById(R.id.txtAddress)
        txtPinTime = findViewById(R.id.txtPinTime)

        map = findViewById<MapView>(R.id.reportMapView).apply {
            setTileSource(MapTiles.STREETS)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(14.0697, 120.6328)) // Nasugbu
            overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                    placePin(p.latitude, p.longitude)
                    return true
                }

                override fun longPressHelper(p: GeoPoint) = false
            }))
        }

        findViewById<View>(R.id.btnMyLocation).setOnClickListener {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) useMyLocation()
            else askLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @SuppressLint("MissingPermission")
    private fun useMyLocation() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { provider ->
                try { lm.getLastKnownLocation(provider) } catch (e: Exception) { null }
            }
            .maxByOrNull { it.time }

        if (loc == null) {
            toast("Can't get your location yet. Tap the map to place the pin.")
            return
        }
        placePin(loc.latitude, loc.longitude, moveCamera = true)
    }

    private fun placePin(lat: Double, lng: Double, moveCamera: Boolean = false) {
        draft.lat = lat
        draft.lng = lng
        draft.address = ""
        draft.pinTime = SimpleDateFormat(dateFormat, Locale.getDefault()).format(Date())

        pinMarker?.let { map.overlays.remove(it) }
        val point = GeoPoint(lat, lng)
        pinMarker = Marker(map).apply {
            position = point
            icon = mapPin(this@UserReportIncidentActivity)   // dark blue pin
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        map.overlays.add(pinMarker)
        if (moveCamera) {
            map.controller.setZoom(17.0)
            map.controller.animateTo(point)
        }
        map.invalidate()

        txtPinTime.text = "Pin placed · ${draft.pinTime}"
        lookupAddress(lat, lng)
        updateNext()
    }

    private fun coordsText() = "%.5f, %.5f".format(draft.lat ?: 0.0, draft.lng ?: 0.0)

    private fun lookupAddress(lat: Double, lng: Double) {
        txtAddress.text = "Finding address…"
        Thread {
            val text = try {
                @Suppress("DEPRECATION")
                val a = Geocoder(this, Locale.getDefault()).getFromLocation(lat, lng, 1)?.firstOrNull()
                if (a == null) {
                    ""
                } else {
                    val street = listOfNotNull(a.subThoroughfare, a.thoroughfare)
                        .joinToString(" ")
                        .ifBlank { null }
                    listOfNotNull(
                        street,                         // street (if there is one)
                        a.subLocality,                  // barangay / ward
                        a.locality ?: a.subAdminArea,   // town / city
                        a.adminArea                     // province / region
                    ).distinct().joinToString(", ")
                }
            } catch (e: Exception) {
                ""
            }
            runOnUiThread {
                // If the pin moved while we were searching, don't update
                if (draft.lat == lat && draft.lng == lng) {
                    draft.address = text.ifBlank { coordsText() }
                    txtAddress.text = draft.address
                }
            }
        }.start()
    }

    // ---------------------------------------------------------------- step 2: type

    private fun buildTypeGrid() {
        val grid = findViewById<LinearLayout>(R.id.typeGrid)
        REPORT_TYPES.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(10) }
            }
            pair.forEachIndexed { index, type ->
                val v = layoutInflater.inflate(R.layout.report_item_type, row, false)
                v.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (index == 0) marginEnd = dp(5) else marginStart = dp(5)
                }
                v.findViewById<ImageView>(R.id.txtTypeIcon).apply {
                    setImageResource(IncidentIcons.iconFor(type.name))
                    setColorFilter(IncidentIcons.colorFor(type.name))
                }
                v.findViewById<TextView>(R.id.txtTypeLabel).text = type.name
                v.setOnClickListener { selectType(type) }
                typeViews[type] = v
                row.addView(v)
            }
            grid.addView(row)
        }
    }

    private fun selectType(type: ReportType) {
        draft.type = type
        typeViews.forEach { (t, v) -> v.isSelected = t == type }
        updateNext()
    }

    // ---------------------------------------------------------------- step 3: severity

    private fun setupSeverity() {
        severityViews[Severity.CRITICAL] = findViewById(R.id.optCritical)
        severityViews[Severity.MODERATE] = findViewById(R.id.optModerate)
        severityViews[Severity.LOW] = findViewById(R.id.optLow)

        severityViews.forEach { (sev, v) ->
            v.setOnClickListener {
                draft.severity = sev
                severityViews.forEach { (s, view) -> view.isSelected = s == sev }
                updateNext()
            }
        }
    }

    // ---------------------------------------------------------------- step 4: details

    private fun setupDetails() {
        edtDesc = findViewById(R.id.edtDesc)
        imgPhotoPreview = findViewById(R.id.imgPhotoPreview)
        txtPhotoHint = findViewById(R.id.txtPhotoHint)
        btnRemovePhoto = findViewById(R.id.btnRemovePhoto)

        txtPhotoHint.text = "Tap to take a photo (required)"
        txtPhotoHint.setTopIcon(R.drawable.ic_camera, Color.parseColor("#64748B"), 32, 6)

        edtDesc.doAfterTextChanged {
            draft.description = it?.toString() ?: ""
            updateNext()
        }

        // Tap the box (or the preview) to take / retake the photo
        findViewById<View>(R.id.btnAddPhoto).setOnClickListener { launchCamera() }
        btnRemovePhoto.setOnClickListener { clearPhoto() }
    }

    private fun launchCamera() {
        val dir = File(filesDir, "report_photos").apply { mkdirs() }
        val file = File(dir, "report_${System.currentTimeMillis()}.jpg")
        pendingPhotoFile = file

        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        try {
            takePicture.launch(uri)
        } catch (e: ActivityNotFoundException) {
            pendingPhotoFile = null
            toast("No camera app found on this device")
        }
    }

    private fun setPhoto(path: String) {
        val bmp = loadBitmapScaled(path)
        if (bmp == null) {
            toast("Couldn't load that photo")
            return
        }
        // Retake: delete the earlier photo
        draft.photoPath?.takeIf { it != path }?.let { File(it).delete() }

        draft.photoPath = path
        imgPhotoPreview.setImageBitmap(bmp)
        imgPhotoPreview.visibility = View.VISIBLE
        txtPhotoHint.visibility = View.GONE
        btnRemovePhoto.visibility = View.VISIBLE
        updateNext()
    }

    private fun clearPhoto() {
        draft.photoPath?.let { File(it).delete() }
        draft.photoPath = null
        imgPhotoPreview.setImageDrawable(null)
        imgPhotoPreview.visibility = View.GONE
        txtPhotoHint.visibility = View.VISIBLE
        btnRemovePhoto.visibility = View.GONE
        updateNext()
    }

    // ---------------------------------------------------------------- step 5: review

    private fun bindReview() {
        val t = draft.type ?: return
        val s = draft.severity ?: return

        findViewById<TextView>(R.id.txtRvType).apply {
            text = t.name
            setStartIcon(
                IncidentIcons.iconFor(t.name),
                IncidentIcons.colorFor(t.name),
                20,
                8
            )
        }
        styleBadge(findViewById(R.id.txtRvSeverity), s.label, s.text, s.bg)
        findViewById<TextView>(R.id.txtRvLocation).apply {
            text = draft.address.ifBlank { coordsText() }
            setStartIcon(R.drawable.ic_location, Color.parseColor("#E53935"), 16, 6)
        }
        findViewById<TextView>(R.id.txtRvDate).apply {
            text = SimpleDateFormat(dateFormat, Locale.getDefault()).format(Date())
            setStartIcon(R.drawable.ic_time, Color.parseColor("#64748B"), 16, 6)
        }
        findViewById<TextView>(R.id.txtRvDescription).text = draft.description.trim()

        val img = findViewById<ImageView>(R.id.imgRvPhoto)
        val bmp = draft.photoPath?.let { loadBitmapScaled(it) }
        if (bmp != null) {
            img.setImageBitmap(bmp)
            img.visibility = View.VISIBLE
        } else {
            img.visibility = View.GONE
        }
    }

    // ---------------------------------------------------------------- submit

    private fun setSubmitting(value: Boolean) {
        submitting = value
        btnNext.text = if (value) "Submitting…" else "Submit Report"
        updateNext()
    }

    private fun fail(message: String) {
        if (isFinishing || isDestroyed) return
        setSubmitting(false)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    /** Sign in (guests get an anonymous account) -> upload the photo -> save the report in Firestore. */
    private fun submit() {
        if (submitting) return

        val type = draft.type ?: return
        val severity = draft.severity ?: return
        val lat = draft.lat ?: return
        val lng = draft.lng ?: return

        // A photo is required to submit
        val photoPath = draft.photoPath
        if (photoPath == null) {
            toast("A photo is required to submit a report")
            showStep(4)
            return
        }

        setSubmitting(true)

        AuthRepository.ensureSignedIn { authError ->
            if (authError != null) {
                fail(authError)
                return@ensureSignedIn
            }

            CloudinaryUploader.upload(photoPath) { photoUrl, uploadError ->
                if (photoUrl == null) {
                    fail(uploadError ?: "Photo upload failed.")
                    return@upload
                }

                ReportStore.submit(
                    type = type.name,
                    severity = severity,
                    location = draft.address.ifBlank { coordsText() },
                    description = draft.description,
                    lat = lat,
                    lng = lng,
                    photoUrl = photoUrl
                ) { id, saveError ->
                    if (id == null) {
                        fail(saveError ?: "Couldn't save the report.")
                    } else {
                        File(photoPath).delete()   // the photo is online now
                        draft.photoPath = null
                        onSubmitted(id)
                    }
                }
            }
        }
    }

    private fun onSubmitted(id: String) {
        if (isFinishing || isDestroyed) return
        submitting = false

        if (isGuest) {
            // Not logged in: ask if they want to log in or register
            startActivity(
                Intent(this, AccountChoiceActivity::class.java)
                    .putExtra(AccountChoiceActivity.EXTRA_REPORT_ID, id)
            )
            finish()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Report submitted")
            .setMessage("Your report $id was sent. You can track it in My Reports.")
            .setCancelable(false)
            .setPositiveButton("View My Reports") { _, _ ->
                startActivity(Intent(this, UserReportsActivity::class.java))
                finish()
            }
            .show()
    }

    // helpers

    private fun hideKeyboard() {
        val view = currentFocus ?: return
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onResume() { super.onResume(); map.onResume() }
    override fun onPause() { super.onPause(); map.onPause() }
}