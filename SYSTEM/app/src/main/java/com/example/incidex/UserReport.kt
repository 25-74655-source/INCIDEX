package com.example.incidex

import java.io.Serializable

enum class Severity(val label: String, val text: String, val bg: String) {
    CRITICAL("CRITICAL", "#DC2626", "#FEE2E2"),
    MODERATE("MODERATE", "#F59E0B", "#FEF3C7"),
    LOW("LOW", "#16A34A", "#DCFCE7")
}

enum class Status(val label: String, val text: String, val bg: String) {
    SUBMITTED("SUBMITTED", "#F59E0B", "#FEF3C7"),
    VERIFIED("VERIFIED", "#2563EB", "#E8EEFF"),
    RESOLVED("RESOLVED", "#16A34A", "#DCFCE7")
}

data class UserReport(
    val id: String,
    val type: String,
    val icon: String = "",     // unused now; icon is looked up from `type`
    val severity: Severity,
    val status: Status,
    val location: String,
    val dateTime: String,
    val description: String,
    val lat: Double,
    val lng: Double,
    val submittedTime: String,
    val verifiedTime: String? = null,
    val resolvedTime: String? = null,
    val photoRes: Int = 0,         // 0 = walang drawable photo
    val photoPath: String? = null  // photo galing sa user (file path)
) : Serializable