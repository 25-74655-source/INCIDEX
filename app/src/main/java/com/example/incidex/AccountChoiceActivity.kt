package com.example.incidex

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AccountChoiceActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REPORT_ID = "report_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.account_choice)

        intent.getStringExtra(EXTRA_REPORT_ID)?.let { id ->
            findViewById<TextView>(R.id.txtChoiceSub).text =
                "Your report $id was sent. Log in or create an account to track it and get updates."
        }

        findViewById<View>(R.id.btnChoiceLogin).setOnClickListener {
            startActivity(Intent(this, Login::class.java))
            finish()
        }

        findViewById<View>(R.id.btnChoiceRegister).setOnClickListener {
            startActivity(Intent(this, Register::class.java))
            finish()
        }

        findViewById<View>(R.id.btnChoiceLater).setOnClickListener {
            finish()
        }
    }
}