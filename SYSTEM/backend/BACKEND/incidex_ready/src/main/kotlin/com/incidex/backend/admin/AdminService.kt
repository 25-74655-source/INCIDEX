package com.incidex.backend.admin

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.incidex.backend.common.FirebaseResult
import com.incidex.backend.models.IncidentReport
import kotlinx.coroutines.tasks.await

class AdminService(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun isCurrentUserAdmin(): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        return try {
            db.collection("users").document(uid).get().await().getString("role") == "admin"
        } catch (_: Exception) { false }
    }

    suspend fun getAllReports(): FirebaseResult<List<IncidentReport>> {
        if (!isCurrentUserAdmin()) return FirebaseResult.Failure("Admin access required.")
        return try {
            val snap = db.collection("reports").orderBy("createdAt", Query.Direction.DESCENDING).get().await()
            FirebaseResult.Success(snap.documents.mapNotNull { it.toObject(IncidentReport::class.java) })
        } catch (e: Exception) {
            FirebaseResult.Failure(e.localizedMessage ?: "Could not load reports.", e)
        }
    }

    suspend fun updateReportStatus(reportId: String, status: String, adminNote: String = ""): FirebaseResult<Boolean> {
        if (!isCurrentUserAdmin()) return FirebaseResult.Failure("Admin access required.")
        val allowed = setOf("Submitted", "Under Review", "Verified", "In Progress", "Resolved", "Rejected")
        if (status !in allowed) return FirebaseResult.Failure("Unsupported report status.")
        return try {
            db.collection("reports").document(reportId).update(
                mapOf("status" to status, "adminNote" to adminNote, "updatedAt" to Timestamp.now())
            ).await()
            FirebaseResult.Success(true)
        } catch (e: Exception) {
            FirebaseResult.Failure(e.localizedMessage ?: "Could not update report.", e)
        }
    }
}
