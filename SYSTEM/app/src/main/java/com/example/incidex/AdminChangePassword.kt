package com.example.incidex

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class AdminChangePassword : AppCompatActivity() {

    private lateinit var tilCurrent: TextInputLayout
    private lateinit var tilNew: TextInputLayout
    private lateinit var tilConfirm: TextInputLayout
    private lateinit var edtCurrent: TextInputEditText
    private lateinit var edtNew: TextInputEditText
    private lateinit var edtConfirm: TextInputEditText
    private lateinit var btnUpdate: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.admin_change_password)

        tilCurrent = findViewById(R.id.tilAdminCurrentPw)
        tilNew = findViewById(R.id.tilAdminNewPw)
        tilConfirm = findViewById(R.id.tilAdminConfirmPw)
        edtCurrent = findViewById(R.id.edtAdminCurrentPw)
        edtNew = findViewById(R.id.edtAdminNewPw)
        edtConfirm = findViewById(R.id.edtAdminConfirmPw)
        btnUpdate = findViewById(R.id.btnAdminUpdatePw)

        // Clear an error as soon as the user edits that field
        edtCurrent.doAfterTextChanged { tilCurrent.error = null }
        edtNew.doAfterTextChanged { tilNew.error = null }
        edtConfirm.doAfterTextChanged { tilConfirm.error = null }

        findViewById<View>(R.id.btnAdminPwBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnAdminPwCancel).setOnClickListener { finish() }
        btnUpdate.setOnClickListener { save() }
    }

    private fun validate(): Boolean {
        val current = edtCurrent.text.toString()
        val newPw = edtNew.text.toString()
        val confirm = edtConfirm.text.toString()
        var ok = true

        if (current.isEmpty()) {
            tilCurrent.error = "Enter your current password"
            ok = false
        }

        if (newPw.length < 6) {
            tilNew.error = "Password must be at least 6 characters"
            ok = false
        } else if (newPw == current) {
            tilNew.error = "New password must be different"
            ok = false
        }

        if (confirm.isEmpty()) {
            tilConfirm.error = "Confirm your new password"
            ok = false
        } else if (confirm != newPw) {
            tilConfirm.error = "Passwords do not match"
            ok = false
        }

        return ok
    }

    private fun save() {
        if (!validate()) return

        btnUpdate.isEnabled = false
        // Firebase checks the current password again, then sets the new one
        AuthRepository.changePassword(
            edtCurrent.text.toString(),
            edtNew.text.toString()
        ) { error, currentWasWrong ->
            btnUpdate.isEnabled = true

            when {
                error == null -> {
                    Toast.makeText(this, "Password updated", Toast.LENGTH_SHORT).show()
                    finish()
                }
                currentWasWrong -> tilCurrent.error = error
                else -> Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            }
        }
    }
}