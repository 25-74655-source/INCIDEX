package com.example.incidex

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class Register : AppCompatActivity() {

    private var selectedRole = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.register)

        val edtName = findViewById<EditText>(R.id.edtName)
        val edtEmail = findViewById<EditText>(R.id.edtEmail)
        val edtPassword = findViewById<EditText>(R.id.edtPassword)
        val edtConfirmPassword = findViewById<EditText>(R.id.edtConfirmPassword)

        val btnUser = findViewById<Button>(R.id.btnRegisterUser)
        val btnAdmin = findViewById<Button>(R.id.btnRegisterAdmin)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

        val txtBackToLogin = findViewById<TextView>(R.id.txtBackToLogin)
        val txtLogin = findViewById<TextView>(R.id.txtLogin)

        // Role selection: the selector drawable handles the colors
        fun selectRole(role: String) {
            selectedRole = role
            btnUser.isSelected = role == "User"
            btnAdmin.isSelected = role == "Admin"
        }

        fun goToLogin() {
            startActivity(Intent(this, Login::class.java))
            finish()
        }

        btnUser.setOnClickListener { selectRole("User") }
        btnAdmin.setOnClickListener { selectRole("Admin") }

        btnRegister.setOnClickListener {

            val name = edtName.text.toString().trim()
            val email = edtEmail.text.toString().trim()
            val password = edtPassword.text.toString()
            val confirmPassword = edtConfirmPassword.text.toString()

            if (selectedRole.isEmpty()) {
                Toast.makeText(
                    this,
                    "Please choose User or Admin",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (name.isEmpty()) {
                edtName.error = "Enter your full name"
                edtName.requestFocus()
                return@setOnClickListener
            }

            if (email.isEmpty()) {
                edtEmail.error = "Enter your email"
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

            Toast.makeText(
                this,
                "Account registered as $selectedRole",
                Toast.LENGTH_LONG
            ).show()

            goToLogin()
        }

        txtBackToLogin.setOnClickListener { goToLogin() }
        txtLogin.setOnClickListener { goToLogin() }
    }
}