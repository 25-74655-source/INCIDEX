package com.example.incidex

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class UserEditProfileActivity : AppCompatActivity() {

    private lateinit var tilName: TextInputLayout
    private lateinit var tilEmail: TextInputLayout
    private lateinit var edtName: TextInputEditText
    private lateinit var edtEmail: TextInputEditText
    private lateinit var btnSave: Button
    private lateinit var txtAvatar: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.user_edit_profile)

        tilName = findViewById(R.id.tilName)
        tilEmail = findViewById(R.id.tilEmail)
        edtName = findViewById(R.id.edtName)
        edtEmail = findViewById(R.id.edtEmail)
        btnSave = findViewById(R.id.btnSave)
        txtAvatar = findViewById(R.id.txtAvatar)

        // Phone number is no longer used: hide the field
        findViewById<View>(R.id.tilPhone).visibility = View.GONE

        // Current values
        edtName.setText(UserSession.name)
        edtEmail.setText(UserSession.email)
        updateAvatar()
        updateSaveState()

        // Live updates
        edtName.doAfterTextChanged {
            tilName.error = null
            updateAvatar()
            updateSaveState()
        }
        edtEmail.doAfterTextChanged {
            tilEmail.error = null
            updateSaveState()
        }

        // Photo (needs storage, kaya coming soon muna)
        val photoClick = View.OnClickListener {
            Toast.makeText(this, "Change photo coming soon", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.btnEditPhoto).setOnClickListener(photoClick)
        findViewById<View>(R.id.btnChangePhoto).setOnClickListener(photoClick)

        // Cancel / Back
        findViewById<View>(R.id.btnCancel).setOnClickListener { confirmExit() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = confirmExit()
        })

        // Save
        btnSave.setOnClickListener { save() }
    }

    private fun currentName() = edtName.text.toString().trim()
    private fun currentEmail() = edtEmail.text.toString().trim()

    private fun hasChanges() =
        currentName() != UserSession.name ||
                currentEmail() != UserSession.email

    private fun updateSaveState() {
        val enabled = hasChanges()
        btnSave.isEnabled = enabled
        btnSave.alpha = if (enabled) 1f else 0.5f
    }

    private fun updateAvatar() {
        val initials = currentName()
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
        txtAvatar.text = initials.ifEmpty { "?" }
    }

    private fun validate(): Boolean {
        var ok = true

        if (currentName().length < 2) {
            tilName.error = "Enter your full name"
            ok = false
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(currentEmail()).matches()) {
            tilEmail.error = "Enter a valid email address"
            ok = false
        }
        return ok
    }

    private fun save() {
        if (!validate()) return

        // Temporary: sa memory lang. Papalitan ng Firebase update later.
        UserSession.name = currentName()
        UserSession.email = currentEmail()

        Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun confirmExit() {
        if (!hasChanges()) {
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Discard changes?")
            .setMessage("Your changes won't be saved.")
            .setPositiveButton("Discard") { _, _ -> finish() }
            .setNegativeButton("Keep editing", null)
            .show()
    }
}