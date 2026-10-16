package com.example.incidex

// Temporary in-memory session. Papalitan ng Firebase Auth wag mo kalimutan bading.
object UserSession {
    var name: String = "Juan dela Cruz"
    var email: String = ""
    var phone: String = ""

    fun clear() {
        name = "Juan dela Cruz"
        email = ""
        phone = ""
    }
}