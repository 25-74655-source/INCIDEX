package com.incidex.backend.models

import com.google.firebase.Timestamp

data class IncidentReport(
    val reportId: String = "",
    val category: String = "",
    val severity: String = "Medium",
    val description: String = "",
    val photoUrls: List<String> = emptyList(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String = "",
    val reporterId: String = "",
    val reporterEmail: String = "",
    val status: String = "Submitted",
    val adminNote: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)
