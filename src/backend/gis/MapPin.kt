package backend.gis

data class MapPin(
    val incidentId: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val category: String = "",
    val severity: String = "",
    val status: String = ""
)