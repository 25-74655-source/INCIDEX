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

class AdminEditProfile : AppCompatActivity() {

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

    private var pendingPhoto: String? = null
    private var removePhoto = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.admin_edit_profile)

        pendingPhoto = savedInstanceState?.getString(STATE_PHOTO)
        removePhoto = savedInstanceState?.getBoolean(STATE_REMOVE) ?: false

        tilName = findViewById(R.id.tilAdminName)
        tilEmail = findViewById(R.id.tilAdminEmail)
        edtName = findViewById(R.id.edtAdminName)
        edtEmail = findViewById(R.id.edtAdminEmail)
        btnSave = findViewById(R.id.btnAdminSave)
        txtAvatar = findViewById(R.id.txtAdminEditAvatar)

        // Photo picker (camera / gallery / remove)
        picker = ProfilePhotoPicker(
            this,
            "admin",
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
                removePhoto = ProfilePhotos.adminPath != null
                updateAvatar()
                updateSaveState()
            }
        )

        // Current values
        edtName.setText(AdminSession.name)
        edtEmail.setText(AdminSession.email)
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

        // Photo: avatar, camera badge and "Change Photo" all open the picker
        val photoClick = View.OnClickListener { picker.show() }
        txtAvatar.setOnClickListener(photoClick)
        findViewById<View>(R.id.btnAdminEditPhoto).setOnClickListener(photoClick)
        findViewById<View>(R.id.btnAdminChangePhoto).setOnClickListener(photoClick)

        // Back / Cancel
        findViewById<View>(R.id.btnAdminEditBack).setOnClickListener { confirmExit() }
        findViewById<View>(R.id.btnAdminCancel).setOnClickListener { confirmExit() }
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
    private fun shownPhoto(): String? =
        if (removePhoto) null else pendingPhoto ?: ProfilePhotos.adminPath

    private fun photoChanged() = pendingPhoto != null || removePhoto

    private fun hasChanges() =
        currentName() != AdminSession.name ||
                currentEmail() != AdminSession.email ||
                photoChanged()

    private fun updateSaveState() {
        val enabled = hasChanges()
        btnSave.isEnabled = enabled
        btnSave.alpha = if (enabled) 1f else 0.5f
    }

    private fun updateAvatar() {
        txtAvatar.bindAvatar(currentName(), shownPhoto(), R.drawable.admin_bg_avatar)
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

        // Temporary: in memory only. Replace with a Firebase update later.
        AdminSession.name = currentName()
        AdminSession.email = currentEmail()

        // Photo
        val newPhoto = pendingPhoto
        if (newPhoto != null) {
            ProfilePhotos.adminPath?.let { File(it).delete() }
            ProfilePhotos.adminPath = newPhoto
            pendingPhoto = null            // saved, so don't delete it in onDestroy
        } else if (removePhoto) {
            ProfilePhotos.adminPath?.let { File(it).delete() }
            ProfilePhotos.adminPath = null
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