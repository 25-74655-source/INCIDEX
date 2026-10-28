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

        // Load the Nasugbu municipal boundary (app/src/main/assets/nasugbu_boundary.geojson)
        NasugbuBoundary.init(this)

        // Someone is still signed in from an earlier launch: reload their name and their reports.
        // (Also covers the app being restarted by Android in the middle of a screen.)
        AuthRepository.restoreSession { profile ->
            if (profile == null) return@restoreSession

            val isAdmin = profile.role == AuthRepository.ROLE_ADMIN
            if (isAdmin) {
                AdminSession.name = profile.name
                AdminSession.email = profile.email
            } else {
                UserSession.name = profile.name
                UserSession.email = profile.email
            }
            ReportStore.start(isAdmin)
        }
    }
}