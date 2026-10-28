package com.incidex.backend.models

import com.google.firebase.Timestamp

data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "user", // Public registration must never allow choosing "admin".
    val createdAt: Timestamp? = null
)
