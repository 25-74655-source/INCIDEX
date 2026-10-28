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
import java.io.File

class UserEditProfileActivity : AppCompatActivity() {

    companion object {
        private const val STATE_PHOTO = "pending_photo"
        private const val STATE_REMOVE = "remove_photo"
    }

    private lateinit var tilName: TextInputLayout
    private lateinit var tilEmail: TextInputLayout
    private lateinit var edtName: TextInputEditText
    private lateinit var edtEmail: TextInputEditText
    private lateinit var btnSave: Button
    private lateinit var txtAvatar: TextView
    private lateinit var picker: ProfilePhotoPicker

    // A photo picked on this screen but not saved yet
    private var pendingPhoto: String? = null
    private var removePhoto = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.user_edit_profile)

        pendingPhoto = savedInstanceState?.getString(STATE_PHOTO)
        removePhoto = savedInstanceState?.getBoolean(STATE_REMOVE) ?: false

        tilName = findViewById(R.id.tilName)
        tilEmail = findViewById(R.id.tilEmail)
        edtName = findViewById(R.id.edtName)
        edtEmail = findViewById(R.id.edtEmail)
        btnSave = findViewById(R.id.btnSave)
        txtAvatar = findViewById(R.id.txtAvatar)

        // Phone number is no longer used: hide the field
        findViewById<View>(R.id.tilPhone).visibility = View.GONE

        // Photo picker (camera / gallery / remove)
        picker = ProfilePhotoPicker(
            this,
            "user",
            hasPhoto = { shownPhoto() != null },
            onPhoto = { file ->
                pendingPhoto?.let { File(it).delete() }   // replace an earlier unsaved pick
                pendingPhoto = file.absolutePath
                removePhoto = false
                updateAvatar()
                updateSaveState()
            },
            onRemove = {
                pendingPhoto?.let { File(it).delete() }
                pendingPhoto = null
                removePhoto = ProfilePhotos.userPath != null
                updateAvatar()
                updateSaveState()
            }
        )

        // Current values
        edtName.setText(UserSession.name)
        edtEmail.setText(UserSession.email)
        updateAvatar()
        updateSaveState()

        // Live updates
        edtName.doAfterTextChanged {
            tilName.error = null
            if (shownPhoto() == null) txtAvatar.text = initialsOf(currentName())
            updateSaveState()
        }
        edtEmail.doAfterTextChanged {
            tilEmail.error = null
            updateSaveState()
        }

        // Photo: avatar, camera badge and "Change photo" all open the picker
        val photoClick = View.OnClickListener { picker.show() }
        findViewById<View>(R.id.btnEditPhoto).setOnClickListener(photoClick)
        findViewById<View>(R.id.btnChangePhoto).setOnClickListener(photoClick)
        txtAvatar.setOnClickListener(photoClick)

        // Cancel / Back
        findViewById<View>(R.id.btnCancel).setOnClickListener { confirmExit() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = confirmExit()
        })

        // Save
        btnSave.setOnClickListener { save() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PHOTO, pendingPhoto)
        outState.putBoolean(STATE_REMOVE, removePhoto)
    }

    override fun onDestroy() {
        // Leaving without saving: delete the unsaved photo file
        if (isFinishing) pendingPhoto?.let { File(it).delete() }
        super.onDestroy()
    }

    private fun currentName() = edtName.text.toString().trim()
    private fun currentEmail() = edtEmail.text.toString().trim()

    /** The photo that should be showing right now, or null for initials. */
    private fun shownPhoto(): String? =
        if (removePhoto) null else pendingPhoto ?: ProfilePhotos.userPath

    private fun photoChanged() = pendingPhoto != null || removePhoto

    private fun hasChanges() =
        currentName() != UserSession.name ||
                currentEmail() != UserSession.email ||
                photoChanged()

    private fun updateSaveState() {
        val enabled = hasChanges()
        btnSave.isEnabled = enabled
        btnSave.alpha = if (enabled) 1f else 0.5f
    }

    private fun updateAvatar() {
        txtAvatar.bindAvatar(currentName(), shownPhoto(), R.drawable.user_bg_avatar)
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

        // Photo
        val newPhoto = pendingPhoto
        if (newPhoto != null) {
            ProfilePhotos.userPath?.let { File(it).delete() }
            ProfilePhotos.userPath = newPhoto
            pendingPhoto = null            // saved, so don't delete it in onDestroy
        } else if (removePhoto) {
            ProfilePhotos.userPath?.let { File(it).delete() }
            ProfilePhotos.userPath = null
        }
        removePhoto = false

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