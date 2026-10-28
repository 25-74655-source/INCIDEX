package com.example.incidex

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class AdminProfile : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.admin_profile)

        bindAdmin()

        findViewById<View>(R.id.rowAdminEditProfile).setOnClickListener {
            startActivity(Intent(this, AdminEditProfile::class.java))
        }

        findViewById<View>(R.id.rowAdminChangePassword).setOnClickListener {
            startActivity(Intent(this, AdminChangePassword::class.java))
        }

        // LOG OUT -> MainActivity, and clear every screen before it
        findViewById<View>(R.id.btnAdminLogout).setOnClickListener {
            AuthRepository.signOut()   // without this, reopening the app logs you straight back in
            ReportStore.stop()
            ProfilePhotos.clearAdmin()
            AdminSession.clear()
            startActivity(
                Intent(this, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                )
            )
            finish()
        }

        // BOTTOM NAVIGATION
        AdminBottomNav.setup(
            this,
            findViewById<BottomNavigationView>(R.id.adminBottomNav),
            R.id.admin_nav_profile
        )
    }

    private fun bindAdmin() {
        val name = AdminSession.name.ifBlank { "Admin Account" }

        findViewById<TextView>(R.id.txtAdminProfileName).text = name
        findViewById<TextView>(R.id.txtAdminProfileEmail).text =
            AdminSession.email.ifBlank { "No email" }
        findViewById<TextView>(R.id.txtAdminProfileAvatar)
            .bindAvatar(name, ProfilePhotos.adminPath, R.drawable.admin_bg_avatar)
    }

    override fun onResume() {
        super.onResume()
        bindAdmin() // refresh after coming back from Edit Profile
    }
}