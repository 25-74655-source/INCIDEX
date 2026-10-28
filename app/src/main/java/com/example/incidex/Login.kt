package com.example.incidex

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class Login : AppCompatActivity() {

    private var selectedRole = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.login)

        val txtBack = findViewById<TextView>(R.id.txtBack)

        val edtEmail = findViewById<EditText>(R.id.edtEmail)
        val edtPassword = findViewById<EditText>(R.id.edtPassword)

        // Show / hide password (eye icon)
        edtPassword.enablePasswordToggle()

        val btnLogin = findViewById<Button>(R.id.btnLoginAccount)

        val btnUser = findViewById<Button>(R.id.btnUser)
        val btnAdmin = findViewById<Button>(R.id.btnAdmin)

        val txtForgotPassword = findViewById<TextView>(R.id.txtForgotPassword)
        val txtRegister = findViewById<TextView>(R.id.txtRegister)

        // Role selection: the selector drawable handles the colors
        fun selectRole(role: String) {
            selectedRole = role
            btnUser.isSelected = role == "User"
            btnAdmin.isSelected = role == "Admin"
        }

        // Back to Main Page
        txtBack.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        btnUser.setOnClickListener { selectRole("User") }
        btnAdmin.setOnClickListener { selectRole("Admin") }

        // Login
        btnLogin.setOnClickListener {

            val email = edtEmail.text.toString().trim()
            val password = edtPassword.text.toString().trim()

            if (selectedRole.isEmpty()) {
                Toast.makeText(this, "Please select User or Admin", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (email.isEmpty()) {
                edtEmail.error = "Enter your email"
                edtEmail.requestFocus()
                return@setOnClickListener
            }

            if (password.isEmpty()) {
                edtPassword.error = "Enter your password"
                edtPassword.requestFocus()
                return@setOnClickListener
            }

            btnLogin.isEnabled = false
            AuthRepository.signIn(email, password) { profile, error ->
                btnLogin.isEnabled = true

                if (profile == null) {
                    Toast.makeText(this, error ?: "Login failed", Toast.LENGTH_LONG).show()
                    return@signIn
                }

                val isAdmin = profile.role == AuthRepository.ROLE_ADMIN

                if ((selectedRole == "Admin") != isAdmin) {
                    AuthRepository.signOut()
                    Toast.makeText(
                        this,
                        if (selectedRole == "Admin") "This account is not an administrator."
                        else "This is an admin account. Select Admin to log in.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@signIn
                }

                ReportStore.start(isAdmin)

                if (isAdmin) {
                    AdminSession.name = profile.name
                    AdminSession.email = profile.email
                    startActivity(Intent(this, AdminHome::class.java))
                } else {
                    UserSession.name = profile.name
                    UserSession.email = profile.email
                    startActivity(Intent(this, UserHome::class.java))
                }
                finish()
            }
        }

        // Forgot Password
        txtForgotPassword.setOnClickListener {
            startActivity(
                Intent(this, ForgotPasswordActivity::class.java)
                    .putExtra(ForgotPasswordActivity.EXTRA_EMAIL, edtEmail.text.toString().trim())
            )
        }

        // Register
        txtRegister.setOnClickListener {
            startActivity(Intent(this, Register::class.java))
        }
    }
}

// `icon` is unused now (icons come from IncidentIcons); kept so other code still compiles.
data class ReportType(val icon: String = "", val name: String, val quickLabel: String)

val REPORT_TYPES = listOf(
    ReportType(name = "Road Accident", quickLabel = "Accident"),
    ReportType(name = "Traffic", quickLabel = "Traffic"),
    ReportType(name = "Road Hazard", quickLabel = "Hazard"),
    ReportType(name = "Flooding", quickLabel = "Flooding"),
    ReportType(name = "Road Closure", quickLabel = "Closure"),
    ReportType(name = "Other", quickLabel = "Other")
)

class ReportDraft {
    var lat: Double? = null
    var lng: Double? = null
    var address: String = ""
    var pinTime: String = ""
    var type: ReportType? = null
    var severity: Severity? = null
    var description: String = ""
    var photoPath: String? = null
}

// Nilo-load ang photo nang maliit para hindi mag-out-of-memory
fun loadBitmapScaled(path: String, maxSize: Int = 1024): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxSize || bounds.outHeight / sample > maxSize) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}