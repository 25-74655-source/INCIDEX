package com.example.incidex

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView

class AdminIncidents : AppCompatActivity() {

    private var allReports: List<UserReport> = emptyList()
    private var statusFilter: Status? = null   // null = All
    private var query = ""

    private lateinit var adapter: AdminIncidentAdapter
    private lateinit var chips: Map<TextView, Status?>
    private lateinit var txtEmpty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.admin_incidents)

        txtEmpty = findViewById(R.id.txtAdminEmpty)

        // LIST (tap opens the admin incident detail)
        adapter = AdminIncidentAdapter(emptyList()) { report ->
            startActivity(
                Intent(this, AdminIncidentDetailActivity::class.java)
                    .putExtra(AdminIncidentDetailActivity.EXTRA_ID, report.id)
            )
        }
        findViewById<RecyclerView>(R.id.rvAdminIncidents).apply {
            layoutManager = LinearLayoutManager(this@AdminIncidents)
            adapter = this@AdminIncidents.adapter
        }

        // SEARCH
        findViewById<EditText>(R.id.edtAdminSearch).doAfterTextChanged {
            query = it?.toString().orEmpty().trim().lowercase()
            applyFilters()
        }

        // FILTER CHIPS
        chips = mapOf<TextView, Status?>(
            findViewById<TextView>(R.id.adminChipAll) to null,
            findViewById<TextView>(R.id.adminChipSubmitted) to Status.SUBMITTED,
            findViewById<TextView>(R.id.adminChipVerified) to Status.VERIFIED,
            findViewById<TextView>(R.id.adminChipResolved) to Status.RESOLVED,
            findViewById<TextView>(R.id.adminChipRejected) to Status.REJECTED
        )
        chips.forEach { (chip, status) ->
            chip.setOnClickListener {
                statusFilter = status
                applyFilters()
            }
        }

        // Reload whenever reports change
        ReportStore.observe(this) {
            allReports = ReportStore.all().sortedByDescending { it.createdAt }
            applyFilters()
        }

        AdminBottomNav.setup(
            this,
            findViewById<BottomNavigationView>(R.id.adminBottomNav),
            R.id.admin_nav_incidents
        )
    }

    private fun applyFilters() {
        chips.forEach { (chip, status) -> chip.isSelected = status == statusFilter }

        val filtered = allReports.filter { r ->
            (statusFilter == null || r.status == statusFilter) &&
                    (query.isEmpty() ||
                            listOf(r.type, r.location, r.reporter, r.id)
                                .any { it.lowercase().contains(query) })
        }

        adapter.submit(filtered)
        txtEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }
}