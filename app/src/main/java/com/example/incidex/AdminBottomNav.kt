package com.example.incidex

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

object AdminBottomNav {

    fun setup(activity: AppCompatActivity, nav: BottomNavigationView, selectedId: Int) {
        // Select first, then attach the listener, so it doesn't navigate by itself
        nav.selectedItemId = selectedId

        nav.setOnItemSelectedListener { item ->
            if (item.itemId == selectedId) return@setOnItemSelectedListener true

            val target: Class<*>? = when (item.itemId) {
                R.id.admin_nav_dashboard -> AdminHome::class.java
                R.id.admin_nav_incidents -> AdminIncidents::class.java
                R.id.admin_nav_map -> AdminMap::class.java
                R.id.admin_nav_profile -> AdminProfile::class.java
                else -> null
            }

            if (target == null) {
                false
            } else {
                activity.startActivity(
                    Intent(activity, target).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                )
                activity.finish()
                true
            }
        }
    }
}