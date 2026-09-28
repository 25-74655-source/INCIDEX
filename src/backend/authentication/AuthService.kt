package backend.authentication

import backend.admin.Admin
import backend.users.User

class AuthService {

    private val users = mutableListOf<User>()
    private val admins = mutableListOf<Admin>()

    private var currentUser: User? = null
    private var currentAdmin: Admin? = null

    // Register a new user
    fun registerUser(user: User): Boolean {

        val emailExists = users.any {
            it.email.equals(user.email, ignoreCase = true)
        }

        if (emailExists) {
            return false
        }

        users.add(user)
        return true
    }

    // Register a new admin
    fun registerAdmin(admin: Admin): Boolean {

        val emailExists = admins.any {
            it.email.equals(admin.email, ignoreCase = true)
        }

        if (emailExists) {
            return false
        }

        admins.add(admin)
        return true
    }

    // user
    fun loginUser(email: String, password: String): Boolean {

        val user = users.find {
            it.email.equals(email, ignoreCase = true) &&
                    it.password == password
        }

        if (user != null) {
            currentUser = user
            currentAdmin = null
            return true
        }

        return false
    }

    // admin
    fun loginAdmin(email: String, password: String): Boolean {

        val admin = admins.find {
            it.email.equals(email, ignoreCase = true) &&
                    it.password == password
        }

        if (admin != null) {
            currentAdmin = admin
            currentUser = null
            return true
        }

        return false
    }

    // Logout
    fun logout() {
        currentUser = null
        currentAdmin = null
    }

    // currently logged-in user
    fun getCurrentUser(): User? {
        return currentUser
    }

    // currently logged-in admin
    fun getCurrentAdmin(): Admin? {
        return currentAdmin
    }

    // Check if someone is logged in
    fun isLoggedIn(): Boolean {
        return currentUser != null || currentAdmin != null
    }

    // current role
    fun getCurrentRole(): String? {

        return when {
            currentUser != null -> "user"
            currentAdmin != null -> "admin"
            else -> null
        }
    }
}