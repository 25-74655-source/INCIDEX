package com.example.incidex

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import java.io.FileNotFoundException

/**
 * The Nasugbu municipal boundary, loaded from app/src/main/assets/nasugbu_boundary.geojson.
 * Every "is this inside Nasugbu?" question in the app goes through contains().
 */
object NasugbuBoundary {

    const val ASSET_NAME = "nasugbu_boundary.geojson"
    private const val TAG = "NasugbuBoundary"

    // Guards against loading the wrong file (e.g. the whole province). Adjust if needed.
    private const val MAX_EXPECTED_SPAN_DEG = 0.6

    private const val MIN_ZOOM = 12.0
    private const val MAX_ZOOM = 19.0

    // Only a starting view. Whether it is inside is checked against the real polygon.
    val DEFAULT_CENTER = GeoPoint(14.0697, 120.6328)

    const val OUTSIDE_TITLE = "Location Outside Coverage"
    const val OUTSIDE_MESSAGE =
        "Incidex only accepts road incident reports within Nasugbu, Batangas. " +
                "Please select a location inside Nasugbu."

    private class Ring(val lat: DoubleArray, val lng: DoubleArray)
    private class Shape(val outer: Ring, val holes: List<Ring>)

    @Volatile private var shapes: List<Shape> = emptyList()

    @Volatile var bounds: BoundingBox? = null
        private set

    /** null = loaded fine. Otherwise the reason it could not be used (everything is blocked). */
    @Volatile var loadError: String? = "Boundary not loaded"
        private set

    val isLoaded: Boolean get() = loadError == null

    // ---------------------------------------------------------------- loading

    fun init(context: Context) {
        try {
            val text = context.applicationContext.assets.open(ASSET_NAME)
                .bufferedReader().use { it.readText() }
            val parsed = parse(text)
            val box = boundsOf(parsed)

            shapes = parsed
            bounds = box
            loadError = null

            val points = parsed.sumOf { s -> s.outer.lat.size + s.holes.sumOf { it.lat.size } }
            Log.i(
                TAG,
                "Loaded ${parsed.size} polygon(s), $points points. " +
                        "bbox N=${box.latNorth} S=${box.latSouth} E=${box.lonEast} W=${box.lonWest}"
            )
        } catch (e: FileNotFoundException) {
            fail("Boundary file missing. Add app/src/main/assets/$ASSET_NAME")
        } catch (e: Exception) {
            fail("Boundary file could not be used: ${e.message}")
        }
    }

    private fun fail(message: String) {
        shapes = emptyList()
        bounds = null
        loadError = message
        Log.e(TAG, message)
    }

    private fun parse(text: String): List<Shape> {
        val root = JSONObject(text)
        val geometries = mutableListOf<JSONObject>()

        fun consider(g: JSONObject?) {
            val type = g?.optString("type")
            if (g != null && (type == "Polygon" || type == "MultiPolygon")) geometries.add(g)
        }

        when (root.optString("type")) {
            "FeatureCollection" -> {
                val features = root.optJSONArray("features") ?: JSONArray()
                for (i in 0 until features.length()) {
                    consider(features.optJSONObject(i)?.optJSONObject("geometry"))
                }
            }
            "Feature" -> consider(root.optJSONObject("geometry"))
            else -> consider(root)
        }

        require(geometries.isNotEmpty()) { "No Polygon or MultiPolygon found in the file" }
        require(geometries.size == 1) {
            "The file has ${geometries.size} polygons. Keep only the Nasugbu municipality feature."
        }

        val geometry = geometries[0]
        val coordinates = geometry.getJSONArray("coordinates")
        val result = mutableListOf<Shape>()
        if (geometry.getString("type") == "Polygon") {
            result.add(polygon(coordinates))
        } else {
            for (i in 0 until coordinates.length()) result.add(polygon(coordinates.getJSONArray(i)))
        }

        val box = boundsOf(result)
        require(box.latNorth <= 90 && box.latSouth >= -90 && box.lonEast <= 180 && box.lonWest >= -180) {
            "Coordinates must be WGS84 degrees in [longitude, latitude] order"
        }
        require(
            box.latNorth - box.latSouth < MAX_EXPECTED_SPAN_DEG &&
                    box.lonEast - box.lonWest < MAX_EXPECTED_SPAN_DEG
        ) { "The polygon is larger than one municipality. Use only the Nasugbu boundary." }

        return result
    }

    private fun polygon(rings: JSONArray): Shape {
        val outer = ring(rings.getJSONArray(0))
        val holes = (1 until rings.length()).map { ring(rings.getJSONArray(it)) }
        return Shape(outer, holes)
    }

    private fun ring(points: JSONArray): Ring {
        val n = points.length()
        require(n >= 3) { "A ring has fewer than 3 points" }
        val lat = DoubleArray(n)
        val lng = DoubleArray(n)
        for (i in 0 until n) {
            val p = points.getJSONArray(i)
            lng[i] = p.getDouble(0)   // GeoJSON order is [longitude, latitude]
            lat[i] = p.getDouble(1)
        }
        return Ring(lat, lng)
    }

