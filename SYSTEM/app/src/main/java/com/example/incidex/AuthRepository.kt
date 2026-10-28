package com.example.incidex

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException

/** Everything about accounts goes through here (Firebase Auth + the users collection). */
object AuthRepository {

    const val ROLE_USER = "user"
    const val ROLE_ADMIN = "admin"

    class Profile(val uid: String, val name: String, val email: String, val role: String)

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    fun currentUid(): String? = auth.currentUser?.uid

    /** True for someone using the app without an account (anonymous sign-in). */
    fun isGuest(): Boolean = auth.currentUser?.isAnonymous == true

    // ------------------------------------------------------------ login

    /** onResult(profile, null) on success, onResult(null, message) on failure. */
    fun signIn(email: String, password: String, onResult: (Profile?, String?) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user == null) {
                    onResult(null, "Sign in failed. Please try again.")
                    return@addOnSuccessListener
                }
                loadProfile(user.uid, user.email.orEmpty(), user.displayName, true, onResult)
            }
            .addOnFailureListener { onResult(null, friendlyMessage(it)) }
    }

    /**
     * For app launch: if someone is already signed in with an account (not a guest),
     * loads their profile. Gives null for "nobody" or "guest". Never signs anyone out,
     * so being offline at launch doesn't lose the session.
     */
    fun restoreSession(onResult: (Profile?) -> Unit) {
        val user = auth.currentUser
        if (user == null || user.isAnonymous) {
            onResult(null)
            return
        }
        loadProfile(user.uid, user.email.orEmpty(), user.displayName, false) { profile, _ ->
            onResult(profile)
        }
    }

    private fun loadProfile(
        uid: String,
        email: String,
        displayName: String?,
        signOutOnFailure: Boolean,
        onResult: (Profile?, String?) -> Unit
    ) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    // The account has no profile (for example a registration that was never finished)
                    if (signOutOnFailure) auth.signOut()
                    onResult(
                        null,
                        "This account has no profile. Please register again, or ask the administrator."
                    )
                    return@addOnSuccessListener
                }
                val name = doc.getString("name")?.takeIf { it.isNotBlank() }
                    ?: displayName?.takeIf { it.isNotBlank() }
                    ?: email.substringBefore("@")
                val role = doc.getString("role") ?: ROLE_USER
                onResult(Profile(uid, name, email, role), null)
            }
            .addOnFailureListener {
                if (signOutOnFailure) auth.signOut()
                onResult(null, friendlyMessage(it))
            }
    }

    /** Makes sure someone is signed in (guests get an anonymous account). null = OK, else an error message. */
    fun ensureSignedIn(onResult: (String?) -> Unit) {
        if (auth.currentUser != null) {
            onResult(null)
            return
        }
        auth.signInAnonymously()
            .addOnSuccessListener { onResult(null) }
            .addOnFailureListener { onResult(friendlyMessage(it)) }
    }

    // ------------------------------------------------------------ registration (3 steps)
    //  1. startRegistration  : creates the account and emails the verification link
    //  2. checkVerified      : the Register screen asks this until the link was clicked
    //  3. finishRegistration : saves the profile in Firestore and signs out
    //  cancelRegistration    : the user gave up, so the unverified account is removed

    /** Creates the account and sends the verification email. The user stays signed in until step 3. */
    fun startRegistration(email: String, password: String, onResult: (String?) -> Unit) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user == null) {
                    onResult("Registration failed. Please try again.")
                    return@addOnSuccessListener
                }
                user.sendEmailVerification()
                    .addOnSuccessListener { onResult(null) }
                    .addOnFailureListener {
                        user.delete()
                        auth.signOut()
                        onResult(friendlyMessage(it))
                    }
            }
            .addOnFailureListener { onResult(friendlyMessage(it)) }
    }

    /** onResult(verified, errorMessage). */
    fun checkVerified(onResult: (Boolean, String?) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onResult(false, "Registration was interrupted. Please register again.")
            return
        }
        user.reload()
            .addOnSuccessListener { onResult(auth.currentUser?.isEmailVerified == true, null) }
            .addOnFailureListener { onResult(false, friendlyMessage(it)) }
    }

    /** Sends the verification email again to the account being registered. null = sent. */
    fun resendVerification(onResult: (String?) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onResult("Registration was interrupted. Please register again.")
            return
        }
        user.sendEmailVerification()
            .addOnSuccessListener { onResult(null) }
            .addOnFailureListener { onResult(friendlyMessage(it)) }
    }

    /** Saves the profile (role is always "user") and signs out. null = done, else an error message. */
    fun finishRegistration(name: String, onResult: (String?) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onResult("Registration was interrupted. Please register again.")
            return
        }

        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build())

        val data = hashMapOf(
            "name" to name,
            "email" to user.email.orEmpty(),
            "role" to ROLE_USER,
            "createdAt" to FieldValue.serverTimestamp()
        )
        db.collection("users").document(user.uid).set(data)
            .addOnSuccessListener {
                auth.signOut()          // the user logs in from the Login screen
                onResult(null)
            }
            .addOnFailureListener { e ->
                user.delete()           // don't leave a half-created account behind
                auth.signOut()
                onResult(friendlyMessage(e))
            }
    }

    /** The user gave up before verifying: remove the account so the email can be used again. */
    fun cancelRegistration() {
        val user = auth.currentUser
        if (user != null && !user.isAnonymous) user.delete()
        auth.signOut()
    }

    // ------------------------------------------------------------ passwords

    /**
     * Sends the reset email. An unknown email is reported as success on purpose,
     * so the screen never reveals which emails have accounts.
     */
    fun sendReset(email: String, onResult: (String?) -> Unit) {
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { onResult(null) }
            .addOnFailureListener {
                if (it is FirebaseAuthInvalidUserException) onResult(null)
                else onResult(friendlyMessage(it))
            }
    }

    /** Checks the current password again, then sets the new one. */
    fun changePassword(
        current: String,
        newPassword: String,
        onResult: (String?, Boolean) -> Unit   // (error message, currentPasswordWasWrong)
    ) {
        val user = auth.currentUser
        val email = user?.email
        if (user == null || email == null) {
            onResult("You are not signed in. Please log in again.", false)
            return
        }

        user.reauthenticate(EmailAuthProvider.getCredential(email, current))
            .addOnSuccessListener {
                user.updatePassword(newPassword)
                    .addOnSuccessListener { onResult(null, false) }
                    .addOnFailureListener { onResult(friendlyMessage(it), false) }
            }
            .addOnFailureListener {
                val wrong = it is FirebaseAuthInvalidCredentialsException ||
                        it is FirebaseAuthInvalidUserException
                onResult(if (wrong) "Current password is incorrect" else friendlyMessage(it), wrong)
            }
    }

    fun signOut() {
        auth.signOut()
    }

    fun friendlyMessage(e: Exception): String = when {
        e is FirebaseNetworkException ->
            "No internet connection. Please try again."
        e is FirebaseTooManyRequestsException ->
            "Too many attempts. Please wait a moment and try again."
        // WeakPassword is a kind of InvalidCredentials, so it must be checked first
        e is FirebaseAuthWeakPasswordException ->
            "Password is too weak. Use at least 6 characters."
        e is FirebaseAuthUserCollisionException ->
            "An account with this email already exists."
        e is FirebaseAuthInvalidUserException && e.errorCode == "ERROR_USER_DISABLED" ->
            "This account has been disabled."
        e is FirebaseAuthInvalidUserException || e is FirebaseAuthInvalidCredentialsException ->
            "Incorrect email or password."
        e is FirebaseAuthException && e.errorCode == "ERROR_OPERATION_NOT_ALLOWED" ->
            "This sign-in method is not enabled in Firebase (Authentication > Sign-in method)."
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Permission denied by the database rules. Publish the Firestore rules first."
        else -> e.localizedMessage ?: "Something went wrong. Please try again."
    }
}