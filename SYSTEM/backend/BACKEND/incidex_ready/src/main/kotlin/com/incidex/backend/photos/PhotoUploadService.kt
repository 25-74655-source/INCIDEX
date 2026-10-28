package com.incidex.backend.photos

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.incidex.backend.common.FirebaseResult
import kotlinx.coroutines.tasks.await
import java.util.UUID

class PhotoUploadService(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    /** Uploads one image to reports/{reportId}/{uid}/{fileName}; returns its download URL. */
    suspend fun uploadEvidence(reportId: String, imageUri: Uri): FirebaseResult<String> {
        val uid = auth.currentUser?.uid
            ?: return FirebaseResult.Failure("Sign in before uploading evidence.")
        if (reportId.isBlank()) return FirebaseResult.Failure("Report ID is required.")
        return try {
            val fileName = "${UUID.randomUUID()}.jpg"
            val ref = storage.reference.child("reports/$reportId/$uid/$fileName")
            ref.putFile(imageUri).await()
            val url = ref.downloadUrl.await().toString()
            db.collection("reports").document(reportId).update(
                "photoUrls", com.google.firebase.firestore.FieldValue.arrayUnion(url),
                "updatedAt", com.google.firebase.Timestamp.now()
            ).await()
            FirebaseResult.Success(url)
        } catch (e: Exception) {
            FirebaseResult.Failure(e.localizedMessage ?: "Photo upload failed.", e)
        }
    }
}
