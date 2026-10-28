package com.example.incidex

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object CloudinaryUploader {

    const val CLOUD_NAME = "tmbpahmg"
    const val UPLOAD_PRESET = "incidex_reports"

    private val main = Handler(Looper.getMainLooper())

    fun upload(path: String, onResult: (String?, String?) -> Unit) {
        if (CLOUD_NAME.startsWith("YOUR_") || UPLOAD_PRESET.startsWith("YOUR_")) {
            onResult(null, "Photo upload is not set up yet. Fill in CloudinaryUploader.kt.")
            return
        }

        Thread {
            var url: String? = null
            var error: String? = null
            try {
                val bmp = loadBitmapScaled(path, 1280)
                    ?: throw IllegalStateException("Couldn't read the photo")
                val jpeg = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
                    .toByteArray()

                val boundary = "----incidex" + UUID.randomUUID().toString().replace("-", "")
                val conn = URL("https://api.cloudinary.com/v1_1/$CLOUD_NAME/image/upload")
                    .openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

                conn.outputStream.use { out ->
                    fun write(s: String) = out.write(s.toByteArray(Charsets.UTF_8))
                    write("--$boundary\r\n")
                    write("Content-Disposition: form-data; name=\"upload_preset\"\r\n\r\n")
                    write("$UPLOAD_PRESET\r\n")
                    write("--$boundary\r\n")
                    write("Content-Disposition: form-data; name=\"file\"; filename=\"report.jpg\"\r\n")
                    write("Content-Type: image/jpeg\r\n\r\n")
                    out.write(jpeg)
                    write("\r\n--$boundary--\r\n")
                }

                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    url = JSONObject(body).getString("secure_url")
                } else {
                    val body = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    error = runCatching { JSONObject(body).getJSONObject("error").getString("message") }
                        .getOrNull() ?: "Photo upload failed (${conn.responseCode})."
                }
                conn.disconnect()
            } catch (e: Exception) {
                error = "Photo upload failed. Check your internet connection."
            }
            main.post { onResult(url, if (url == null) error ?: "Photo upload failed." else null) }
        }.start()
    }
}