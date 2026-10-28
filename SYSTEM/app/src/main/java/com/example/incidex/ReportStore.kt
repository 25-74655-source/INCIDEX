package com.example.incidex

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Reports live in Firestore (collection "reports").
 * - A normal user only receives their own reports (the rules enforce this too).
 * - An admin receives all reports.
 * Screens call observe() and redraw whenever the list changes.
 */
object ReportStore {

    private const val TAG = "ReportStore"
    private const val COLLECTION = "reports"

    private var items: List<UserReport> = emptyList()
    private val listeners = mutableSetOf<() -> Unit>()
    private var registration: ListenerRegistration? = null
    private var startedUid: String? = null
    private var startedAsAdmin = false

    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    // ------------------------------------------------------------ start / stop

    /** Call after login (and on app start when someone is already signed in). */
    fun start(isAdmin: Boolean) {
        val uid = AuthRepository.currentUid() ?: return
        if (registration != null && uid == startedUid && isAdmin == startedAsAdmin) return

        registration?.remove()
        startedUid = uid
        startedAsAdmin = isAdmin

        val base = db.collection(COLLECTION)
        val query = if (isAdmin) base else base.whereEqualTo("reporterUid", uid)

        registration = query.addSnapshotListener { snap, error ->
            if (error != null) {
                Log.w(TAG, "Listening for reports failed", error)
                return@addSnapshotListener
            }
            items = snap?.documents
                ?.mapNotNull { it.toReport() }
                ?.sortedByDescending { it.createdAt }
                .orEmpty()
            notifyChanged()
        }
    }

    /** Call on logout. */
    fun stop() {
        registration?.remove()
        registration = null
        startedUid = null
        items = emptyList()
        notifyChanged()
    }

    // ------------------------------------------------------------ reading

    /** Only reports inside the Nasugbu boundary are ever returned. */
    fun all(): List<UserReport> =
        items.filter { NasugbuBoundary.contains(it.lat, it.lng) }

    fun find(id: String): UserReport? = items.find { it.id == id }

    /** Runs onChange now and every time the reports change, while the screen is visible. */
    fun observe(owner: LifecycleOwner, onChange: () -> Unit) {
        owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                listeners.add(onChange)
                onChange()
            }

            override fun onStop(owner: LifecycleOwner) {
                listeners.remove(onChange)
            }
        })
    }

    private fun notifyChanged() {
        listeners.toList().forEach { it() }
    }

    // ------------------------------------------------------------ writing

    /** Saves a new report. onResult(id, null) on success, onResult(null, message) on failure. */
    fun submit(
        type: String,
        severity: Severity,
        location: String,
        description: String,
        lat: Double,
        lng: Double,
        photoUrl: String,
        onResult: (String?, String?) -> Unit
    ) {
        val uid = AuthRepository.currentUid()
        if (uid == null) {
            onResult(null, "You are not signed in.")
            return
        }
        if (!NasugbuBoundary.contains(lat, lng)) {
            onResult(null, "That location is outside Nasugbu. Move the pin and try again.")
            return
        }
        val text = description.trim()
        if (text.length < 10) {
            onResult(null, "Please describe the incident in at least 10 characters.")
            return
        }
        if (text.length > 300) {
            onResult(null, "The description is too long (300 characters at most).")
            return
        }

        val id = newId()
        val data = hashMapOf<String, Any>(
            "reporterUid" to uid,
            "reporterName" to UserSession.name.ifBlank { "Guest" }.take(100),
            "isGuest" to AuthRepository.isGuest(),
            "type" to type,
            "severity" to severity.name,
            "status" to Status.SUBMITTED.name,
            "location" to location.take(200),
            "description" to text,
            "lat" to lat,
            "lng" to lng,
            "photoUrl" to photoUrl,
            "createdAt" to FieldValue.serverTimestamp()
        )

        db.collection(COLLECTION).document(id).set(data)
            .addOnSuccessListener { onResult(id, null) }
            .addOnFailureListener { onResult(null, AuthRepository.friendlyMessage(it)) }
    }

    /** Admin only. Sets the status (and the verified / resolved time). */
    fun setStatus(id: String, newStatus: Status, note: String, onResult: (String?) -> Unit) {
        val data = hashMapOf<String, Any>(
            "status" to newStatus.name,
            "adminNote" to note
        )
        if (newStatus == Status.VERIFIED) data["verifiedAt"] = FieldValue.serverTimestamp()
        if (newStatus == Status.RESOLVED) data["resolvedAt"] = FieldValue.serverTimestamp()

        db.collection(COLLECTION).document(id).update(data)
            .addOnSuccessListener { onResult(null) }
            .addOnFailureListener { onResult(AuthRepository.friendlyMessage(it)) }
    }

    /** Admin only. Saves just the note. */
    fun saveNote(id: String, note: String, onResult: (String?) -> Unit) {
        db.collection(COLLECTION).document(id).update("adminNote", note)
            .addOnSuccessListener { onResult(null) }
            .addOnFailureListener { onResult(AuthRepository.friendlyMessage(it)) }
    }

    // ------------------------------------------------------------ helpers

    private fun newId(): String {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val tail = (1..6).map { chars.random() }.joinToString("")
        return "INC-$year-$tail"
    }

    private fun DocumentSnapshot.millis(field: String): Long? =
        getTimestamp(field, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time

    private fun DocumentSnapshot.toReport(): UserReport? {
        val type = getString("type") ?: return null
        val lat = getDouble("lat") ?: return null
        val lng = getDouble("lng") ?: return null
        val severity = runCatching { Severity.valueOf(getString("severity").orEmpty()) }
            .getOrNull() ?: return null
        val status = runCatching { Status.valueOf(getString("status").orEmpty()) }
            .getOrNull() ?: return null

        val created = millis("createdAt") ?: System.currentTimeMillis()
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val dateFormat = SimpleDateFormat("MMM d, yyyy · hh:mm a", Locale.getDefault())

        return UserReport(
            id = id,
            type = type,
            severity = severity,
            status = status,
            location = getString("location").orEmpty(),
            dateTime = dateFormat.format(Date(created)),
            description = getString("description").orEmpty(),
            lat = lat,
            lng = lng,
            submittedTime = timeFormat.format(Date(created)),
            verifiedTime = millis("verifiedAt")?.let { timeFormat.format(Date(it)) },
            resolvedTime = millis("resolvedAt")?.let { timeFormat.format(Date(it)) },
            photoUrl = getString("photoUrl"),
            reporter = getString("reporterName").orEmpty(),
            adminNote = getString("adminNote").orEmpty(),
            createdAt = created
        )
    }
}