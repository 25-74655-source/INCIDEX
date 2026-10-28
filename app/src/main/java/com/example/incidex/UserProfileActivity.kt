package com.example.incidex

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class UserProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.user_profile)

        bindUser()

        findViewById<View>(R.id.rowEditProfile).setOnClickListener {
            startActivity(Intent(this, UserEditProfileActivity::class.java))
        }

        // Forgot Password (the row id is still rowChangePassword in the layout)
        findViewById<View>(R.id.rowChangePassword).setOnClickListener {
            startActivity(
                Intent(this, ForgotPasswordActivity::class.java)
                    .putExtra(ForgotPasswordActivity.EXTRA_EMAIL, UserSession.email)
            )
        }

        // LOG OUT -> MainActivity, and clear every screen before it
        findViewById<View>(R.id.btnLogout).setOnClickListener {
            AuthRepository.signOut()   // without this, reopening the app logs you straight back in
            ReportStore.stop()
            ProfilePhotos.clearUser()
            UserSession.clear()
            startActivity(
                Intent(this, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                )
            )
            finish()
        }

        // BOTTOM NAV
        BottomNavHelper.setup(this, findViewById(R.id.bottomNav), R.id.nav_profile)
    }

    private fun bindUser() {
        val name = UserSession.name
        findViewById<TextView>(R.id.txtName).text = name
        findViewById<TextView>(R.id.txtEmail).text = UserSession.email.ifBlank { "No email" }
        findViewById<TextView>(R.id.txtAvatar)
            .bindAvatar(name, ProfilePhotos.userPath, R.drawable.user_bg_avatar)
    }

    override fun onResume() {
        super.onResume()
        bindUser() // refresh after coming back from Edit Profile
        BottomNavHelper.highlight(findViewById<BottomNavigationView>(R.id.bottomNav), R.id.nav_profile)
    }
}