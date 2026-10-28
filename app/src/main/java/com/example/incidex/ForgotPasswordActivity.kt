package com.example.incidex

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ForgotPasswordActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EMAIL = "email"   // optional: prefill the field
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.forgot_password)

        val edtEmail = findViewById<EditText>(R.id.edtFpEmail)
        val btnSend = findViewById<Button>(R.id.btnSendReset)
        val form = findViewById<View>(R.id.layoutFpForm)
        val success = findViewById<View>(R.id.layoutFpSuccess)
        val txtSuccessSub = findViewById<TextView>(R.id.txtFpSuccessSub)

        // Prefill (from Login's email box or the logged-in user's email)
        intent.getStringExtra(EXTRA_EMAIL)?.takeIf { it.isNotBlank() }?.let {
            edtEmail.setText(it)
            edtEmail.setSelection(it.length)
        }

        findViewById<View>(R.id.txtFpBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnFpDone).setOnClickListener { finish() }

        btnSend.setOnClickListener {
            val email = edtEmail.text.toString().trim()

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

            btnSend.isEnabled = false
            AuthRepository.sendReset(email) { error ->
                btnSend.isEnabled = true

                if (error != null) {
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                    return@sendReset
                }

                // Same message whether or not the email has an account (nothing is revealed)
                txtSuccessSub.text =
                    "If an account exists for $email, we've sent a link to reset your password. " +
                            "Check your spam folder too."
                form.visibility = View.GONE
                success.visibility = View.VISIBLE
            }
        }
    }
}