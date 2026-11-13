package com.example.incidex

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

// =====================================================================================
//  Data
// =====================================================================================

data class Hospital(
    val id: String,
    val name: String,
    val municipality: String,
    val address: String,
    val lat: Double?,
    val lng: Double?,
    val kind: String,           // "hospital" or "infirmary"
    val level: Int?,            // DOH level, if known
    val ownership: String,      // "government" or "private"
    val open24h: Boolean,
    val er: String,             // "yes", "unconfirmed" or "no"
    val services: Set<String>,  // "ICU", "OR", "AMBULANCE"
    val phones: List<String>,
    val notes: String
) {
    val hasLocation: Boolean get() = lat != null && lng != null
}

class RoutingData(
    val mdrrmoName: String,
    val mdrrmoLat: Double?,
    val mdrrmoLng: Double?,
    val hospitals: List<Hospital>
)

class RouteResult(
    val points: List<GeoPoint>,
    val distanceM: Double,
    val durationS: Double
)

/** An error whose message is safe to show to the admin. */
class RouteException(message: String) : Exception(message)

/** Reads app/src/main/assets/routing_data.json */
object RoutingDataLoader {

    private const val TAG = "RoutingData"

    fun load(context: Context): RoutingData {
        val text = context.assets.open("routing_data.json").bufferedReader().use { it.readText() }
        val root = JSONObject(text)

        val office = root.getJSONObject("mdrrmo")
        val officeLat = office.doubleOrNull("lat")
        val officeLng = office.doubleOrNull("lng")
        val officeOk = validPoint(officeLat, officeLng)

        val array = root.getJSONArray("hospitals")
        val list = ArrayList<Hospital>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            var lat = o.doubleOrNull("lat")
            var lng = o.doubleOrNull("lng")
            if (!validPoint(lat, lng)) {
                if (lat != null || lng != null) {
                    Log.w(TAG, "${o.optString("name")} has invalid coordinates ($lat, $lng). Is lat/lng swapped?")
                }
                lat = null
                lng = null
            }
            list.add(
                Hospital(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    municipality = o.optString("municipality"),
                    address = o.optString("address"),
                    lat = lat,
                    lng = lng,
                    kind = o.optString("kind", "hospital"),
                    level = o.intOrNull("level"),
                    ownership = o.optString("ownership"),
                    open24h = o.optBoolean("open24h", false),
                    er = o.optString("er", "unconfirmed"),
                    services = o.stringList("services").toSet(),
                    phones = o.stringList("phones"),
                    notes = o.optString("notes")
                )
            )
        }

        return RoutingData(
            mdrrmoName = office.optString("name", "MDRRMO Nasugbu"),
            mdrrmoLat = if (officeOk) officeLat else null,
            mdrrmoLng = if (officeOk) officeLng else null,
            hospitals = list
        )
    }

    private fun validPoint(lat: Double?, lng: Double?): Boolean =
        lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0

    private fun JSONObject.doubleOrNull(key: String): Double? =
        if (isNull(key)) null else getDouble(key)

    private fun JSONObject.intOrNull(key: String): Int? =
        if (isNull(key)) null else getInt(key)

    private fun JSONObject.stringList(key: String): List<String> {
        val a = optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).map { a.getString(it) }
    }
}

// =====================================================================================
//  The hospital rule
// =====================================================================================

object RoutePlanner {

    class Selection(
        val candidates: List<Hospital>,
        val downgraded: Boolean,        // true = nothing met the requirement, a lower tier was used
        val skippedNoLocation: Int      // hospitals ignored because they have no coordinates yet
    )

    fun select(all: List<Hospital>, severity: Severity): Selection {
        val withLocation = all.filter { it.hasLocation }
        val skipped = all.size - withLocation.size

        val strict = withLocation.filter { meets(it, severity) }
        if (strict.isNotEmpty()) return Selection(strict, false, skipped)

        // Nothing meets the requirement. Critical falls back to the Moderate tier (and the
        // screen says so). Other severities have no fallback.
        val fallback =
            if (severity == Severity.CRITICAL) withLocation.filter { meets(it, Severity.MODERATE) }
            else emptyList()
        return Selection(fallback, fallback.isNotEmpty(), skipped)
    }

