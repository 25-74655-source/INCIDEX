package com.example.incidex

import android.content.Intent
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
                val intent = Intent(this, UserHome::class.java)
                intent.putExtra("name", "Juan dela Cruz") // temporary until we have real accounts
                startActivity(intent)
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
            Toast.makeText(
                this,
                "Forgot Password selected",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Register
        txtRegister.setOnClickListener {
            startActivity(Intent(this, Register::class.java))
        }
    }
}