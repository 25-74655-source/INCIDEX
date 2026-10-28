package com.example.incidex

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class Register : AppCompatActivity() {

    private var selectedRole = ""

    private val handler = Handler(Looper.getMainLooper())
    private var poll: Runnable? = null
    private var verifyDialog: AlertDialog? = null
    private var completing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.register)

        val edtName = findViewById<EditText>(R.id.edtName)
        val edtEmail = findViewById<EditText>(R.id.edtEmail)
        val edtPassword = findViewById<EditText>(R.id.edtPassword)
        val edtConfirmPassword = findViewById<EditText>(R.id.edtConfirmPassword)

        edtPassword.enablePasswordToggle()
        edtConfirmPassword.enablePasswordToggle()

        val btnUser = findViewById<Button>(R.id.btnRegisterUser)
        val btnAdmin = findViewById<Button>(R.id.btnRegisterAdmin)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

        val txtBackToLogin = findViewById<TextView>(R.id.txtBackToLogin)
        val txtLogin = findViewById<TextView>(R.id.txtLogin)

        fun selectRole(role: String) {
            selectedRole = role
            btnUser.isSelected = role == "User"
            btnAdmin.isSelected = role == "Admin"
        }

        // User is the only role that can be registered from the app
        selectRole("User")

        btnUser.setOnClickListener { selectRole("User") }
        btnAdmin.setOnClickListener {
            Toast.makeText(
                this,
                "Admin accounts are created by the system administrator.",
                Toast.LENGTH_LONG
            ).show()
        }

        btnRegister.setOnClickListener {

            val name = edtName.text.toString().trim()
            val email = edtEmail.text.toString().trim()
            val password = edtPassword.text.toString()
            val confirmPassword = edtConfirmPassword.text.toString()

            if (name.isEmpty()) {
                edtName.error = "Enter your full name"
                edtName.requestFocus()
                return@setOnClickListener
            }

            if (email.isEmpty()) {
                edtEmail.error = "email@gmail.com"
                edtEmail.requestFocus()
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                edtEmail.error = "Enter a valid email"
                edtEmail.requestFocus()
                return@setOnClickListener
            }

            if (password.isEmpty()) {
                edtPassword.error = "Create a password"
                edtPassword.requestFocus()
                return@setOnClickListener
            }

            if (password.length < 6) {
                edtPassword.error = "Password must be at least 6 characters"
                edtPassword.requestFocus()
                return@setOnClickListener
            }

            if (confirmPassword.isEmpty()) {
                edtConfirmPassword.error = "Confirm your password"
                edtConfirmPassword.requestFocus()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                edtConfirmPassword.error = "Passwords do not match"
                edtConfirmPassword.requestFocus()
                return@setOnClickListener
            }

            btnRegister.isEnabled = false
            AuthRepository.startRegistration(email, password) { error ->
                btnRegister.isEnabled = true

                if (error != null) {
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                    return@startRegistration
                }
                showVerifyDialog(name, email)
            }
        }

        txtBackToLogin.setOnClickListener { goToLogin() }
        txtLogin.setOnClickListener { goToLogin() }
    }

    // ---------------------------------------------------------------- email verification

    /** Waits here until the user clicks the link in the email. The account is only saved after that. */
    private fun showVerifyDialog(name: String, email: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Verify your email")
            .setMessage(
                "We sent a verification link to $email.\n\n" +
                        "Open it, then come back here. This screen continues by itself. " +
                        "Check your spam folder too."
            )
            .setCancelable(false)
            .setPositiveButton("I've verified", null)
            .setNeutralButton("Resend email", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
        verifyDialog = dialog

        // Set the click listeners after show() so the dialog stays open after a tap
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            checkNow(name, manual = true)
        }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            AuthRepository.resendVerification { error ->
                Toast.makeText(
                    this,
                    error ?: "Verification email sent to $email",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            stopPolling()
            AuthRepository.cancelRegistration()   // removes the unverified account
            dialog.dismiss()
            verifyDialog = null
        }

        startPolling(name)
    }

    private fun startPolling(name: String) {
        stopPolling()
        val task = object : Runnable {
            override fun run() {
                checkNow(name, manual = false)
                handler.postDelayed(this, 3000)
            }
        }
        poll = task
        handler.postDelayed(task, 3000)
    }

    private fun stopPolling() {
        poll?.let { handler.removeCallbacks(it) }
        poll = null
    }

    private fun checkNow(name: String, manual: Boolean) {
        if (completing) return
        AuthRepository.checkVerified { verified, error ->
            when {
                verified -> complete(name)
                manual -> Toast.makeText(
                    this,
                    error ?: "Not verified yet. Open the link in the email first.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun complete(name: String) {
        if (completing) return
        completing = true
        stopPolling()

        AuthRepository.finishRegistration(name) { error ->
            completing = false
            verifyDialog?.dismiss()
            verifyDialog = null
            if (isFinishing || isDestroyed) return@finishRegistration

            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                return@finishRegistration
            }
            Toast.makeText(this, "Email verified. Please log in.", Toast.LENGTH_LONG).show()
            goToLogin()
        }
    }

    private fun goToLogin() {
        startActivity(Intent(this, Login::class.java))
        finish()
    }

    override fun onDestroy() {
        stopPolling()
        verifyDialog?.dismiss()
        super.onDestroy()
    }
}