    /**
     * THE RULE. Edit this function to change which hospital serves which severity.
     * Confirm the thresholds with MDRRMO before real use.
     */
    fun meets(h: Hospital, severity: Severity): Boolean = when (severity) {
        // Any facility open 24 hours that is not known to lack an ER
        Severity.LOW ->
            h.open24h && h.er != "no"

        // A real hospital (not an infirmary), open 24 hours, not known to lack an ER
        Severity.MODERATE ->
            h.kind == "hospital" && h.open24h && h.er != "no"

        // A hospital with a CONFIRMED ER, and DOH Level 2+ or both ICU and an operating room
        Severity.CRITICAL ->
            h.kind == "hospital" && h.er == "yes" &&
                    ((h.level ?: 0) >= 2 || ("ICU" in h.services && "OR" in h.services))
    }
}

// =====================================================================================
//  OpenRouteService (blocks, so call it from a background thread)
// =====================================================================================

object OrsClient {

    private const val ORS_API_KEY = "eyJvcmciOiI1YjNjZTM1OTc4NTExMTAwMDFjZjYyNDgiLCJpZCI6IjBiMjU0Nzc1MjE1NDQyYTFiN2QyZWQxMGM0YWI1NTQxIiwiaCI6Im11cm11cjY0In0="

    private const val TAG = "OrsClient"
    private const val BASE = "https://api.heigit.org/openrouteservice"
    private const val PROFILE = "driving-car"

    /** "shortest" = least road distance, "fastest" = least travel time. */
    const val PREFERENCE = "shortest"

    /** The hospital is picked by the same measure. */
    val BY_DISTANCE: Boolean = PREFERENCE == "shortest"

    fun directions(from: GeoPoint, to: GeoPoint): RouteResult {
        val body = JSONObject()
            .put("coordinates", JSONArray().put(lonLat(from)).put(lonLat(to)))
            .put("preference", PREFERENCE)
            .put("instructions", false)

        val text = post("/v2/directions/$PROFILE/geojson", body)
        try {
            val feature = JSONObject(text).getJSONArray("features").getJSONObject(0)
            val summary = feature.getJSONObject("properties").getJSONObject("summary")
            val coords = feature.getJSONObject("geometry").getJSONArray("coordinates")

            val points = ArrayList<GeoPoint>(coords.length())
            for (i in 0 until coords.length()) {
                val c = coords.getJSONArray(i)
                points.add(GeoPoint(c.getDouble(1), c.getDouble(0)))   // [lng, lat] -> lat, lng
            }
            return RouteResult(points, summary.getDouble("distance"), summary.getDouble("duration"))
        } catch (e: JSONException) {
            Log.e(TAG, "Unexpected directions answer: $text", e)
            throw RouteException("The routing service returned an unexpected answer.")
        }
    }

    /** Road distance (m) or travel time (s) from [origin] to each destination; null = no road link. */
    fun matrixCosts(origin: GeoPoint, destinations: List<GeoPoint>, byDistance: Boolean): List<Double?> {
        val locations = JSONArray().put(lonLat(origin))
        destinations.forEach { locations.put(lonLat(it)) }

        val body = JSONObject()
            .put("locations", locations)
            .put("metrics", JSONArray().put(if (byDistance) "distance" else "duration"))

        val text = post("/v2/matrix/$PROFILE", body)
        try {
            // Row 0 is the origin; columns 1..n are the destinations
            val row = JSONObject(text)
                .getJSONArray(if (byDistance) "distances" else "durations")
                .getJSONArray(0)
            return (1..destinations.size).map { i -> if (row.isNull(i)) null else row.getDouble(i) }
        } catch (e: JSONException) {
            Log.e(TAG, "Unexpected matrix answer: $text", e)
            throw RouteException("The routing service returned an unexpected answer.")
        }
    }

