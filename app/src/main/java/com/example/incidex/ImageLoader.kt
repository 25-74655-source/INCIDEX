package com.example.incidex

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Tiny image loader for report photos (no extra library needed). */
object ImageLoader {

    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    /** onDone(true) when the picture was shown, onDone(false) if it couldn't be loaded. */
    fun load(view: ImageView, url: String, maxSize: Int = 1024, onDone: ((Boolean) -> Unit)? = null) {
        val key = "$url@$maxSize"
        view.tag = key

        cache.get(key)?.let {
            view.setImageBitmap(it)
            onDone?.invoke(true)
            return
        }

        view.setImageDrawable(null)
        pool.execute {
            val bmp = try { download(url, maxSize) } catch (e: Exception) { null }
            if (bmp != null) cache.put(key, bmp)
            main.post {
                // The row may have been reused for another report while we were downloading
                if (view.tag == key) {
                    if (bmp != null) view.setImageBitmap(bmp)
                    onDone?.invoke(bmp != null)
                }
            }
        }
    }

    private fun download(url: String, maxSize: Int): Bitmap? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        val bytes = try {
            conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxSize || bounds.outHeight / sample > maxSize) sample *= 2
        return BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    }
}