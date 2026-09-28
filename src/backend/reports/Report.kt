package backend.reports

data class Report(
    val reportId: String = "",
    val reporterId: String = "",

    val category: String = "",
    val severity: String = "",
    val description: String = "",

    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address: String = "",

    val photoUrl: String = "",

    val status: String = "Submitted",

    val createdAt: Long = System.currentTimeMillis()
)