    // ORS wants [longitude, latitude], longitude first
    private fun lonLat(p: GeoPoint) = JSONArray().put(p.longitude).put(p.latitude)

    private fun post(path: String, body: JSONObject): String {
        if (ORS_API_KEY.isBlank() || ORS_API_KEY.startsWith("PASTE_")) {
            throw RouteException("No routing key yet. Set ORS_API_KEY in AdminRoute.kt.")
        }

        val conn = URL(BASE + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.doOutput = true
            conn.setRequestProperty("Authorization", ORS_API_KEY)
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Accept", "application/json, application/geo+json")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (code !in 200..299) {
                Log.e(TAG, "HTTP $code from $path: $text")
                throw RouteException(describeError(code, text))
            }
            return text
        } catch (e: RouteException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Request to $path failed", e)
            throw RouteException("Couldn't reach the routing service. Check the internet connection.")
        } finally {
            conn.disconnect()
        }
    }

    private fun describeError(code: Int, body: String): String {
        val error = try { JSONObject(body).optJSONObject("error") } catch (e: Exception) { null }
        val orsCode = error?.optInt("code", 0) ?: 0
        val detail = error?.optString("message").orEmpty()

        return when {
            code == 401 || code == 403 ->
                "The routing key was rejected, or today's free quota is used up."
            code == 429 ->
                "Too many requests. Please wait a minute and try again."
            orsCode == 2010 ->
                "A location is too far from a road, so no route could be found."
            else -> "Routing failed (HTTP $code). $detail".trim()
        }
    }
}

// =====================================================================================
//  The screen
// =====================================================================================

class AdminRoute : AppCompatActivity() {

    companion object {
        const val EXTRA_ID = "report_id"
        private const val ACCIDENT = "Road Accident"
    }

    private class Plan(
        val officeName: String,
        val office: GeoPoint,
        val incident: GeoPoint,
        val leg1: RouteResult,
        val hospital: Hospital?,
        val leg2: RouteResult?,
        val notes: List<String>
    )

