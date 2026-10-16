package com.example.incidex

import android.app.Application
import android.content.Context
import org.osmdroid.config.Configuration
import java.io.File

class IncidexApp : Application() {
    override fun onCreate() {
        super.onCreate()

        Configuration.getInstance().apply {
            load(
                applicationContext,
                getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
            )
            userAgentValue = "Incidex/1.0 (${packageName})"
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
        }
    }
}