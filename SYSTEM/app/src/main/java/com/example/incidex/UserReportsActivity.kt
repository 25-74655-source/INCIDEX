package com.example.incidex

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView

class UserReportsActivity : AppCompatActivity() {

    private var allReports: List<UserReport> = emptyList()
    private var currentStatus: Status? = null

    private lateinit var adapter: UserReportAdapter
    private lateinit var txtEmpty: TextView
    private lateinit var chips: Map<TextView, Status?>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.user_reports)

        // LIST
        adapter = UserReportAdapter(emptyList()) { report ->
            startActivity(
                Intent(this, UserReportDetailActivity::class.java).putExtra("report", report)
            )
        }
        val rv = findViewById<RecyclerView>(R.id.rvReports)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        txtEmpty = findViewById(R.id.txtEmpty)

        // FILTER CHIPS
        chips = mapOf(
            findViewById<TextView>(R.id.chipAll) to null,
            findViewById<TextView>(R.id.chipSubmitted) to Status.SUBMITTED,
            findViewById<TextView>(R.id.chipVerified) to Status.VERIFIED,
            findViewById<TextView>(R.id.chipResolved) to Status.RESOLVED
        )
        chips.forEach { (chip, status) ->
            chip.setOnClickListener {
                currentStatus = status
                applyFilter()
            }
        }

        // BOTTOM NAV
        BottomNavHelper.setup(this, findViewById(R.id.bottomNav), R.id.nav_reports)
    }

    private fun refresh() {
        allReports = ReportStore.all()

        findViewById<TextView>(R.id.txtSubmittedCount).text = allReports.size.toString()
        findViewById<TextView>(R.id.txtVerifiedCount).text =
            allReports.count { it.status == Status.VERIFIED }.toString()
        findViewById<TextView>(R.id.txtResolvedCount).text =
            allReports.count { it.status == Status.RESOLVED }.toString()

        applyFilter()
    }

    private fun applyFilter() {
        chips.keys.forEach { it.isSelected = chips[it] == currentStatus }
        val filtered =
            if (currentStatus == null) allReports
            else allReports.filter { it.status == currentStatus }
        adapter.submit(filtered)
        txtEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        refresh() // may bagong report ba galing sa wizard?
        BottomNavHelper.highlight(findViewById<BottomNavigationView>(R.id.bottomNav), R.id.nav_reports)
    }
}