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
import java.util.Calendar

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
                Toast.makeText(
                    this,
                    "Please select User or Admin",
                    Toast.LENGTH_SHORT
                ).show()
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

            if (selectedRole == "User") {
                // temporary until we have real accounts
                UserSession.name = "Juan dela Cruz"
                UserSession.email = email
                UserSession.phone = "09171234567"

                startActivity(Intent(this, UserHome::class.java))
                finish()
            } else {
                Toast.makeText(
                    this,
                    "Admin side coming soon",
                    Toast.LENGTH_SHORT
                ).show()
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

data class ReportType(val icon: String = "", val name: String, val quickLabel: String)

val REPORT_TYPES = listOf(
    ReportType(name = "Road Accident", quickLabel = "Accident"),
    ReportType(name = "Traffic", quickLabel = "Traffic"),
    ReportType(name = "Road Hazard", quickLabel = "Hazard"),
    ReportType(name = "Flooding", quickLabel = "Flooding"),
    ReportType(name = "Road Closure", quickLabel = "Closure"),
    ReportType(name = "Other", quickLabel = "Other")
)

// Pansamantalang sagot habang ginagawa ang report (hindi pa naka-save)
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

// Temporary in-memory list. Papalitan ng Firebase later.
object ReportStore {

    private var counter = 35

    private val items = mutableListOf(
        UserReport(
            id = "INC-2024-0031",
            type = "Road Accident",
            severity = Severity.CRITICAL,
            status = Status.VERIFIED,
            location = "Brgy. 4, Nasugbu, Batangas",
            dateTime = "Sep 6, 2026 · 09:14 AM",
            description = "Vehicle collision near the Wawa junction. Two motorcycles involved, one lane blocked.",
            lat = 14.0745,
            lng = 120.6310,
            submittedTime = "09:14 AM",
            verifiedTime = "09:28 AM"
        ),
        UserReport(
            id = "INC-2024-0032",
            type = "Flooding",
            severity = Severity.MODERATE,
            status = Status.SUBMITTED,
            location = "Brgy. Tumalim, Nasugbu, Batangas",
            dateTime = "Oct 4, 2026 · 08:52 AM",
            description = "Knee-deep flood on the main road, vehicles slowing down.",
            lat = 14.0690,
            lng = 120.6360,
            submittedTime = "08:52 AM"
        ),
        UserReport(
            id = "INC-2024-0033",
            type = "Traffic",
            severity = Severity.MODERATE,
            status = Status.VERIFIED,
            location = "Brgy 6, Nasugbu Batangas",
            dateTime = "Sep 4, 2026 · 08:10 AM",
            description = "Heavy traffic near the public market due to road works.",
            lat = 14.0770,
            lng = 120.6400,
            submittedTime = "08:10 AM",
            verifiedTime = "08:25 AM"
        ),
        UserReport(
            id = "INC-2024-0034",
            type = "Road Hazard",
            severity = Severity.LOW,
            status = Status.RESOLVED,
            location = "Brgy. Bucana, Nasugbu, Batangas",
            dateTime = "Sep 4, 2026 · 06:20 AM",
            description = "Fallen tree branch on the shoulder of the road.",
            lat = 14.0420,
            lng = 120.6560,
            submittedTime = "06:20 AM",
            verifiedTime = "06:40 AM",
            resolvedTime = "07:30 AM"
        )
    )

    fun all(): List<UserReport> = items.toList()

    fun add(report: UserReport) {
        items.add(0, report) // pinakabago sa taas
    }

    fun nextId(): String {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        return "INC-$year-${counter++.toString().padStart(4, '0')}"
    }
}

// Nilo-load ang photo nang maliit para hindi mag-out-of-memory
fun loadBitmapScaled(path: String, maxSize: Int = 1024): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxSize || bounds.outHeight / sample > maxSize) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}