    private fun boundsOf(list: List<Shape>): BoundingBox {
        var north = -90.0
        var south = 90.0
        var east = -180.0
        var west = 180.0
        for (shape in list) {
            val r = shape.outer
            for (i in r.lat.indices) {
                if (r.lat[i] > north) north = r.lat[i]
                if (r.lat[i] < south) south = r.lat[i]
                if (r.lng[i] > east) east = r.lng[i]
                if (r.lng[i] < west) west = r.lng[i]
            }
        }
        return BoundingBox(north, east, south, west)
    }

    // ---------------------------------------------------------------- the check

    /** True only if the point is inside the real Nasugbu polygon. False if the data is not loaded. */
    fun contains(lat: Double, lng: Double): Boolean {
        if (lat.isNaN() || lng.isNaN()) return false
        val b = bounds ?: return false
        if (lat > b.latNorth || lat < b.latSouth || lng > b.lonEast || lng < b.lonWest) return false
        return shapes.any { s ->
            inRing(s.outer, lat, lng) && s.holes.none { inRing(it, lat, lng) }
        }
    }

    /** Ray casting: count how many polygon edges a ray going east from the point crosses. */
    private fun inRing(r: Ring, lat: Double, lng: Double): Boolean {
        var inside = false
        var j = r.lat.size - 1
        for (i in r.lat.indices) {
            val yi = r.lat[i]
            val yj = r.lat[j]
            val xi = r.lng[i]
            val xj = r.lng[j]
            if ((yi > lat) != (yj > lat) &&
                lng < (xj - xi) * (lat - yi) / (yj - yi) + xi
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    /** A point that is really inside the boundary, for opening the map and snapping back. */
    fun safeCenter(): GeoPoint {
        if (contains(DEFAULT_CENTER.latitude, DEFAULT_CENTER.longitude)) return DEFAULT_CENTER
        val b = bounds ?: return DEFAULT_CENTER

        val midLat = (b.latNorth + b.latSouth) / 2
        val midLng = (b.lonEast + b.lonWest) / 2
        if (contains(midLat, midLng)) return GeoPoint(midLat, midLng)

        // Odd shape: scan a grid and take the inside point closest to the middle
        var best: GeoPoint? = null
        var bestDist = Double.MAX_VALUE
        val steps = 40
        for (r in 0..steps) {
            for (c in 0..steps) {
                val lat = b.latSouth + (b.latNorth - b.latSouth) * r / steps
                val lng = b.lonWest + (b.lonEast - b.lonWest) * c / steps
                if (!contains(lat, lng)) continue
                val d = (lat - midLat) * (lat - midLat) + (lng - midLng) * (lng - midLng)
                if (d < bestDist) {
                    bestDist = d
                    best = GeoPoint(lat, lng)
                }
            }
        }
        return best ?: DEFAULT_CENTER
    }

    // ---------------------------------------------------------------- messages

    /** Returns true if the point is inside Nasugbu. Otherwise explains why not and returns false. */
    fun validateOrExplain(context: Context, lat: Double, lng: Double): Boolean {
        if (loadError != null) {
            showLoadErrorDialog(context)
            return false
        }
        if (!contains(lat, lng)) {
            showOutsideDialog(context)
            return false
        }
        return true
    }

    fun showOutsideDialog(context: Context) {
        AlertDialog.Builder(context)
            .setTitle(OUTSIDE_TITLE)
            .setMessage(OUTSIDE_MESSAGE)
            .setPositiveButton("OK", null)
            .show()
    }

    fun showLoadErrorDialog(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("Coverage data missing")
            .setMessage(
                "${loadError ?: "Boundary not loaded"}\n\n" +
                        "Reports are blocked until the Nasugbu boundary file is added."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    // ---------------------------------------------------------------- map helpers

    /**
     * Limits zoom and panning to Nasugbu, draws the boundary outline, and brings the map
     * back if its center leaves the polygon. Call after setTileSource().
     */
    fun applyToMap(map: MapView) {
        map.setMinZoomLevel(MIN_ZOOM)
        map.setMaxZoomLevel(MAX_ZOOM)
        bounds?.let { map.setScrollableAreaLimitDouble(it) }
        addOutline(map)
        watchCamera(map)
    }

    private fun addOutline(map: MapView) {
        shapes.forEach { shape ->
            val outline = Polygon().apply {
                setPoints(shape.outer.lat.indices.map { GeoPoint(shape.outer.lat[it], shape.outer.lng[it]) })
                fillPaint.color = Color.TRANSPARENT
                outlinePaint.color = Color.parseColor("#2563EB")
                outlinePaint.strokeWidth = 4f
            }
            map.overlays.add(0, outline)   // at the bottom, so markers and taps stay on top
        }
    }

    private fun watchCamera(map: MapView) {
        if (bounds == null) return

        val handler = Handler(Looper.getMainLooper())
        val check = Runnable {
            if (!map.isAttachedToWindow) return@Runnable
            val c = map.mapCenter
            if (!contains(c.latitude, c.longitude)) {
                map.controller.animateTo(safeCenter())
                Toast.makeText(
                    map.context,
                    "Incidex covers Nasugbu, Batangas only.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // Wait until the user stops moving the map, so the snap-back doesn't fight the gesture
        map.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                handler.removeCallbacks(check)
                handler.postDelayed(check, 600)
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                handler.removeCallbacks(check)
                handler.postDelayed(check, 600)
                return false
            }
        })
    }
}