    private lateinit var map: MapView
    private lateinit var report: UserReport

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.admin_route)

        findViewById<View>(R.id.btnAdminRouteBack).setOnClickListener { finish() }

        val found = intent.getStringExtra(EXTRA_ID)?.let { ReportStore.find(it) }
        if (found == null) {
            toast("Report not found")
            finish()
            return
        }
        report = found

        // Plain map: the Nasugbu-only camera limit is NOT applied here, because the
        // hospital route may leave Nasugbu (Balayan, Tagaytay).
        map = findViewById<MapView>(R.id.adminRouteMap).apply {
            setTileSource(MapTiles.STREETS)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.ALWAYS)
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(report.lat, report.lng))
        }

        bindSummary()
        compute()
    }

    private fun bindSummary() {
        findViewById<ImageView>(R.id.imgAdminRouteIcon).apply {
            setImageResource(IncidentIcons.iconFor(report.type))
            setColorFilter(IncidentIcons.colorFor(report.type))
        }
        findViewById<TextView>(R.id.txtAdminRouteType).text = report.type
        styleBadge(
            findViewById(R.id.txtAdminRouteSeverity),
            report.severity.label, report.severity.text, report.severity.bg
        )
        findViewById<TextView>(R.id.txtAdminRouteLocation).text = report.location
    }

    // ---------------------------------------------------------------- planning (background)

    private fun compute() {
        Thread {
            val result: Result<Plan> = try {
                Result.success(buildPlan())
            } catch (e: RouteException) {
                Result.failure(e)
            } catch (e: Exception) {
                Log.e("AdminRoute", "Could not build the route", e)
                Result.failure(RouteException("Something went wrong: ${e.message}"))
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                findViewById<View>(R.id.layoutAdminRouteLoading).visibility = View.GONE
                result
                    .onSuccess { show(it) }
                    .onFailure { showError(it.message ?: "Could not build the route.") }
            }
        }.start()
    }

    private fun buildPlan(): Plan {
        if (report.lat == 0.0 && report.lng == 0.0) {
            throw RouteException("This report has no location, so a route can't be calculated.")
        }

        val data = RoutingDataLoader.load(this)

        val oLat = data.mdrrmoLat
        val oLng = data.mdrrmoLng
        if (oLat == null || oLng == null) {
            throw RouteException(
                "The MDRRMO office coordinates are missing. " +
                        "Fill in \"mdrrmo\" lat and lng in routing_data.json."
            )
        }

        val office = GeoPoint(oLat, oLng)
        val incident = GeoPoint(report.lat, report.lng)
        val notes = mutableListOf<String>()

        // Leg 1: MDRRMO -> incident
        val leg1 = OrsClient.directions(office, incident)

        if (report.type != ACCIDENT) {
            notes.add("The hospital route is only calculated for Road Accident reports.")
            return Plan(data.mdrrmoName, office, incident, leg1, null, null, notes)
        }

        // Leg 2: incident -> the nearest hospital that suits the severity
        val selection = RoutePlanner.select(data.hospitals, report.severity)
        if (selection.skippedNoLocation > 0) {
            notes.add(
                "${selection.skippedNoLocation} hospital(s) were skipped because they have " +
                        "no coordinates in routing_data.json yet."
            )
        }
        if (selection.downgraded) {
            notes.add(
                "No hospital in the list meets the CRITICAL requirement (confirmed ER and " +
                        "Level 2+, or ICU and OR). Showing the nearest hospital that meets the " +
                        "MODERATE requirement instead. Call ahead."
            )
        }
        if (selection.candidates.isEmpty()) {
            notes.add(
                "No hospital in the list meets the requirement for " +
                        "${report.severity.label} cases yet. Add coordinates or update the hospital data."
            )
            return Plan(data.mdrrmoName, office, incident, leg1, null, null, notes)
        }

        val costs = OrsClient.matrixCosts(
            incident,
            selection.candidates.map { GeoPoint(it.lat!!, it.lng!!) },
            OrsClient.BY_DISTANCE
        )
        var bestIndex = -1
        var best = Double.MAX_VALUE
        costs.forEachIndexed { i, c ->
            if (c != null && c < best) {
                best = c
                bestIndex = i
            }
        }
        if (bestIndex < 0) throw RouteException("No hospital could be reached by road from the incident site.")

        val hospital = selection.candidates[bestIndex]
        val leg2 = OrsClient.directions(incident, GeoPoint(hospital.lat!!, hospital.lng!!))
        if (hospital.er != "yes") {
            notes.add("The ER of ${hospital.name} is not confirmed. Call before sending a patient.")
        }
        return Plan(data.mdrrmoName, office, incident, leg1, hospital, leg2, notes)
    }

    // ---------------------------------------------------------------- showing the plan

    private fun show(p: Plan) {
        // Map: lines first, pins on top
        map.overlays.add(line(p.leg1.points, "#2563EB"))
        p.leg2?.let { map.overlays.add(line(it.points, "#16A34A")) }
        map.overlays.add(pin(p.office, "#2563EB", p.officeName))
        map.overlays.add(pin(p.incident, "#DC2626", report.type))
        p.hospital?.let { map.overlays.add(pin(GeoPoint(it.lat!!, it.lng!!), "#16A34A", it.name)) }
        map.invalidate()

        val all = p.leg1.points + (p.leg2?.points ?: emptyList())
        map.post {
            map.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(all), true, dp(48))
        }

        // Notes and warnings
        if (p.notes.isNotEmpty()) {
            findViewById<TextView>(R.id.txtAdminRouteWarning).apply {
                text = p.notes.joinToString("\n\n")
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(Color.parseColor("#FEF3C7"))
                    setStroke(dp(1), Color.parseColor("#FCD34D"))
                }
                visibility = View.VISIBLE
            }
        }

        // Leg 1
        findViewById<View>(R.id.cardAdminLeg1).visibility = View.VISIBLE
        findViewById<TextView>(R.id.txtAdminLeg1Stats).text = stats(p.leg1)
        findViewById<View>(R.id.btnAdminLeg1Navigate).setOnClickListener { navigate(p.office, p.incident) }

        // Leg 2
        val h = p.hospital
        val l2 = p.leg2
        if (h != null && l2 != null) {
            findViewById<View>(R.id.cardAdminLeg2).visibility = View.VISIBLE
            findViewById<TextView>(R.id.txtAdminLeg2Name).text = h.name
            findViewById<TextView>(R.id.txtAdminLeg2Sub).text = listOfNotNull(
                h.municipality.takeIf { it.isNotBlank() },
                if (h.kind == "infirmary") "Infirmary" else "Hospital",
                h.level?.let { "Level $it" },
                h.ownership.takeIf { it.isNotBlank() }?.replaceFirstChar { c -> c.uppercase() }
            ).joinToString(" · ")
            findViewById<TextView>(R.id.txtAdminLeg2Stats).text = stats(l2)

            val er = when (h.er) {
                "yes" -> "ER confirmed"
                "no" -> "No ER"
                else -> "ER not confirmed"
            }
            val services = if (h.services.isEmpty()) "" else " · ${h.services.joinToString(", ")}"
            val phones = if (h.phones.isEmpty()) "" else "\nPhone: ${h.phones.joinToString(" / ")}"
            findViewById<TextView>(R.id.txtAdminLeg2Services).text = er + services + phones

            findViewById<TextView>(R.id.txtAdminLeg2Note).apply {
                text = h.notes
                visibility = if (h.notes.isBlank()) View.GONE else View.VISIBLE
            }

            findViewById<View>(R.id.btnAdminLeg2Navigate).setOnClickListener {
                navigate(p.incident, GeoPoint(h.lat!!, h.lng!!))
            }
            findViewById<View>(R.id.btnAdminLeg2Call).apply {
                if (h.phones.isEmpty()) {
                    visibility = View.GONE
                } else {
                    visibility = View.VISIBLE
                    setOnClickListener { dial(h.phones.first()) }
                }
            }
        }
    }

    private fun showError(message: String) {
        findViewById<TextView>(R.id.txtAdminRouteError).apply {
            text = message
            visibility = View.VISIBLE
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun line(points: List<GeoPoint>, color: String) = Polyline().apply {
        setPoints(points)
        outlinePaint.color = Color.parseColor(color)
        outlinePaint.strokeWidth = 12f
    }

    private fun pin(point: GeoPoint, color: String, title: String) = Marker(map).apply {
        position = point
        this.title = title
        icon = mapPin(this@AdminRoute, Color.parseColor(color))
        setAnchor(0.5f, PIN_TIP_Y)
    }

    private fun stats(r: RouteResult): String {
        val km = r.distanceM / 1000.0
        val minutes = maxOf(1, (r.durationS / 60.0).roundToInt())
        val time = if (minutes >= 60) "${minutes / 60} hr ${minutes % 60} min" else "$minutes min"
        return String.format(Locale.getDefault(), "%.1f km · %s", km, time)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun dial(phone: String) {
        val uri = Uri.parse("tel:${phone.replace(" ", "")}")
        try {
            startActivity(Intent(Intent.ACTION_DIAL, uri))
        } catch (e: ActivityNotFoundException) {
            toast("No phone app found")
        }
    }

    private fun navigate(from: GeoPoint, to: GeoPoint) {
        val uri = Uri.parse(
            "https://www.google.com/maps/dir/?api=1" +
                    "&origin=${from.latitude},${from.longitude}" +
                    "&destination=${to.latitude},${to.longitude}" +
                    "&travelmode=driving"
        )
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            toast("No maps app found")
        }
    }
}