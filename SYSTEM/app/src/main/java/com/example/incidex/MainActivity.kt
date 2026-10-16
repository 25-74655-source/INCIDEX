package com.example.incidex

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // LOG IN (button at link)
        val openLogin = View.OnClickListener {
            startActivity(Intent(this, Login::class.java))
        }
        findViewById<View>(R.id.btnLogin).setOnClickListener(openLogin)
        findViewById<View>(R.id.txtMainLogin).setOnClickListener(openLogin)

        // QUICK REPORT (button at link): guest mode, hindi kailangang naka-login
        val openQuickReport = View.OnClickListener {
            startActivity(
                Intent(this, UserReportIncidentActivity::class.java)
                    .putExtra(UserReportIncidentActivity.EXTRA_GUEST, true)
            )
        }
        findViewById<View>(R.id.btnQuickReport).setOnClickListener(openQuickReport)
        findViewById<View>(R.id.txtQuickReport).setOnClickListener(openQuickReport)
    }
}