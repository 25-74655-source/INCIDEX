package com.example.incidex

import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

/**
 * "Take a photo / Choose from gallery / Remove photo" for a profile picture.
 * Create it inside onCreate() (before the activity starts), then call show().
 */
class ProfilePhotoPicker(
    private val activity: AppCompatActivity,
    private val prefix: String,                 // "user" or "admin" (used in file names)
    private val hasPhoto: () -> Boolean,        // is a photo showing right now?
    private val onPhoto: (File) -> Unit,        // a new square photo is ready
    private val onRemove: () -> Unit            // the user chose "Remove photo"
) {

    // Same file every time, so it can be found again even if the screen was recreated
    private fun cameraTarget() = File(ProfilePhotos.dir(activity), "camera_$prefix.jpg")

    private val takePicture =
        activity.registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val file = cameraTarget()
            if (success && file.length() > 0) {
                process(Uri.fromFile(file), deleteSource = file)
            } else {
                file.delete()
            }
        }

    private val pickImage =
        activity.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) process(uri, deleteSource = null)
        }

    fun show() {
        val options = mutableListOf("Take a photo", "Choose from gallery")
        if (hasPhoto()) options.add("Remove photo")

        AlertDialog.Builder(activity)
            .setTitle("Profile photo")
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Take a photo" -> launchCamera()
                    "Choose from gallery" -> pickImage.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                    else -> onRemove()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun launchCamera() {
        val file = cameraTarget()
        file.delete()
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        try {
            takePicture.launch(uri)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, "No camera app found on this device", Toast.LENGTH_SHORT).show()
        }
    }
    private fun process(source: Uri, deleteSource: File?) {
        Thread {
            val dest = File(ProfilePhotos.dir(activity), "${prefix}_${System.currentTimeMillis()}.jpg")
            val ok = ProfilePhotos.saveSquare(activity, source, dest)
            deleteSource?.delete()

            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) {
                    dest.delete()
                } else if (ok) {
                    onPhoto(dest)
                } else {
                    dest.delete()
                    Toast.makeText(activity, "Couldn't load that photo", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}