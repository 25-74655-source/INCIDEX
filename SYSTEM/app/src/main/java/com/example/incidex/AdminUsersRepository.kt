package com.example.incidex

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

data class AppUser(
    val uid: String,
    val name: String,
    val email: String,
    val role: String,
    val createdAt: Long?     // milliseconds; null if unknown
)

/** What the admin Users screen can do. The rules decide who is allowed (admins only). */
object AdminUsersRepository {

    private const val SECONDARY_APP = "adminCreateUser"

    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    /** Live list of every profile in the users collection (guests have none). */
    fun listen(onChange: (List<AppUser>) -> Unit, onError: (String) -> Unit): ListenerRegistration =
        db.collection("users").addSnapshotListener { snap, e ->
            if (snap == null) {
                onError(AuthRepository.friendlyMessage(e ?: Exception("Could not load users")))
                return@addSnapshotListener
            }
            onChange(snap.documents.map { d ->
                AppUser(
                    uid = d.id,
                    name = d.getString("name").orEmpty(),
                    email = d.getString("email").orEmpty(),
                    role = d.getString("role") ?: AuthRepository.ROLE_USER,
                    createdAt = d.getTimestamp(
                        "createdAt",
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )?.toDate()?.time
                )
            })
        }

    /**
     * Creates a normal user. A second Firebase connection is used so the admin
     * stays logged in (creating an account normally signs in as the new account).
     */
    fun addUser(
        context: Context,
        name: String,
        email: String,
        password: String,
        onResult: (String?) -> Unit
    ) {
        val secondary = try {
            FirebaseApp.getInstance(SECONDARY_APP)
        } catch (e: IllegalStateException) {
            FirebaseApp.initializeApp(
                context.applicationContext,
                FirebaseApp.getInstance().options,
                SECONDARY_APP
            )
        }
        val secAuth = FirebaseAuth.getInstance(secondary)
        val secDb = FirebaseFirestore.getInstance(secondary)

        secAuth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user == null) {
                    secAuth.signOut()
                    onResult("Could not create the account. Please try again.")
                    return@addOnSuccessListener
                }

                val data = hashMapOf(
                    "name" to name,
                    "email" to email,
                    "role" to AuthRepository.ROLE_USER,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                // Written as the new user themselves, which the rules allow for role = user
                secDb.collection("users").document(user.uid).set(data)
                    .addOnSuccessListener {
                        secAuth.signOut()
                        onResult(null)
                    }
                    .addOnFailureListener { e ->
                        user.delete()
                        secAuth.signOut()
                        onResult(AuthRepository.friendlyMessage(e))
                    }
            }
            .addOnFailureListener { onResult(AuthRepository.friendlyMessage(it)) }
    }

    fun updateName(uid: String, name: String, onResult: (String?) -> Unit) {
        db.collection("users").document(uid).update("name", name)
            .addOnSuccessListener { onResult(null) }
            .addOnFailureListener { onResult(AuthRepository.friendlyMessage(it)) }
    }

    /** Removes the profile. Without a profile the account is signed out and cannot log in. */
    fun deleteUser(uid: String, onResult: (String?) -> Unit) {
        db.collection("users").document(uid).delete()
            .addOnSuccessListener { onResult(null) }
            .addOnFailureListener { onResult(AuthRepository.friendlyMessage(it)) }
    }
}