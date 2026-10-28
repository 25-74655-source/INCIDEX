package com.example.incidex

// In-memory copy of the signed-in admin's profile. It is filled in after login (Login / IncidexApp).
object AdminSession {
    var name: String = ""
    var email: String = ""

    fun clear() {
        name = ""
        email = ""
    }
}