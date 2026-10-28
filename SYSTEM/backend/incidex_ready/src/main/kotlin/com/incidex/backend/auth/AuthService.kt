package com.incidex.backend.auth

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.incidex.backend.common.FirebaseResult
import com.incidex.backend.models.UserProfile
import kotlinx.coroutines.tasks.await

class AuthService(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun register(name: String, email: String, password: String): FirebaseResult<UserProfile> {
        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            return FirebaseResult.Failure("Name, email, and password are required.")
        }
        if (password.length < 6) return FirebaseResult.Failure("Password must be at least 6 characters.")
        return try {
            val credential = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            val user = credential.user ?: return FirebaseResult.Failure("Firebase did not return a user.")
            val profile = UserProfile(
                uid = user.uid,
                name = name.trim(),
                email = user.email ?: email.trim(),
                role = "user",
                createdAt = Timestamp.now()
            )
            db.collection("users").document(user.uid).set(profile).await()
            FirebaseResult.Success(profile)
        } catch (e: Exception) {
            FirebaseResult.Failure(e.localizedMessage ?: "Registration failed.", e)
        }
    }

    suspend fun login(email: String, password: String): FirebaseResult<UserProfile> = try {
        val credential = auth.signInWithEmailAndPassword(email.trim(), password).await()
        val user = credential.user ?: return FirebaseResult.Failure("Login failed.")
        val snapshot = db.collection("users").document(user.uid).get().await()
        val profile = snapshot.toObject(UserProfile::class.java)
            ?: UserProfile(uid = user.uid, email = user.email.orEmpty())
        FirebaseResult.Success(profile)
    } catch (e: Exception) {
        FirebaseResult.Failure(e.localizedMessage ?: "Login failed.", e)
    }

    fun logout() = auth.signOut()
    fun currentUid(): String? = auth.currentUser?.uid
    fun isSignedIn(): Boolean = auth.currentUser != null
}
