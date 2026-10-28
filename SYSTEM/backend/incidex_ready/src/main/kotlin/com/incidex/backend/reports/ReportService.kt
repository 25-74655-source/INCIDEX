package com.incidex.backend.reports

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.incidex.backend.common.FirebaseResult
import com.incidex.backend.models.IncidentReport
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ReportService(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    suspend fun submitReport(
        category: String,
        severity: String,
        description: String,
        reporterId: String,
        reporterEmail: String,
        latitude: Double? = null,
        longitude: Double? = null,
        address: String = "",
        photoUrls: List<String> = emptyList()
    ): FirebaseResult<IncidentReport> {
        if (category.isBlank() || description.isBlank() || reporterId.isBlank()) {
            return FirebaseResult.Failure("Category, description, and signed-in reporter are required.")
        }
        return try {
            val id = UUID.randomUUID().toString()
            val now = Timestamp.now()
            val report = IncidentReport(
                reportId = id, category = category.trim(), severity = severity,
                description = description.trim(), photoUrls = photoUrls,
                latitude = latitude, longitude = longitude, address = address,
                reporterId = reporterId, reporterEmail = reporterEmail,
                status = "Submitted", createdAt = now, updatedAt = now
            )
            db.collection("reports").document(id).set(report).await()
            FirebaseResult.Success(report)
        } catch (e: Exception) {
            FirebaseResult.Failure(e.localizedMessage ?: "Could not submit report.", e)
        }
    }

    suspend fun getMyReports(uid: String): FirebaseResult<List<IncidentReport>> = try {
        val snap = db.collection("reports").whereEqualTo("reporterId", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING).get().await()
        FirebaseResult.Success(snap.documents.mapNotNull { it.toObject(IncidentReport::class.java) })
    } catch (e: Exception) {
        FirebaseResult.Failure(e.localizedMessage ?: "Could not load your reports.", e)
    }

    suspend fun getReport(reportId: String): FirebaseResult<IncidentReport> = try {
        val snap = db.collection("reports").document(reportId).get().await()
        val report = snap.toObject(IncidentReport::class.java)
            ?: return FirebaseResult.Failure("Report was not found.")
        FirebaseResult.Success(report)
    } catch (e: Exception) {
        FirebaseResult.Failure(e.localizedMessage ?: "Could not load report.", e)
    }
}
