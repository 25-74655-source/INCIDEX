package backend.locations

import kotlin.math.*

class LocationCalculator {

    fun calculateDistance(
        location1: Location,
        location2: Location
    ): Double {

        val earthRadius = 6371.0

        val lat1 = Math.toRadians(location1.latitude)
        val lat2 = Math.toRadians(location2.latitude)

        val deltaLat = Math.toRadians(
            location2.latitude - location1.latitude
        )

        val deltaLon = Math.toRadians(
            location2.longitude - location1.longitude
        )

        val a = sin(deltaLat / 2).pow(2) +
                cos(lat1) * cos(lat2) *
                sin(deltaLon / 2).pow(2)

        val c = 2 * atan2(
            sqrt(a),
            sqrt(1 - a)
        )

        return earthRadius * c
    }
}