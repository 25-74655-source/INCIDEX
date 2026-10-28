package com.incidex.backend.users

import com.google.firebase.firestore.FirebaseFirestore
import com.incidex.backend.common.FirebaseResult
import com.incidex.backend.models.UserProfile
import kotlinx.coroutines.tasks.await

class UserProfileService(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    suspend fun getProfile(uid: String): FirebaseResult<UserProfile> = try {
        val snap = db.collection("users").document(uid).get().await()
        val profile = snap.toObject(UserProfile::class.java)
            ?: return FirebaseResult.Failure("User profile was not found.")
        FirebaseResult.Success(profile)
    } catch (e: Exception) {
        FirebaseResult.Failure(e.localizedMessage ?: "Could not load profile.", e)
    }

    suspend fun updateName(uid: String, name: String): FirebaseResult<Boolean> {
        if (name.isBlank()) return FirebaseResult.Failure("Name cannot be empty.")
        return try {
            db.collection("users").document(uid).update("name", name.trim()).await()
            FirebaseResult.Success(true)
        } catch (e: Exception) {
            FirebaseResult.Failure(e.localizedMessage ?: "Could not update profile.", e)
        }
    }
}
