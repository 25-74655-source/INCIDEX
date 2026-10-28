package com.example.incidex

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import com.google.android.material.bottomnavigation.BottomNavigationView

object BottomNavHelper {

    fun setup(activity: Activity, nav: BottomNavigationView, current: Int) {
        nav.menu.findItem(current).isChecked = true

        nav.setOnItemSelectedListener { item ->
            val id = item.itemId
            if (id == current) return@setOnItemSelectedListener false

            when (id) {
                R.id.nav_home -> {
                    activity.startActivity(
                        Intent(activity, UserHome::class.java).addFlags(
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        )
                    )
                    activity.finish()
                }
                R.id.nav_map -> go(activity, UserMapActivity::class.java, current)
                R.id.nav_reports -> go(activity, UserReportsActivity::class.java, current)
                R.id.nav_profile -> go(activity, UserProfileActivity::class.java, current)
                else -> Toast.makeText(activity, "Coming soon", Toast.LENGTH_SHORT).show()
            }
            activity.overridePendingTransition(0, 0)
            false // current tab stays highlighted
        }
    }

    private fun go(activity: Activity, target: Class<*>, current: Int) {
        activity.startActivity(Intent(activity, target))
        // Galing sa Map/Reports/Profile: palitan lang. Galing sa Home: iwan ang Home sa likod.
        if (current != R.id.nav_home) activity.finish()
    }

    fun highlight(nav: BottomNavigationView, current: Int) {
        nav.menu.findItem(current).isChecked = true
    }
}