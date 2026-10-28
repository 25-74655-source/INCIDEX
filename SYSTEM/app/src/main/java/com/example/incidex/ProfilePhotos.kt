package com.example.incidex

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import java.io.File
import java.io.FileOutputStream

object ProfilePhotos {

    // Current profile photos (in memory only, like UserSession / AdminSession)
    var userPath: String? = null
    var adminPath: String? = null

    fun dir(context: Context): File =
        File(context.filesDir, "profile_photos").apply { mkdirs() }

    fun clearUser() {
        userPath?.let { File(it).delete() }
        userPath = null
    }

    fun clearAdmin() {
        adminPath?.let { File(it).delete() }
        adminPath = null
    }

    /**
     * Reads the picture at [source], fixes its rotation, crops it to a centered square
     * and saves it as a small JPEG in [dest]. Call this off the main thread.
     */
    fun saveSquare(context: Context, source: Uri, dest: File, size: Int = 512): Boolean {
        try {
            val resolver = context.contentResolver

            // 1. find the picture's size so it can be loaded smaller
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

            var sample = 1
            while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= size) sample *= 2

            // 2. load it
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = resolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return false

            // 3. fix rotation (camera photos are often stored sideways)
            val orientation = resolver.openInputStream(source)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL

            val rotation = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (rotation != 0f) {
                val matrix = Matrix().apply { postRotate(rotation) }
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
            }

            // 4. crop to a centered square and shrink
            val side = minOf(bmp.width, bmp.height)
            val x = (bmp.width - side) / 2
            val y = (bmp.height - side) / 2
            val square = Bitmap.createBitmap(bmp, x, y, side, side)
            val finalBmp =
                if (side > size) Bitmap.createScaledBitmap(square, size, size, true) else square

            // 5. save
            dest.parentFile?.mkdirs()
            FileOutputStream(dest).use { finalBmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            return true
        } catch (e: Throwable) {
            return false
        }
    }
}

fun initialsOf(name: String, fallback: String = "?"): String =
    name.split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { fallback }

fun TextView.bindAvatar(
    name: String,
    photoPath: String?,
    @DrawableRes fallbackBg: Int
) {
    val bmp = photoPath?.takeIf { File(it).exists() }?.let { BitmapFactory.decodeFile(it) }
    if (bmp != null) {
        background = RoundedBitmapDrawableFactory.create(resources, bmp).apply {
            isCircular = true
        }
        text = ""
    } else {
        setBackgroundResource(fallbackBg)
        text = initialsOf(name)
    }
}